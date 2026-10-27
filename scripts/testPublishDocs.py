#!/usr/bin/env python3
"""Integration tests for the documentation publisher CLI."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
PUBLISHER = REPOSITORY_ROOT / ".buildscript" / "publish_docs.sh"


class PublishDocsTest(unittest.TestCase):
    def setUp(self):
        self.temporary_directory = tempfile.TemporaryDirectory(
            prefix="publish-docs-test-"
        )
        self.addCleanup(self.temporary_directory.cleanup)
        self.root = Path(self.temporary_directory.name)
        self.home = self.root / "home"
        self.home.mkdir()
        self.temporary_files = self.root / "tmp"
        self.temporary_files.mkdir()
        self.destination = self.root / "destination.git"
        self.site = self.root / "site"
        self.site.mkdir()

        self.git_environment = {
            "PATH": os.environ["PATH"],
            "HOME": str(self.home),
            "TMPDIR": str(self.temporary_files),
            "GIT_CONFIG_GLOBAL": "/dev/null",
            "GIT_CONFIG_NOSYSTEM": "1",
            "LC_ALL": "C",
        }
        self.git(
            "init",
            "--bare",
            "--initial-branch=gh-pages",
            str(self.destination),
        )

    def git(self, *arguments, cwd=REPOSITORY_ROOT):
        return subprocess.run(
            ["git", *arguments],
            cwd=cwd,
            env=self.git_environment,
            check=True,
            capture_output=True,
            text=True,
            timeout=30,
        )

    def run_publisher(self, site=None, destination=None, environment=None):
        return subprocess.run(
            [
                "bash",
                str(PUBLISHER),
                str(site if site is not None else self.site),
                str(destination if destination is not None else self.destination),
            ],
            cwd=REPOSITORY_ROOT,
            env=environment if environment is not None else self.git_environment,
            capture_output=True,
            text=True,
            timeout=30,
        )

    def remote_files(self):
        result = self.git(
            "--git-dir",
            str(self.destination),
            "ls-tree",
            "-r",
            "--name-only",
            "gh-pages",
        )
        return set(result.stdout.splitlines())

    def remote_file_contents(self, path):
        return self.git(
            "--git-dir",
            str(self.destination),
            "show",
            f"gh-pages:{path}",
        ).stdout

    def remote_commit_count(self):
        return int(
            self.git(
                "--git-dir",
                str(self.destination),
                "rev-list",
                "--count",
                "gh-pages",
            ).stdout
        )

    def write_site_file(self, relative_path, contents):
        path = self.site / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(contents, encoding="utf-8")

    def test_publisher_entrypoint_exists(self):
        self.assertTrue(
            PUBLISHER.is_file(),
            f"documentation publisher script is missing: {PUBLISHER}",
        )

    def test_first_publication_keeps_dotfiles_and_restored_v1_docs(self):
        self.write_site_file("index.html", "first publication\n")
        self.write_site_file(".nojekyll", "")
        self.write_site_file(".well-known/site-verification", "verified\n")
        self.write_site_file("1.x/index.html", "restored 1.x docs\n")
        self.write_site_file(".gitignore", "1.x/\n.well-known/\n")
        status_before = self.git("status", "--porcelain", "--untracked-files=all").stdout
        head_before = self.git("rev-parse", "HEAD").stdout
        refs_before = self.git("for-each-ref", "--format=%(refname)").stdout

        result = self.run_publisher()

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(
            self.remote_files(),
            {
                ".gitignore",
                ".nojekyll",
                ".well-known/site-verification",
                "1.x/index.html",
                "index.html",
            },
        )
        self.assertEqual(self.remote_file_contents("1.x/index.html"), "restored 1.x docs\n")
        self.assertEqual(self.remote_file_contents(".well-known/site-verification"), "verified\n")
        self.assertEqual(self.remote_commit_count(), 1)
        identity = self.git(
            "--git-dir",
            str(self.destination),
            "log",
            "-1",
            "--format=%an <%ae>",
            "gh-pages",
        ).stdout.strip()
        self.assertEqual(
            identity,
            "github-actions[bot] <41898282+github-actions[bot]@users.noreply.github.com>",
        )
        self.assertEqual(
            self.git("status", "--porcelain", "--untracked-files=all").stdout,
            status_before,
        )
        self.assertEqual(self.git("rev-parse", "HEAD").stdout, head_before)
        self.assertEqual(
            self.git("for-each-ref", "--format=%(refname)").stdout,
            refs_before,
        )

    def test_site_inside_checkout_does_not_publish_checkout_files(self):
        site_temporary_directory = tempfile.TemporaryDirectory(
            prefix=".publish-docs-site-",
            dir=REPOSITORY_ROOT,
        )
        self.addCleanup(site_temporary_directory.cleanup)
        site = Path(site_temporary_directory.name)
        (site / "index.html").write_text("site content\n", encoding="utf-8")
        status_before = self.git("status", "--porcelain", "--untracked-files=all").stdout

        result = self.run_publisher(site=site)

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.remote_files(), {"index.html"})
        self.assertEqual(self.remote_file_contents("index.html"), "site content\n")
        self.assertEqual(
            self.git("status", "--porcelain", "--untracked-files=all").stdout,
            status_before,
        )

    def test_republication_replaces_stale_files_and_keeps_one_commit(self):
        self.write_site_file("index.html", "old site\n")
        self.write_site_file("stale.html", "remove this\n")
        self.write_site_file("old-directory/stale.txt", "remove this too\n")
        first_result = self.run_publisher()
        self.assertEqual(first_result.returncode, 0, first_result.stdout + first_result.stderr)

        (self.site / "stale.html").unlink()
        (self.site / "old-directory" / "stale.txt").unlink()
        (self.site / "old-directory").rmdir()
        self.write_site_file("index.html", "replacement site\n")
        self.write_site_file("1.x/index.html", "restored docs\n")
        self.write_site_file(".nojekyll", "")
        second_result = self.run_publisher()

        self.assertEqual(second_result.returncode, 0, second_result.stdout + second_result.stderr)
        self.assertEqual(
            self.remote_files(),
            {".nojekyll", "1.x/index.html", "index.html"},
        )
        self.assertEqual(self.remote_file_contents("index.html"), "replacement site\n")
        self.assertEqual(self.remote_commit_count(), 1)

    def test_destination_configuration_is_preserved_unless_source_replaces_it(self):
        seed = self.root / "seed"
        self.git("init", "--initial-branch=gh-pages", str(seed))
        previous = {".nojekyll": "keep generated assets as-is\n", "CNAME": "docs.example.test\n"}
        for name, contents in previous.items():
            (seed / name).write_text(contents, encoding="utf-8")
        (seed / ".nojekyll").chmod(0o755)
        (seed / "stale.html").write_text("old page\n", encoding="utf-8")
        self.git("add", "--all", cwd=seed)
        self.git("-c", "user.name=Test", "-c", "user.email=test@example.test",
                 "commit", "-m", "Seed destination configuration", cwd=seed)
        seed_head = self.git("rev-parse", "HEAD", cwd=seed).stdout.strip()
        status_before = self.git("status", "--porcelain", "--untracked-files=all").stdout
        head_before = self.git("rev-parse", "HEAD").stdout
        refs_before = self.git("for-each-ref", "--format=%(refname) %(objectname)").stdout
        git_directory = Path(self.git("rev-parse", "--absolute-git-dir").stdout.strip())
        metadata_before = {name: (git_directory / name).read_bytes()
                           if (git_directory / name).exists() else None
                           for name in ("index", "FETCH_HEAD", "shallow")}
        self.write_site_file("index.html", "new page\n")
        self.write_site_file("_static/asset.css", "new asset\n")
        self.write_site_file("1.x/index.html", "restored docs\n")

        # Reset the destination between scenarios; publication itself is the public CLI.
        for replacement in (None, ".nojekyll", "CNAME"):
            with self.subTest(source_replacement=replacement):
                self.git("push", "--force", str(self.destination), f"{seed_head}:refs/heads/gh-pages", cwd=seed)
                expected = dict(previous)
                if replacement is not None:
                    expected[replacement] = f"replacement {replacement}\n"
                    self.write_site_file(replacement, expected[replacement])
                try:
                    result = self.run_publisher()
                    self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                    self.assertEqual(set(previous) | {"index.html", "_static/asset.css", "1.x/index.html"},
                                     self.remote_files())
                    for name, contents in expected.items():
                        self.assertEqual(contents, self.remote_file_contents(name))
                    self.assertEqual("new page\n", self.remote_file_contents("index.html"))
                    self.assertEqual("new asset\n", self.remote_file_contents("_static/asset.css"))
                    self.assertEqual("restored docs\n", self.remote_file_contents("1.x/index.html"))
                    self.assertEqual(1, self.remote_commit_count())
                    marker_mode = self.git("--git-dir", str(self.destination), "ls-tree", "gh-pages", ".nojekyll").stdout.split()[0]
                    self.assertEqual("100644" if replacement == ".nojekyll" else "100755", marker_mode)
                    self.assertEqual(status_before, self.git("status", "--porcelain", "--untracked-files=all").stdout)
                    self.assertEqual(head_before, self.git("rev-parse", "HEAD").stdout)
                    self.assertEqual(refs_before, self.git("for-each-ref", "--format=%(refname) %(objectname)").stdout)
                    for name, contents in metadata_before.items():
                        path = git_directory / name
                        self.assertEqual(contents, path.read_bytes() if path.exists() else None, name)
                    self.assertEqual([], list(self.temporary_files.iterdir()))
                finally:
                    if replacement is not None:
                        (self.site / replacement).unlink()

    def test_missing_site_fails_with_descriptive_error(self):
        missing_site = self.root / "missing-site"

        result = self.run_publisher(site=missing_site)

        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(
            result.stderr.strip(),
            f"publish_docs.sh: site directory does not exist: {missing_site}",
        )

    def test_empty_site_fails_with_descriptive_error(self):
        empty_site = self.root / "empty-site"
        empty_site.mkdir()

        result = self.run_publisher(site=empty_site)

        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(
            result.stderr.strip(),
            f"publish_docs.sh: site directory is empty: {empty_site}",
        )

    def test_invalid_destination_fails_and_preserves_checkout_worktree(self):
        self.write_site_file("index.html", "published site\n")
        invalid_destination = self.root / "not-a-git-repository.git"
        status_before = self.git("status", "--porcelain", "--untracked-files=all").stdout
        head_before = self.git("rev-parse", "HEAD").stdout

        result = self.run_publisher(destination=invalid_destination)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn(
            "publish_docs.sh: failed to publish documentation to branch gh-pages",
            result.stderr,
        )
        self.assertEqual(
            self.git("status", "--porcelain", "--untracked-files=all").stdout,
            status_before,
        )
        self.assertEqual(self.git("rev-parse", "HEAD").stdout, head_before)
        self.assertEqual(
            list(self.temporary_files.iterdir()),
            [],
            "publisher left a temporary directory behind after a failed push",
        )

    def test_checkout_scoped_git_config_is_available_during_push(self):
        self.write_site_file("index.html", "published site\n")
        hook_directory = self.root / "hooks"
        hook_directory.mkdir()
        auth_config = self.root / "checkout-auth.config"
        auth_config.write_text(
            "[core]\n"
            f"\thooksPath = {hook_directory}\n"
            "[http \"https://publish-docs-test.invalid/\"]\n"
            "\textraheader = X-Checkout-Config: local-test-value\n",
            encoding="utf-8",
        )
        global_config = self.root / "gitconfig"
        global_config.write_text("", encoding="utf-8")
        git_directory = self.git("rev-parse", "--absolute-git-dir").stdout.strip()
        self.git(
            "config",
            "--file",
            str(global_config),
            "--add",
            f"includeIf.gitdir:{git_directory}.path",
            str(auth_config),
        )
        hook_marker = self.root / "push-hook-ran"
        pre_push = hook_directory / "pre-push"
        pre_push.write_text(
            "#!/bin/sh\n"
            "header=$(git config --get-urlmatch http.extraheader "
            "https://publish-docs-test.invalid/repository.git) || exit 1\n"
            "if [ \"$header\" != \"X-Checkout-Config: local-test-value\" ]; then\n"
            "  echo 'repository-scoped Git config was not available to push' >&2\n"
            "  exit 1\n"
            "fi\n"
            f"printf 'ran\\n' > '{hook_marker}'\n",
            encoding="utf-8",
        )
        pre_push.chmod(0o755)
        environment = {
            **self.git_environment,
            "GIT_CONFIG_GLOBAL": str(global_config),
        }

        result = self.run_publisher(environment=environment)

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(hook_marker.read_text(encoding="utf-8"), "ran\n")
        self.assertEqual(self.remote_file_contents("index.html"), "published site\n")

    def test_temporary_files_are_removed_after_publication(self):
        self.write_site_file("index.html", "published site\n")

        result = self.run_publisher()

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(
            list(self.temporary_files.iterdir()),
            [],
            "publisher left a temporary directory behind",
        )


if __name__ == "__main__":
    unittest.main()
