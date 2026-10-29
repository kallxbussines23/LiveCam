# LiveCam

Android Native Kotlin camera app starter using CameraX.

## Features
- Live Photo mode (manual clip start/stop around the moment)
- Motion Photo mode (still photo + short video after shutter)
- Front/back camera switch
- Saves stills to Pictures/LiveCam and clips to Movies/LiveCam
- Minimum Android 10 (API 29)

## Build in CodeAssist
1. Clone/download this repository.
2. Open the project root (folder containing settings.gradle).
3. Sync Gradle and install dependencies.
4. Run on an Android 10+ device and grant camera permission.

## Important limitation
This first version stores the still image and video as separate files. It does not yet create a single embedded JPEG/HEIC Motion Photo file. Live Photo uses a manual recording flow rather than an automatic pre-capture ring buffer. The gallery button opens the system gallery; an in-app paired-media gallery is planned.
