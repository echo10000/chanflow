# ChanFlow

A fresh, independent Android 4chan reader with a vertical media feed, private offline
library and a working desktop preview. This is original MIT-licensed source, not a
fork of ChanVault, Chance or Kuroba.

## Try on your desktop

Requires Node.js 22 or newer. Run `npm ci` followed by `npm run dev`, then open
[http://127.0.0.1:4173](http://127.0.0.1:4173). The local server proxies only supported
4chan API/media paths. It listens on your computer's loopback interface.

The preview uses real boards and attachments. Choose a thread and scroll, swipe or
press Up/Down to change files. Swipe sideways or press Left/Right to change threads
on the current board. Use the board picker to change boards. Space pauses videos.
Desktop/Phone changes the preview's width. Back returns to your previous scroll spot.
Save writes media into this browser's IndexedDB, without sending it to your Downloads
folder. Browser storage is separate from Android storage. Clearing browser site data
removes desktop saves. Browser codec support may differ from Android's.

## Features

- Explore catalogs, find boards, read post text and swipe through a thread's media.
- Images, GIFs, WebM and MP4 playback, fit/fill, mute and seek controls on Android.
- Save a file or an entire thread. Durable Android background jobs, duplicate
  prevention, progress, cancellation and isolated failures.
- Downloads remain in the app's private storage, organized into thread folders with
  file counts, storage size and watched counts. Browse and play saved files offline.
- Select and delete entire saved threads, or select individual files inside a folder.
  A confirmation shows the file count and space that will be removed.
- Catalogs and saved folders remember their scroll positions during navigation;
  each thread remembers the last file you opened. Boards keep separate positions.
- Recently watched retains up to 200 local records and video resume positions.
  Filter downloads by watched/unwatched. Clear history without deleting saved files.
- Look-ahead loading prepares the next item while you watch. Android prefetches up
  to 5 MiB into a 128 MiB LRU cache; desktop keeps at most 64 MiB of prefetched media,
  limited to 32 MiB per look-ahead file. Cache can be evicted; saved media is permanent
  until you delete it or uninstall/clear app data.
- One Media3 player per native feed and lazy grids. No image decoding on the UI thread.
- GitHub Release update checks, private APK download and signing-certificate
  verification before handing an update to Android's installer.

Safe boards are shown by default. Adult boards can be enabled in Find a board.
No accounts, analytics, posting, cloud library or algorithmic recommendations.

## Android builds and updates

Install `chanflow.apk` from [Releases](https://github.com/echo10000/chanflow/releases).
Signed releases update in place and preserve saved files. Android still requires your
confirmation to install and may ask you to allow updates from ChanFlow.
Debug builds use a separate `.preview` application ID and cannot update stable installs.

Every main-branch push runs unit tests, Android lint and APK builds in GitHub Actions.
An emulator job verifies offline vault persistence. For a release:

1. Change `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Push the code to main, then push a matching `vX.Y.Z` tag.
3. The release workflow signs the APK with the permanent key and publishes it.
4. On the phone, Settings → Check for updates → Download update → Install.

The permanent signing key lives in GitHub Actions secrets `CHANFLOW_KEYSTORE_B64` and
`CHANFLOW_KEYSTORE_PASSWORD`, not in this repository. Keep a separate private backup
of the key: replacing it prevents existing installations from updating in place.

For local builds, use JDK 17, Android SDK 35 and `./gradlew :app:assembleDebug`.
Release builds additionally require `CHANFLOW_KEYSTORE_PATH` and
`CHANFLOW_KEYSTORE_PASSWORD` environment variables.

## Checks

`npm test` exercises desktop proxy path restrictions. Android unit tests cover attachment
validation, metadata and version ordering. Run `./gradlew :app:testDebugUnitTest
:app:lintDebug :app:assembleDebug`. Android instrumentation tests require a device/emulator.
See [QA.md](QA.md) for the interactive verification inventory and [DESIGN.md](DESIGN.md)
for the design references.

Independent project, not affiliated with 4chan. Uses the official read-only
[JSON API](https://github.com/4chan/4chan-API), with one API request per second and a
minimum 30-second in-memory thread cache. Expired network media may no longer open;
downloaded files continue to work offline.
