# Releasing SideType

SideType is distributed as a signed APK. The recommended update channel is **[Obtainium](https://github.com/ImranR98/Obtainium)** pointed at this repo's **GitHub Releases** — it needs zero server infrastructure and works with the current `applicationId` (`fi.palonkorpi.sidetype`).

> **Why not official F-Droid?** F-Droid already hosts upstream TT9 under `io.github.sspanak.tt9`, and it cannot list a second app with a different id under the same source without a separate metadata submission + reproducible-build review (slow). Obtainium is the fast, low-friction path. Hosting your **own** F-Droid repo (GitHub Pages + `fdroid` tool) is a nice v2 upgrade — users add one URL and get in-client auto-updates.

## One-time: create a signing key

Every release **must** be signed with the **same** key, or Android refuses to install the update over the previous version.

```bash
keytool -genkey -v -keystore sidetype-release.jks \
    -keyalg RSA -keysize 2048 -validity 10000 -alias sidetype
```

Then copy `keystore.properties.example` → `keystore.properties` (git-ignored) and fill in the store path + passwords. **Back up `sidetype-release.jks` and its passwords somewhere safe** — lose them and you can never ship an update to existing installs.

## Cut a release

`applicationId` is `fi.palonkorpi.sidetype`, so SideType installs **alongside** stock TT9 (T9-tile users keep theirs). Only the `full` flavor is shipped.

Version numbers are normally derived from git, but that is unreliable from a shallow clone, so pin them explicitly. `versionCode` **must increase every release** (Obtainium/F-Droid use it to detect "newer"):

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # JDK 21 required to run Gradle
./gradlew :app:assembleFullRelease \
    -PreleaseVersionCode=1 \
    -PreleaseVersionName=1.0
```

Start `versionCode` at `1` and bump it by one for every subsequent release (`2`, `3`, …).

The signed APK lands in `app/build/outputs/apk/full/release/` (named `tt9-v<versionName>-full.apk`) with the
version you passed. Upload it to a GitHub Release (tag e.g. `v1.0`).

> Note: a `assembleFullRelease` run leaves `app/src/main/AndroidManifest.xml` with a changed
> `versionCode` (a cosmetic post-build rewrite based on git commit count — it does **not** affect the built
> APK, which uses the `-P` value). Just discard it: `git checkout app/src/main/AndroidManifest.xml`.

## Users: install + get updates

1. Enable the SideType keyboard: **Settings → System → Languages & input → On-screen keyboard → Manage → SideType**, then switch to it with the keyboard picker.
2. For updates, install **Obtainium**, add this GitHub repo URL, and it will notify on each new Release.

Sideloading a single APK also works (download → open → allow "install unknown apps") — you just won't get automatic update notifications.
