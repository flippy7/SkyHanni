#!/usr/bin/env bash
set -euo pipefail

upstream_url="${UPSTREAM_URL:-https://github.com/SkyHanniStudios/SkyHanni.git}"
branch="${SYNC_BRANCH:-beta}"

git fetch --no-tags "$upstream_url" "$branch"
upstream_commit="$(git rev-parse FETCH_HEAD)"

if git merge-base --is-ancestor "$upstream_commit" HEAD; then
    echo 'The fork already contains the latest SkyHanni updates.' >> "$GITHUB_STEP_SUMMARY"
    exit 0
fi

if ! git merge --no-edit "$upstream_commit"; then
    git merge --abort
    echo '::error::SkyHanni updates conflict with fork changes. Nothing was pushed; resolve the merge locally.'
    echo 'Merge conflict: no changes were pushed. The previous release remains available.' >> "$GITHUB_STEP_SUMMARY"
    exit 1
fi

# A normal push refuses to overwrite any concurrent changes on GitHub.
if ! git push origin "HEAD:$branch"; then
    echo '::error::Could not push the merge. Check concurrent changes or GitHub workflow-file permissions; see FORK.md.'
    echo 'Push rejected: the previous release remains available. See FORK.md for recovery instructions.' >> "$GITHUB_STEP_SUMMARY"
    exit 1
fi
echo "Merged SkyHanni commit $upstream_commit into $branch, preserving fork commits." >> "$GITHUB_STEP_SUMMARY"
