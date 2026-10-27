# Aurora Native

Standalone Java / Android Views adaptation of CleanYourTV, pinned to
`76a9312cd9ac378fc8698f31427805af32c6f965`, with the approved minimal Aurora home.
Imported from the native Aurora implementation in `huzaifa-d/GTvLauncher`;
there is no Flutter project or runtime in this module.
See `UPSTREAM.txt`, `THIRD_PARTY_NOTICES.txt` and this module's GPLv3 `LICENSE`.
The original CleanYourTV launcher, tools and root MIT licence are unchanged.

| Item | Value |
| --- | --- |
| Display name | Aurora Native |
| Application ID | `com.huzaifa.aurora.nativehome` |
| Activity | `com.gathin.tvhome.AuroraActivity` |
| Minimum Android | 8.0 / API 26 |
| Local checkout | `A:\Repos\CleanYourTV` |
| Fork / branch | `huzaifa-d/cleanyourtv` / `main` |

It installs alongside Neons and CleanYourTV. It does not choose default Home,
disable packages, run cleanup, intercept global remote keys or enable services.
There are no storage, network, accessibility or privileged-settings permissions.

## Build

Requires JDK 17+ (the local build uses Android Studio's JDK 21), Android SDK 34 and
build-tools 34.0.0. The independent wrapper uses Gradle 8.9 / AGP 8.5.2.

```powershell
Set-Location 'A:\Repos\CleanYourTV'
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='A:\Android'
.\native-aurora\gradlew.bat -p .\native-aurora --console=plain `
  :app:testDebugUnitTest :app:lintRelease :app:assembleRelease `
  :app:assembleDebugAndroidTest
```

Release APK: `native-aurora\app\build\outputs\apk\release\app-release.apk`.
Release builds use R8/resource shrinking and the local Android debug signing key
for personal sideloading, **not a production distribution key**. Updates require
the same signing key.

Artwork/fonts are staged from this module's `assets` folder. The unchanged
`native-aurora\assets\aurora\blue-bokeh.mp4` is included in source and the APK,
so builds do not need an untracked private file. The user confirmed ownership
or permission to redistribute both supplied backgrounds in this public fork
and its APK. This does not grant an additional open artwork licence or rights
to the brand marks; see `assets\aurora\CREDITS.txt`.
Nothing downloads or transcodes the video. Builds without the MP4, or devices
that cannot decode it, retain the still wallpaper.

## Install and provide an MP4

Select an already authorised device from `adb devices -l`. Replace
`SELECTED_TV_SERIAL` below with its exact serial; these instructions do not
discover/connect to network TVs or change device authorisation.

```powershell
$adb='A:\Android\platform-tools\adb.exe'
$serial='SELECTED_TV_SERIAL'
& $adb -s $serial install -r `
  'A:\Repos\CleanYourTV\native-aurora\app\build\outputs\apk\release\app-release.apk'
& $adb -s $serial shell am start -n `
  'com.huzaifa.aurora.nativehome/com.gathin.tvhome.AuroraActivity'
```

Open **profile / Menu > Background** once. It displays the actual app-owned
directory, normally the following path for Android's primary user.
Android-device paths intentionally use `/`; PC paths use `\`.

Transfer to a temporary `.part` name, then rename it only when the push succeeds:

```powershell
& $adb -s $serial push 'A:\Users\modanish\Videos\Looping\Blue Bokeh.mp4' `
  '/sdcard/Android/data/com.huzaifa.aurora.nativehome/files/backgrounds/Blue Bokeh.mp4.part'
if ($LASTEXITCODE -eq 0) {
  & $adb -s $serial shell mv `
    '"/sdcard/Android/data/com.huzaifa.aurora.nativehome/files/backgrounds/Blue Bokeh.mp4.part"' `
    '"/sdcard/Android/data/com.huzaifa.aurora.nativehome/files/backgrounds/Blue Bokeh.mp4"'
}
```

In Background, choose **Refresh files**, then the filename. Only complete,
direct-child `.mp4` files are listed; `.part`, subdirectories and paths outside
the app folder are ignored. Use a new filename rather than overwriting a playing
file. If a TV firmware denies ADB access to this app-specific folder, report that
restriction; the app does not broaden permissions or expose an import server.

**Uninstalling Aurora Native deletes its copied backgrounds and preferences.**
The PC originals and Neons are unaffected.

## Controls

| Action | Result |
| --- | --- |
| Cold launch | No visible selection |
| Right / Down from idle | Netflix |
| Left from idle | All Apps |
| Up | Profile |
| Left / Right in row | Adjacent shortcut; no wrapping |
| Down from profile | Last row item, initially Netflix |
| OK | Open shortcut, All Apps or profile hub |
| Menu on Home | Profile/settings hub |
| Back in panel | Close that panel |
| Back on Home | Clear selection |

Branded shortcuts are white at rest and colour on focus. All Apps is labelled
and scrollable. Missing applications show a message, never a store redirect.
The clock uses the device's timezone and 12/24-hour preference.

The hub contains Background, Show RAM counter, HDMI / Inputs, System settings,
App management and About/credits. Inputs use Android TV channel intents and
Sony fallbacks; they do not remap physical remote keys.

Background speed choices are **0.25x through 2x in 0.25x steps**, initially
**0.5x**. The unchanged 12-second Blue Bokeh source therefore has a 24-second
loop at the default speed. Selection, speed and RAM visibility persist across
restarts and in-place updates. **Default background** resets the source, not the
chosen speed.

Playback is silent, looping and centre-cropped through one native MediaPlayer /
SurfaceView. Opening a panel, leaving Home, losing the surface or screen-off
releases the player. Returning restarts from the beginning. Failed, missing,
unsupported or timed-out media falls back to the still wallpaper without a retry
loop. Settings remain available to reset the source or speed.

The RAM counter defaults on. Turning it off hides both lines and cancels polling;
there is no background polling service. Enabled readings run off the UI thread
at five-second intervals after the previous sample completes, only on active
Home:

| Reading | Meaning |
| --- | --- |
| App RAM (PSS), MiB | This process's proportional set size; denominator is total device RAM |
| TV RAM, GiB | `totalMem - availMem`, divided by total device RAM |

Android may cache PSS. It is not a full accounting of decoder/GPU memory in other
processes, and TV RAM includes the OS and all applications. Unavailable data is
labelled unavailable, not fabricated as zero.

## Verification boundaries

JVM policy tests, Android lint, release APK audit and local emulator UI tests are
separate from Sony hardware acceptance. Before relocation, the native app
passed 28 JVM tests and 12 Android UI/playback tests on an isolated Android 14
TV / 1080p emulator. The relocated module is rebuilt with its JVM tests and
release lint (zero errors; advisory warnings remain). APK auditing checks for
permissions, Flutter runtime and test media, verifies the signature, and checks
the unchanged default video's hash.

An emulator cannot establish Sony remote/input compatibility, 4K AV1 decoder
behaviour, supported speeds, loop quality or real RAM savings.

Before calling this verified on the Sony TV, exercise app launch/return, all
inputs, actual ADB transfer, original Blue Bokeh, every speed, repeated panel
open/close, missing/corrupt media, sleep/wake and RAM toggling. Installation does
not make Aurora the default Home. No performance gain is claimed without
equivalent-workload release measurements on the same TV.

To run Android tests against an explicitly selected local emulator/device:

```powershell
$env:ANDROID_SERIAL='SELECTED_TEST_SERIAL'
.\native-aurora\gradlew.bat -p .\native-aurora --console=plain `
  :app:connectedDebugAndroidTest
```

The tests change only this app's preferences/files and open its native UI. Do not
run them against an arbitrary first connected device.
The bundled Android-test-only `playback-test.mp4` is a synthetic 320x180, one-second
H.264 fixture generated with FFmpeg's `testsrc2`; it is not the personal clip and
is not included in the release APK. It exercises actual file playback, looping,
half-speed parameters, fallback and native-player release.
