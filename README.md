# 📱 MoodBeats - Standalone Android Music App

[![Download APK](https://img.shields.io/badge/Download-MoodBeats%20APK-brightgreen?style=for-the-badge&logo=android)](https://github.com/ANURAG120612/MoodBeats/releases/latest)
![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B-blue?style=for-the-badge&logo=android)
![Architecture](https://img.shields.io/badge/Architecture-100%25%20Pure%20Native-orange?style=for-the-badge)
![Status](https://img.shields.io/badge/Status-Working%20%26%20Tested-success?style=for-the-badge)
[![Release](https://img.shields.io/badge/Release-v3.1.0-brightgreen?style=for-the-badge)](https://github.com/ANURAG120612/MoodBeats/releases/tag/v3.1.0)

**MoodBeats** is an intelligent, real-time emotion-aware music streaming application for Android. It operates **100% standalone** (like Spotify) — requiring **zero PC server connection**.

---

## 🌟 Key Features

- 🎧 **100% Standalone & Offline Mode**:
  - No PC server, background Python scripts, or terminal commands needed.
  - Pre-bundled studio-quality offline songs ready to play instantly with zero internet:
    - *Ilahi* — Arijit Singh (*Happy / Energetic*)
    - *Kesariya* — Arijit Singh (*Romantic / Love*)
    - *Channa Mereya* — Arijit Singh (*Sad / Soulful*)
    - *O Mere Dil Ke Chain* — Kishore Kumar (*Calm / Classic*)
- ☁️ **Cloud Streaming & Worldwide Search**:
  - Connects directly to global cloud music networks whenever Wi-Fi or mobile data is turned on.
  - Search any artist, song, or album in real time with instant streaming.
- 🎭 **Live Camera Emotion Recognition**:
  - Real-time facial expression analysis using Russell's 2D Circumplex Model of Affect (Valence & Energy).
  - Dynamically classifies emotions (Happy, Calm, Energetic, Sad, Neutral) to recommend matching musical tracks.
- 🎼 **Mood-Adaptive Playlists**:
  - Instant one-tap mood curation chips (*All Songs, Happy, Energetic, Calm, Sad, Romantic, Offline Only*).
- 🚀 **Pure Native Android UI**:
  - Built with native Java view hierarchies and hardware-accelerated `MediaPlayer`.
  - Zero WebView dependency for core playback — renders smoothly at 60 FPS.
- 🎵 **Spotify-Style Bottom Player**:
  - Interactive scrubbable seek bar, current/total time display, play/pause, next/previous controls, and album artwork.
- ⚙️ **Smart Server Selector**:
  - Switch anytime between Standalone Offline Mode, Cloud Streaming, or optional local LAN PC Server.

---

## 📥 How to Download & Install

1. Tap the green **[Download MoodBeats APK](https://github.com/ANURAG120612/MoodBeats/releases/latest)** badge above.
2. Under the **Assets** section of the latest release, click **`MoodBeats.apk`**.
3. Once downloaded to your Android device, tap the file to install (allow "Install Unknown Apps" if prompted).
4. Open **MoodBeats** and enjoy!

---

## 🛠 Project Structure & Building

```
MoodBeats_Android/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/aurabeats/moodbeats/
│   │       │   ├── MainActivity.java
│   │       │   ├── CloudMusicApi.java
│   │       │   ├── FavoritesManager.java
│   │       │   ├── Song.java
│   │       │   └── SongAdapter.java
│   │       ├── res/
│   │       │   ├── layout/activity_main.xml
│   │       │   ├── raw/ (Bundled offline audio tracks)
│   │       │   └── drawable/
│   │       ├── assets/www/ (Offline web assets)
│   │       └── AndroidManifest.xml
│   └── build.gradle
├── docs/
│   ├── MoodBeats_Phase2_Presentation.pptx
│   ├── MoodBeats_Phase2_Report.docx
│   ├── MoodBeats_Presentation.pptx
│   └── MoodBeats_Synopsis.docx
├── build_native_apk.py (Native zero-Gradle AAPT2+D8 pipeline)
├── build_apk.bat
├── build.gradle
├── settings.gradle
└── README.md
```

### Rebuilding the APK

You can build the APK in two ways:

#### Option 1: Native CLI Build (Instant)
Run the automated native Python pipeline:
```cmd
python build_native_apk.py
```
Or double-click `build_apk.bat`.

#### Option 2: Android Studio / Gradle
Open the project folder in **Android Studio** and click **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

---

## 💻 Technology Stack

- **Platform**: Android (API 24+ Android 7.0 to API 34+ Android 14)
- **Core Languages**: Java 8, XML
- **Audio Engine**: Android Hardware-Accelerated `MediaPlayer`
- **Networking**: Native `HttpURLConnection` & Public Music APIs
- **Build Pipeline**: AAPT2, D8 Dexer, zipalign, apksigner (v1, v2 & v3 schemes)

---

## 👨‍💻 Author & Credits

- **Author**: Anurag ([@ANURAG120612](https://github.com/ANURAG120612))
- **Course**: BCA (AI / Data Science) — 3rd Semester
- **Institution**: Graphic Era (Deemed to be University), Dehradun
