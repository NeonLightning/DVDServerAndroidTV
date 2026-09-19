# DVD Server Android TV

Feature-rich native Android TV client for the Flask DVD Server.

## Key Features

- **Sub Folder Tree Viewing**: Hierarchical browsing organized by Genres and nested Subpath folders with collapsible nodes.
- **User Profiles & Watch Progress**: Create and switch user profiles synced directly with the server backend (`/api/users` and `/api/progress`).
- **Playback Position Resuming**: Automatically resumes video playback from where you left off.
- **Watch Status & Progress Indicators**: Displays `[Watched]` and `[In Progress: XX% (HH:MM / HH:MM)]` badges directly on titles.
- **Individual Title Progress Reset**: Easily reset watch progress per title with the "RESET TITLE PROGRESS" action.
- **Hybrid Subtitle Engine**:
  - Native ExoPlayer text subtitle rendering for SRT / text WebVTT tracks.
  - Synchronized image overlay rendering via Glide for DVD image-based VTT/PGS subtitles.
- **Audio & Subtitle Track Selection**: Switch audio tracks and subtitle tracks seamlessly without losing playback position.
- **Customizable UI Themes**: 14+ themes including Android TV, Terminal, Midnight, Dracula, Nord, Cyberpunk, and Hot Dog Stand.
- **Automatic Server Discovery**: Discovers DVD servers automatically on your local network via mDNS.

## Build

Open this folder in Android Studio and let Gradle sync.

Then select the `app` configuration and run/build it for an Android TV device or emulator.

## Server

The Flask server should be running on the same LAN and listening on:

    0.0.0.0:4251

The Android app permits cleartext HTTP because the supplied DVD server uses ordinary HTTP.

## TV Controls

- **DPAD**: Navigate UI, menus, and media controls.
- **OK/Enter**: Select items.
- **Back**: Return / navigate back.
- **Player top bar**: Access Back, Audio track selection, Subtitle track selection, and Aspect Ratio scaling.
