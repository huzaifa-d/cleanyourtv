# Aurora Native

Standalone Java / Android Views adaptation of CleanYourTV, pinned to
`76a9312cd9ac378fc8698f31427805af32c6f965`, with the approved Cherry Blossom home.
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
| Version | 1.4 / code 5 |
| Local checkout | `A:\Repos\CleanYourTV.CherryBlossom` |
| Fork / branch | `huzaifa-d/cleanyourtv` / `Cherry-BLossom` |

It installs alongside Neons and CleanYourTV. It does not choose default Home,
disable packages, run cleanup, intercept global remote keys or enable services.
The only permission is `ACCESS_NETWORK_STATE`, for the clock's connection icon.
There are no internet, storage, accessibility or privileged-settings permissions.

## Build

Requires JDK 17+ (the local build uses Android Studio's JDK 21), Android SDK 34 and
build-tools 34.0.0. The independent wrapper uses Gradle 8.9 / AGP 8.5.2.

```powershell
Set-Location 'A:\Repos\CleanYourTV.CherryBlossom'
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

Artwork/fonts are staged from this module's `assets` folder. The default is the
user-supplied 2000x1125 Cherry Blossom still with sixteen native drifting petal
sprites. No video decoder runs for the default scene. The supplied blossom and
six petal textures retain their original resolution; disconnected faint alpha
debris was removed without inpainting, sharpening or upscaling. The source sheet
and logo SVGs remain under `assets\aurora\cherry\source`, outside APK staging.
Original PC files are unchanged. Archived Blue Bokeh/Aurora media remains in
source but is not included in this branch's APK. Nothing downloads or transcodes
at runtime. No additional open artwork licence or rights to brand marks are
asserted; see `assets\aurora\CREDITS.txt`.

## Install and provide an MP4

Select an already authorised device from `adb devices -l`. Replace
`SELECTED_TV_SERIAL` below with its exact serial; these instructions do not
discover/connect to network TVs or change device authorisation.

```powershell
$adb='A:\Android\platform-tools\adb.exe'
$serial='SELECTED_TV_SERIAL'
& $adb -s $serial install -r `
  'A:\Repos\CleanYourTV.CherryBlossom\native-aurora\app\build\outputs\apk\release\app-release.apk'
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
| Up from upper row | Profile |
| Up / Down between rows | Nearest mapped blossom |
| Left / Right in row | Adjacent shortcut; no wrapping |
| Down from profile | Last row item, initially Netflix |
| OK | Open shortcut, All Apps or profile hub |
| Menu on Home | Profile/settings hub |
| Back in panel | Close that panel |
| Back on Home | Clear selection |

The staggered upper row is Netflix, Prime, YouTube and Disney+; the lower row is
Apple TV, JioHotstar and All Apps. Down maps the four upper shortcuts to
Apple TV, Apple TV, JioHotstar and All Apps; Up maps the lower shortcuts to
Netflix, YouTube and Disney+. Down at the bottom and horizontal row edges stop.
Seven differently angled blossoms carry upright white marks at rest.
Focus grows the blossom to 1.14x, gently turns only its flower another 8 degrees
and changes the logo to brand colours, with no selection box or fill.
Disney+ uses the full pre-2024 dark-blue wordmark and blue/cyan gradient arc.
Apple TV uses Apple's pearlescent mark on focus. All Apps is labelled
and scrollable. Missing applications show a message, never a store redirect.
Native home glyphs are rasterised at output resolution, including All Apps'
maximum focus size. Brand images are decoded for that same focused output size,
bounded by the original asset resolution. Library icons use their displayed
pixel dimensions and a size-aware cache.
The clock uses the device's timezone and 12/24-hour preference.
Its small white status icon shows Ethernet or Wi-Fi when Android validates the
default connection's internet access, or a crossed-out connection otherwise,
including captive portals. An unusual validated transport uses a generic online
glyph. This is system-reported status, not a guarantee every service is reachable.
Updates use a foreground network callback, without polling or external requests.

The greeting alternates fifteen seconds of English with ten seconds of Urdu,
using a gentle 400 ms fade/slide. Urdu uses bundled OFL-licensed Noto Sans Arabic with native
right-to-left shaping on one horizontal line, anchored beside the profile. Time-of-day updates preserve
the current language. The cycle stops behind panels and when Home is inactive;
returning starts a fresh English interval.

Sixteen falling petals share six native textures in one Canvas view, with
independent 14-36-second cycles, varied opacity, phase, size, angle and drift.
Each cycle fades out before wrapping. Frame callbacks stop behind panels,
when Home is inactive and on detach; resuming does not advance hidden time.
System-disabled animation stops the petal loop. The background itself is still.

The hub contains Background, Show RAM counter, HDMI / Inputs, System settings,
App management and About/credits. Inputs use Android TV channel intents and
Sony fallbacks; they do not remap physical remote keys.

Custom MP4s play at **1x**. There is no speed selector; previously saved speed
values are ignored. Selection and RAM visibility persist across restarts and
in-place updates. **Default background** returns to the Cherry Blossom still.

Playback is silent, looping and centre-cropped through one Media3 ExoPlayer /
SurfaceView, with audio/text tracks disabled. Two references to the same local
clip form a repeating playlist, queuing the next loop without the native
MediaPlayer's end-of-file seek/decoder flush. Only one video decoder is used;
compressed buffering targets 4 MiB and five seconds, not decoded-frame
or dual-player crossfading. Custom video frames/motion are unchanged;
a visible discontinuity already in any supplied clip is not edited away.
Opening a panel, leaving Home, losing the surface or screen-off
releases the player. Returning restarts from the beginning. Failed, missing,
unsupported or timed-out media falls back to the still wallpaper without a retry
loop. Settings remain available to reset the source.

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
separate from Sony hardware acceptance. Initial Cherry Blossom validation passes 34 JVM tests and
24 Android UI/playback tests on an isolated Android 14 TV / 960x540 emulator.
The final Apple TV/JioHotstar position swap is additionally checked against all
34 JVM tests and the four native navigation/lifecycle tests.
Icon tests cover physical output sizes through 4K; the release scene is also
inspected in the separate 1080p visible preview. Release lint has zero errors;
advisory warnings remain, including the programmatic-only view constructors.
APK auditing checks for
permissions, Flutter runtime and test media, verifies the signature, and checks
the staged Cherry Blossom assets and absence of bundled video.

An emulator cannot establish Sony remote/input compatibility, 4K AV1 decoder
behaviour, loop quality or real RAM savings.

Before calling this verified on the Sony TV, exercise app launch/return, all
inputs, actual ADB transfer, original Blue Bokeh at 1x, repeated panel
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
fixed 1x playback despite legacy preferences, fallback and native-player release.
