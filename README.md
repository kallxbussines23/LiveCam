# LiveCam

Android Native Kotlin camera and media gallery app using CameraX.

## Features
- Comic-inspired 2D interface with bold outlines and bright colors
- Live Photo mode with manual recording start/stop
- Motion Photo mode (still photo + short video after shutter)
- Built-in gallery with All, Photo, Video, and Live filters
- Live moment viewer: open a paired photo and play its motion clip inside LiveCam
- Front/back camera switch
- Saves stills to Pictures/LiveCam and clips to Movies/LiveCam
- Minimum Android 10 (API 29)

## Build in CodeAssist
1. Clone/download this repository.
2. Open the project root (folder containing settings.gradle).
3. Sync Gradle and install dependencies.
4. Run on an Android 10+ device and grant camera and microphone permissions.

## Current limitations
Still images and motion clips are stored as separate media files. LiveCam pairs its own photo and clip in the in-app gallery using a shared moment ID; it does not yet create a single embedded JPEG/HEIC Motion Photo file. Live Photo recording is manual and does not use an automatic pre-capture ring buffer. The in-app gallery currently focuses on browsing and playback.
