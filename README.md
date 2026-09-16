# DVD Server Android TV

Native Android TV client for the Flask DVD Server.

## What it does

- Shows every DVD folder returned by `GET /api/dvds`.
- Lets you open a DVD and choose a title.
- Plays the selected title through the existing `/api/dvd/stream/<title>` endpoint.
- Uses the server's alternate-audio remux endpoint when an alternate audio track is selected.
- Loads playable SRT/VTT/embedded text subtitles through `/api/dvd/subtitle/...`.
- Uses Android TV remote/DPAD focusable controls.
- Lets you change the DVD server address from the TV.
- Defaults to `http://192.168.1.100:4251`.

## Build

Open this folder in Android Studio and let Gradle sync.

Then select the `app` configuration and run/build it for an Android TV device or emulator.

The project intentionally does not include a Gradle wrapper. Android Studio can generate/use the appropriate wrapper for your installed Gradle/Android plugin setup.

## Server

The Flask server should be running on the same LAN and listening on:

    0.0.0.0:4251

The Android app permits cleartext HTTP because the supplied DVD server uses ordinary HTTP.

## Important server behavior

Your Flask server currently serves audio track 0 directly from the MKV. Alternate audio tracks are remuxed into cached MP4 files. The app therefore asks the server for:

    /api/dvd/stream/<title>?audio=<track>

Subtitle playback uses:

    /api/dvd/subtitle/<title>/<subtitle-id>

Bitmap subtitles such as PGS/VOBSUB are not exposed as browser/Media3 text subtitles by the current server, so they remain unavailable in this client too.

## TV controls

- DPAD: navigate
- OK/Enter: select
- Back: return
- Player controls: standard Media3 controls
- Audio/Subtitles: select track while playback is paused or the top bar is visible

## Suggested next additions

This is deliberately a small native client around the API you already have. A richer version could add poster/thumbnail generation, recently watched titles, resume position, chapter selection, search, and automatic server discovery.
