# Garmin Edge Audio Remote

🇸🇰 [Slovak version / Slovenská verzia](README.md)

A custom Connect IQ widget for Garmin Edge bike computers (530, 830, 1030,
1030 Plus) that, together with a companion Android app, controls audiobook
and podcast playback (Smart AudioBook Player, Pocket Casts) directly from
the head unit's screen — play/pause, -10s, +10s, skip to next queue item,
with a progress bar and title display.

## Why this exists

Older Edge generations (530/830/1030) don't have a native "Music Controls"
widget (that only arrived with the 540/840). On top of that, Garmin blocks
third parties from sending Bluetooth AVRCP commands (play/pause/seek) to
the phone's system media player — so a plain Connect IQ widget could never
reach the player at all.

## How it works

The solution sidesteps the AVRCP block entirely by not using Bluetooth
media commands at all — it consists of two parts that must run at the
same time:

- `edge-widget/` — Connect IQ widget (Monkey C), shows the title, a
  progress bar, and 4 buttons (-10s, next-in-queue, +10s, play/pause).
- `android-companion/` — Android app (Kotlin) that runs in the background
  on the phone and, via `MediaSessionManager`/`NotificationListenerService`,
  controls the target app's `MediaSession` directly — this is a system
  API, not Bluetooth, so Garmin's AVRCP block doesn't apply.

The widget and the app communicate over `Toybox.Communications` / the
Garmin Connect IQ Mobile SDK, riding on the watch's existing pairing in
the Garmin Connect Mobile app.

## 1. Widget on the Edge

### Prerequisites

- [Connect IQ SDK Manager](https://developer.garmin.com/connect-iq/sdk/) —
  download and install it, sign in with a Garmin Connect Developer account
  (no paid developer account needed, a regular Garmin account is enough).
- In the SDK Manager, install an SDK at least matching `minSdkVersion="3.2.0"`
  from the manifest (the current SDK satisfies this automatically).
- Generate a developer signing key via the SDK Manager (Utilities →
  Generate Developer Key) if you don't have one yet.

### Build and sideload

```bash
cd ~/garmin-audio-remote/edge-widget

# Compile to .prg (check the exact path to monkeyc in your SDK install)
monkeyc -f monkey.jungle -o bin/AudioRemote.prg \
    -y ~/.Garmin/ConnectIQ/developer_key.der \
    -d edge830

# Prepare the Edge: connect via USB, it will show up as a GARMIN drive
cp bin/AudioRemote.prg /Volumes/GARMIN/GARMIN/Apps/
```

Replace `edge830` with `edge530`, `edge1030`, or `edge1030plus` to build
for a different supported device.

After disconnecting the USB cable, the widget should appear in the widget
loop (UP/DOWN button or swipe, since the Edge has a touchscreen). Since
this is a sideload (not the Connect IQ Store), Garmin Connect Mobile won't
manage/update the widget — new versions need to be copied over USB again.

**Note on UUID:** the manifest uses `id="7ce36164-617d-483b-8c29-c1eff82bc95c"`.
This value (without dashes: `7ce36164617d483b8c29c1eff82bc95c`) is also
hardcoded in the Android app (`Constants.kt` → `WATCH_APP_ID`) — **they
must match**, otherwise the watch and phone won't find each other's apps
via the Connect IQ Mobile SDK. If you ever change the manifest UUID, make
sure to update `Constants.kt` too.

## 2. Android companion app

### Prerequisites

- Android Studio (latest stable version).
- A phone with **Garmin Connect Mobile** installed, with the Edge already
  paired in it (the Connect IQ Mobile SDK rides on this pairing — the
  companion app itself doesn't need a separate pairing).
- **Smart AudioBook Player** and/or **Pocket Casts** installed.

### Build

```bash
cd ~/garmin-audio-remote/android-companion
```

Open the folder in Android Studio ("Open" → select `android-companion/`).
Android Studio will automatically regenerate `gradle-wrapper.jar` and the
wrapper scripts on first open (they're missing from this repo since it's
a binary file). After syncing, build and install the app on the phone via
Run ▶ in Android Studio, or from the command line:

```bash
./gradlew installDebug
```

### One-time setup after install

1. Open the "Audio Remote" app on the phone.
2. Tap **"Allow notification access"** → find "Audio Remote" in the
   system settings and enable it. Without this, the app can't see what's
   playing in other apps (`MediaSessionManager` requires it).
3. Tap **"Disable battery optimization for the app"** → confirm.
   Otherwise Android will eventually put the foreground service to sleep
   and the widget will stop receiving updates.
4. After returning to the main screen, the app automatically starts
   `AudioRemoteService` (a foreground service, visible as a persistent
   "Audio Remote running" notification).

### How the app picks which player to control

Automatically: if Smart AudioBook Player or Pocket Casts is actively
playing (STATE_PLAYING) among the phone's active MediaSessions, the app
controls that one. If neither is playing but one has an active (paused)
session, it controls that one. If none of the whitelisted apps has a
session at all, the app falls back to whichever other app happens to be
playing — so the widget doesn't stay completely dead with a different
player, just without guaranteed behavior.

## 3. Verifying it works

1. Start playback in Smart AudioBook Player or Pocket Casts on the phone.
2. Open the "Audio Remote" widget on the Edge (widget loop).
3. The chapter/title name should appear and the progress bar should sync
   within a few seconds (up to ~3s, the app polls).
4. Tap play/pause, -10s, +10s and confirm the command takes effect in the
   phone's player.

### Debugging

- Widget: `monkeydo bin/AudioRemote.prg edge830` in the simulator (swap
  in another device ID as needed), or `adb logcat` on the phone side for
  `AudioRemoteService`/`ConnectIQ` logs.
- If the widget and app won't pair: check that the Edge is paired in
  Garmin Connect Mobile and that Bluetooth is on on both sides; the app
  looks up `connectIQ.knownDevices` only after the `onSdkReady()` callback.
- If the progress bar on the watch stutters or lags: the watch
  interpolates position locally between messages (`System.getTimer()`),
  so a short delay after manually seeking in the phone app is expected
  until the next poll update arrives (~3s).

## Limitations / known issues

- A sideloaded widget doesn't update via the Connect IQ Store — a new
  version requires copying the `.prg` file over USB again.
- If Bluetooth turns off or the Edge goes out of range while the app is
  running, `connectIQ.sendMessage()` fails silently (see the catch block
  in `AudioRemoteService.kt`) — the app retries on the next poll tick once
  the connection is restored.
- Package IDs `ak.alizandro.smartaudiobookplayer` (Smart AudioBook Player)
  and `au.com.shiftyjelly.pocketcasts` (Pocket Casts) were verified via
  Google Play (July 2026). Note that `de.ph1b.audiobook` is a different
  app ("Voice Audiobook Player") — it was originally mistaken for Smart
  AudioBook Player; `Constants.kt` already has the correct ID.
