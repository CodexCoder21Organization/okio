#!/usr/bin/env python3
"""Run the Android test command with an owned API 24 emulator process."""
import argparse
import os
from pathlib import Path
import signal
import subprocess
import sys
import time


def stop_process(process):
    if process is None:
        return
    # Each child owns a separate group, so cancellation also stops its children.
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        pass  # The owned process group has already exited.
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait(timeout=10)


def interrupted(signum, _frame):
    raise SystemExit(128 + signum)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk-root', required=True, type=Path)
    parser.add_argument('--boot-timeout', type=float, default=600,
                        help='Boot budget in seconds (default: 600, unchanged from the previous action)')
    parser.add_argument('command', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ['--'] else args.command
    if not command:
        parser.error('Provide the Android test command after --')
    if args.boot_timeout < 0:
        parser.error('--boot-timeout must be non-negative')
    emulator_path = args.sdk_root / 'emulator/emulator'
    adb_path = args.sdk_root / 'platform-tools/adb'
    for executable in (emulator_path, adb_path):
        if not executable.is_file():
            parser.error(f'Android SDK executable does not exist: {executable}')
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    emulator = None
    test_process = None
    try:
        emulator = subprocess.Popen(
            [str(emulator_path), '-port', '5554', '-avd', 'test', '-no-window',
             '-gpu', 'swiftshader_indirect', '-no-snapshot', '-noaudio', '-no-boot-anim'],
            start_new_session=True)
        deadline = time.monotonic() + args.boot_timeout
        while True:
            code = emulator.poll()
            if code is not None:
                raise RuntimeError(f'Android emulator exited before boot completed (exit code {code}).')
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise RuntimeError(f'Android emulator did not complete boot within {args.boot_timeout:g} seconds.')
            try:
                boot = subprocess.run([str(adb_path), '-s', 'emulator-5554', 'shell',
                                       'getprop', 'sys.boot_completed'],
                                      capture_output=True, text=True, timeout=min(10, remaining))
                if boot.returncode == 0 and boot.stdout.strip() == '1':
                    break
                if boot.returncode != 0:
                    print(boot.stderr, file=sys.stderr, end='')
            except subprocess.TimeoutExpired:
                print('adb boot-status read did not finish within its remaining budget.', file=sys.stderr)
            time.sleep(min(2, max(0, deadline - time.monotonic())))
        subprocess.run([str(adb_path), '-s', 'emulator-5554', 'shell', 'input', 'keyevent', '82'],
                       check=True, timeout=10)
        for setting in ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'):
            subprocess.run([str(adb_path), '-s', 'emulator-5554', 'shell', 'settings',
                            'put', 'global', setting, '0.0'], check=True, timeout=10)
        environment = dict(os.environ, ANDROID_SERIAL='emulator-5554', EMULATOR_PORT='5554')
        test_process = subprocess.Popen(command, env=environment, start_new_session=True)
        code = test_process.wait()
        return code if code >= 0 else 128 - code
    finally:
        stop_process(test_process)
        stop_process(emulator)


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (OSError, RuntimeError, subprocess.SubprocessError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
