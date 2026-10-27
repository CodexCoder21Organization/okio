#!/usr/bin/env python3
"""Exercise the runner CLI with an isolated executable SDK boundary.

This host cannot run Android. These small SDK programs model process lifecycle
and adb responses; the workflow still runs the real connectedCheck workload.
"""
import os
import pathlib
import signal
import subprocess
import sys
import tempfile
import time
import unittest


class AndroidTestRunnerTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = pathlib.Path(self.temporary.name)
        self.sdk = self.root / 'SDK with spaces'
        (self.sdk / 'emulator').mkdir(parents=True)
        (self.sdk / 'platform-tools').mkdir()
        self.runner = pathlib.Path(__file__).resolve().parents[1] / '.buildscript/run_android_tests.py'

    def sdk_programs(self, *, boot='1', emulator_exit=False, settings_fail=False):
        emulator = self.sdk / 'emulator/emulator'
        emulator.write_text('#!' + sys.executable + '\n' +
                            'import os, pathlib, sys, time\n' +
                            f'root = pathlib.Path({str(self.root)!r})\n' +
                            '(root / "emulator.pid").write_text(str(os.getpid()))\n' +
                            '(root / "emulator.args").write_text("\\n".join(sys.argv[1:]))\n' +
                            ('print("emulator failed", flush=True)\nsys.exit(31)\n' if emulator_exit else
                             'while True: time.sleep(1)\n'))
        emulator.chmod(0o755)
        adb = self.sdk / 'platform-tools/adb'
        adb.write_text('#!' + sys.executable + '\n' +
                       'import pathlib, sys\n' +
                       f'root = pathlib.Path({str(self.root)!r})\n' +
                       'with (root / "adb.calls").open("a") as out: out.write(" ".join(sys.argv[1:]) + "\\n")\n' +
                       f'print({boot!r}) if "getprop" in sys.argv else None\n' +
                       ('sys.exit(19) if "settings" in sys.argv else None\n' if settings_fail else ''))
        adb.chmod(0o755)

    def invoke(self, command=None, timeout='600'):
        if command is None:
            command = [sys.executable, '-c', f'from pathlib import Path; Path({str(self.root / "ran")!r}).touch()']
        return subprocess.run([sys.executable, str(self.runner), '--sdk-root', str(self.sdk),
                               '--boot-timeout', timeout, '--', *command],
                              text=True, capture_output=True, timeout=30)

    def assert_emulator_reaped(self):
        pid_file = self.root / 'emulator.pid'
        if pid_file.exists():
            with self.assertRaises(ProcessLookupError):
                os.kill(int(pid_file.read_text()), 0)

    def test_boot_runs_exact_command_then_reaps_emulator(self):
        self.sdk_programs()
        result = self.invoke([sys.executable, '-c',
                              f'import os,sys; from pathlib import Path; Path({str(self.root / "ran")!r}).write_text(os.environ["ANDROID_SERIAL"] + "\\n" + sys.argv[1])',
                              'argument with spaces; literal'])
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual('emulator-5554\nargument with spaces; literal', (self.root / 'ran').read_text())
        arguments = (self.root / 'emulator.args').read_text().splitlines()
        self.assertIn('test', arguments)
        self.assertIn('-no-snapshot', arguments)
        self.assertIn('swiftshader_indirect', arguments)
        self.assertEqual(3, (self.root / 'adb.calls').read_text().count('settings put global'))
        self.assert_emulator_reaped()

    def test_test_failure_is_returned_and_emulator_reaped(self):
        self.sdk_programs()
        result = self.invoke([sys.executable, '-c', 'raise SystemExit(23)'])
        self.assertEqual(23, result.returncode, result.stdout + result.stderr)
        self.assert_emulator_reaped()

    def test_emulator_exit_before_boot_fails_without_running_tests(self):
        self.sdk_programs(boot='0', emulator_exit=True)
        result = self.invoke()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Android emulator exited before boot completed (exit code 31).', result.stderr)
        self.assertFalse((self.root / 'ran').exists())
        self.assert_emulator_reaped()

    def test_emulator_exit_during_test_stops_test_process(self):
        self.sdk_programs()
        process = subprocess.Popen([sys.executable, str(self.runner), '--sdk-root', str(self.sdk), '--',
                                    sys.executable, '-c',
                                    f'import os,signal,time; from pathlib import Path; os.kill(int(Path({str(self.root / "emulator.pid")!r}).read_text()), signal.SIGTERM); time.sleep(600)'],
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
                                   start_new_session=True)
        try:
            try:
                stdout, stderr = process.communicate(timeout=15)
            except subprocess.TimeoutExpired:
                process.terminate()
                process.communicate(timeout=20)
                self.fail('Runner kept waiting for tests after its emulator exited')
            self.assertNotEqual(0, process.returncode)
            self.assertIn('Android emulator exited while tests were running (exit code -15).', stderr)
            self.assert_emulator_reaped()
        finally:
            if process.poll() is None:
                process.terminate()
            process.communicate(timeout=20)

    def test_settings_failure_fails_without_running_tests(self):
        self.sdk_programs(settings_fail=True)
        result = self.invoke()
        self.assertNotEqual(0, result.returncode)
        self.assertFalse((self.root / 'ran').exists())
        self.assert_emulator_reaped()

    def test_boot_deadline_fails_without_running_tests(self):
        self.sdk_programs(boot='0')
        # Deliberately exhaust the configurable budget; do not wait 600 seconds.
        result = self.invoke(timeout='0')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Android emulator did not complete boot within 0 seconds.', result.stderr)
        self.assertFalse((self.root / 'ran').exists())
        self.assert_emulator_reaped()

    def test_missing_sdk_has_descriptive_error(self):
        result = self.invoke()
        self.assertNotEqual(0, result.returncode)
        self.assertIn(f'Android SDK executable does not exist: {self.sdk / "emulator/emulator"}', result.stderr)
        self.assertFalse((self.root / 'ran').exists())

    def test_cancellation_reaps_emulator_and_test_process(self):
        self.sdk_programs()
        command_pid = self.root / 'test.pid'
        process = subprocess.Popen([sys.executable, str(self.runner), '--sdk-root', str(self.sdk), '--',
                                    sys.executable, '-c',
                                    f'import os,time; from pathlib import Path; Path({str(command_pid)!r}).write_text(str(os.getpid())); time.sleep(600)'],
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        try:
            deadline = time.monotonic() + 20
            while not command_pid.exists() and process.poll() is None and time.monotonic() < deadline:
                time.sleep(0.02)
            self.assertTrue(command_pid.exists(), 'Test command never started')
            process.send_signal(signal.SIGTERM)
            stdout, stderr = process.communicate(timeout=20)
            self.assertEqual(128 + signal.SIGTERM, process.returncode, stdout + stderr)
            with self.assertRaises(ProcessLookupError):
                os.kill(int(command_pid.read_text()), 0)
            self.assert_emulator_reaped()
        finally:
            if process.poll() is None:
                process.kill()
            process.communicate(timeout=20)


if __name__ == '__main__':
    unittest.main()
