#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  printf 'usage: %s <site-directory> <destination-repository-url>\n' "$0" >&2
  exit 2
fi

site_argument=$1
destination_url=$2

if [[ ! -d "$site_argument" ]]; then
  printf 'publish_docs.sh: site directory does not exist: %s\n' "$site_argument" >&2
  exit 1
fi

if [[ -z "$destination_url" ]]; then
  printf 'publish_docs.sh: destination repository URL must not be empty\n' >&2
  exit 2
fi

site_directory=$(cd -- "$site_argument" && pwd -P)
site_has_content=false
while IFS= read -r -d '' _; do
  site_has_content=true
done < <(find "$site_directory" -mindepth 1 ! -type d -print0 -quit)

if [[ "$site_has_content" != true ]]; then
  printf 'publish_docs.sh: site directory is empty: %s\n' "$site_directory" >&2
  exit 1
fi

script_directory=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
if ! repository_root=$(git -C "$script_directory/.." rev-parse --show-toplevel 2>/dev/null); then
  printf 'publish_docs.sh: script must run from a Git checkout\n' >&2
  exit 1
fi
git_directory=$(git -C "$repository_root" rev-parse --absolute-git-dir)

temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/publish-docs.XXXXXX")
cleanup() {
  rm -rf -- "$temporary_directory"
}
trap cleanup EXIT
index_file=$temporary_directory/index

site_git() {
  GIT_INDEX_FILE=$index_file \
    git -C "$site_directory" \
      --git-dir="$git_directory" \
      --work-tree="$site_directory" \
      "$@"
}

site_git read-tree --empty
site_git add --force --all -- .
# Read the destination without changing checkout refs, FETCH_HEAD or its index.
if previous_ref=$(git -C "$repository_root" ls-remote --exit-code -- "$destination_url" refs/heads/gh-pages); then
  previous_oid=${previous_ref%%$'\t'*}
  if ! git -C "$repository_root" fetch --no-write-fetch-head --no-tags --no-recurse-submodules \
      --refmap= -- "$destination_url" "$previous_oid"; then
    printf 'publish_docs.sh: failed to read documentation from branch gh-pages\n' >&2
    exit 1
  fi
  for file in .nojekyll CNAME; do
    if [[ ! -e "$site_directory/$file" && ! -L "$site_directory/$file" ]]; then
      previous_entry=$(site_git ls-tree "$previous_oid" -- "$file")
      if [[ -n "$previous_entry" ]]; then
        printf '%s\n' "$previous_entry" | site_git update-index --index-info
      fi
    fi
  done
else
  read_status=$?
  if [[ $read_status -ne 2 ]]; then
    printf 'publish_docs.sh: failed to publish documentation to branch gh-pages\n' >&2
    exit 1
  fi
  # Exit status 2 means the repository exists but has no gh-pages branch yet.
fi
tree=$(site_git write-tree)

bot_name='github-actions[bot]'
bot_email='41898282+github-actions[bot]@users.noreply.github.com'
commit_oid=$(
  GIT_AUTHOR_NAME=$bot_name \
    GIT_AUTHOR_EMAIL=$bot_email \
    GIT_COMMITTER_NAME=$bot_name \
    GIT_COMMITTER_EMAIL=$bot_email \
    git --git-dir="$git_directory" \
      -c user.name="$bot_name" \
      -c user.email="$bot_email" \
      commit-tree "$tree" -m 'Publish documentation'
)

if ! git -C "$repository_root" push --force -- "$destination_url" "$commit_oid:refs/heads/gh-pages"; then
  printf 'publish_docs.sh: failed to publish documentation to branch gh-pages\n' >&2
  exit 1
fi

printf 'Published documentation to branch gh-pages.\n'
