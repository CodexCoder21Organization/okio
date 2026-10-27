#!/usr/bin/env python3
"""Guard workflow action references against the selected publisher policy."""
import pathlib
import re
import unittest


class WorkflowActionPolicyTest(unittest.TestCase):
    def test_workflows_use_allowed_publishers(self):
        # Mirrors the organization's GitHub-owned/verified-only action policy.
        # Gradle is the only verified third-party publisher these workflows use.
        rejected = []
        for workflow in sorted((pathlib.Path(__file__).resolve().parents[1] / '.github/workflows').glob('*')):
            if workflow.suffix not in ('.yml', '.yaml'):
                continue
            for action in re.findall(r'^\s*-?\s*uses:\s*([^\s#]+)', workflow.read_text(), re.MULTILINE):
                if not action.startswith(('actions/', 'gradle/actions/')):
                    rejected.append(f'{workflow.name}: {action}')
        self.assertEqual([], rejected, 'Workflow actions must be GitHub-owned or from the existing verified Gradle publisher')

    def test_existing_workloads_remain(self):
        workflow = (pathlib.Path(__file__).resolve().parents[1] / '.github/workflows/build.yml').read_text()
        self.assertEqual(['jvm', 'emulator', 'loom', 'all-platforms', 'publish', 'publish-website'],
                         re.findall(r'^  ([a-z-]+):$', workflow.split('jobs:', 1)[1], re.MULTILINE))
        for command in ('-Dtest.java.version=${{ matrix.java-version }} build --stacktrace',
                        ':okio-assetfilesystem:connectedCheck', '-DloomEnabled=true build',
                        './gradlew build', './gradlew build -Dkwasm=false',
                        './gradlew publish --stacktrace', '.buildscript/prepare_mkdocs.sh',
                        'mkdocs build', '.buildscript/restore_v1_docs.sh'):
            self.assertIn(command, workflow)
        self.assertEqual(2, workflow.count("if: github.repository == 'lysine-dev/okio' && github.ref == 'refs/heads/main'"))
        self.assertIn('os: [ macos-14, ubuntu-latest, windows-latest ]', workflow)
        self.assertIn('needs: [jvm, all-platforms, emulator]', workflow)


if __name__ == '__main__':
    unittest.main()
