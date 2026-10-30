# flippy7's SkyHanni fork

This fork adds a second independent key for every Garden Custom Keybind action.
Open `/sh` → **Garden** → **Custom Keybinds** and assign the **Second Key** options.

## Download your fork

- [Latest release](https://github.com/flippy7/SkyHanni/releases/latest)
- [Minecraft 26.1 JAR](https://github.com/flippy7/SkyHanni/releases/latest/download/SkyHanni-Fork-mc26.1.jar)
- [Minecraft 26.2 JAR](https://github.com/flippy7/SkyHanni/releases/latest/download/SkyHanni-Fork-mc26.2.jar)

Download the matching JAR, close Minecraft, remove the old SkyHanni JAR from your
`mods` folder, and put the downloaded JAR there. Keep only one SkyHanni JAR installed.
The release also contains SHA-256 checksums.

The in-game update checker intentionally still uses the official SkyHanni sources.
Use the links above to install this fork with your custom keybind changes.

## Updates from SkyHanni

The **Sync SkyHanni** GitHub Actions workflow checks upstream `beta` daily at
05:23 UTC. It merges updates into this fork's `beta` branch using a normal Git
merge, keeping the fork's commits. It does not force-push or reset your changes.

If anything conflicts, the workflow fails without pushing the conflicting merge.
The last successful release remains available. Resolve conflicts locally and
push the result to `origin/beta` to resume normal builds.

If upstream changes GitHub workflow files, GitHub can reject the automatic push
because its built-in token cannot grant workflow-file write permission. In that
case, merge and push locally using the commands below. To automate that case too,
you can optionally add a repository Actions secret named `FORK_SYNC_TOKEN` with a
fine-grained token restricted to this repository and **Contents: read and write**
and **Workflows: read and write**. The sync checkout uses that token when present.
No personal token is required for ordinary source updates.

To check immediately, open [Sync SkyHanni](https://github.com/flippy7/SkyHanni/actions/workflows/fork-sync.yml)
and choose **Run workflow**. The workflow explicitly requests **Build** after a
sync if the current commit does not already have a running or successful build.
This also retries a failed build on the next daily check.

Every successful **Build** for the current `beta` commit triggers **Publish fork
release**, which downloads the tested JARs and publishes a release with stable
download filenames. Pull request builds cannot publish releases. Older builds
cannot replace the latest release after `beta` has moved on.

GitHub may disable scheduled workflows after 60 days without repository activity;
re-enable **Sync SkyHanni** in Actions if GitHub reports that it is disabled.

## Local development

Push your changes to `origin/beta`. GitHub will build and test them, then publish
the new release automatically if the build passes.

To merge upstream locally:

```sh
git fetch upstream beta
git switch beta
git merge upstream/beta
git push origin beta
```
