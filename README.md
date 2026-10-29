# LiveCam

Android Native Kotlin camera app with an in-app comic-style gallery, built on CameraX.

## Features
- Comic-inspired 2D camera and gallery interface
- Live Photo capture mode (manual start/stop recording)
- Motion Photo capture mode (photo + approximately 3-second clip)
- Combines JPEG + MP4 into one Motion Photo JPEG with Google Camera XMP metadata
- In-app gallery with All, Photo, Video, and Live filters
- Fullscreen photo viewer with embedded-motion playback
- Legacy separate photo/video items remain browsable
- Front/back camera switch
- Minimum Android 10 (API 29)

## Motion Photo implementation
LiveCam writes the still JPEG first, then appends the recorded MP4 bytes to the same .jpg file. It inserts an XMP APP1 segment with GCamera:MotionPhoto, GCamera:MotionPhotoVersion, and GCamera:MicroVideoOffset. The in-app viewer reads that offset and extracts the embedded clip to a temporary cache file for playback.

The combined file is one media item in Pictures/LiveCam. Compatibility with OEM gallery apps varies; LiveCam's own gallery is the reference viewer for this implementation. It is not a HEIC/Apple Live Photo package, and Live Photo capture currently starts recording after the still is taken (no rolling pre-capture buffer).

## Build in CodeAssist
1. Clone/download this repository.
2. Open the project root (folder containing settings.gradle).
3. Sync Gradle and install dependencies.
4. Run on Android 10+ and grant camera and microphone permissions.
