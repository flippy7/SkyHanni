#!/usr/bin/env bash
set -euo pipefail
shopt -s nullglob

version="$(tr -d '\r\n' < buildTools/PROJECT_VERSION)"
tag="fork-$version-build-${BUILD_NUMBER:?}"
title="SkyHanni $version - flippy7 fork (build $BUILD_NUMBER)"
jars=(artifacts/SkyHanni-*-mc*.jar)
if [[ "${#jars[@]}" == "0" ]]; then
    echo '::error::The successful build did not provide any SkyHanni JARs.'
    exit 1
fi

mkdir -p release
for jar in "${jars[@]}"; do
    name="${jar##*/}"
    if [[ ! "$name" =~ -mc([0-9][0-9.]*)\.jar$ ]]; then
        echo "::error::Unexpected JAR name: $name"
        exit 1
    fi
    minecraft_version="${BASH_REMATCH[1]}"
    unzip -t "$jar" > /dev/null
    cp "$jar" "release/SkyHanni-Fork-mc$minecraft_version.jar"
done
(cd release && sha256sum *.jar > SHA256SUMS.txt)

cat > release-notes.md <<EOF
Personal SkyHanni fork by flippy7, based on upstream **$version (beta branch)**.

Includes two independent Garden keybinds per action, with Second Key options in the GUI.

Download the JAR matching your Minecraft version and replace the old SkyHanni JAR in your mods folder.

- Fork commit: $BUILD_SHA
- Successful build and tests: $BUILD_URL
- Upstream updates are merged daily when there are no conflicts.
- The in-game update checker still uses the official SkyHanni sources.
EOF

if [[ "${PUBLISH_DRY_RUN:-false}" == "true" ]]; then
    echo "Prepared $tag with ${#jars[@]} JARs; publication skipped for dry run."
    exit 0
fi

current="$(gh api "repos/$GITHUB_REPOSITORY/commits/beta" --jq .sha)"
if [[ "$current" != "$BUILD_SHA" ]]; then
    echo 'Skipped publication: beta has advanced while the release was being prepared.' >> "$GITHUB_STEP_SUMMARY"
    exit 0
fi

if gh release view "$tag" --repo "$GITHUB_REPOSITORY" > /dev/null 2>&1; then
    target="$(gh release view "$tag" --repo "$GITHUB_REPOSITORY" --json targetCommitish --jq .targetCommitish)"
    if [[ "$target" != "$BUILD_SHA" ]]; then
        echo '::error::The existing release points at a different commit.'
        exit 1
    fi
else
    gh release create "$tag" --repo "$GITHUB_REPOSITORY" --target "$BUILD_SHA" \
        --title "$title" --notes-file release-notes.md --draft
fi

gh release upload "$tag" release/* --repo "$GITHUB_REPOSITORY" --clobber
gh release edit "$tag" --repo "$GITHUB_REPOSITORY" --title "$title" \
    --notes-file release-notes.md --draft=false --latest
echo "Published [$title](https://github.com/$GITHUB_REPOSITORY/releases/tag/$tag)." >> "$GITHUB_STEP_SUMMARY"
