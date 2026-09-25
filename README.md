# 👻 Ouija — Paranormal Spirit Board & Remote Prank App

An atmospheric, interactive Ouija board Android application featuring a dual-mode client-controller architecture. Designed as an authentic spirit board experience with a built-in remote control prank system powered by Supabase Realtime.

---

## 📸 Overview

The application functions in two distinct modes within a single APK:

1. **Client Mode (Victim's Device)**:
   - Displays an immersive, gothic spirit board with aged wood textures, blood drip spatters, flickering candles, and custom Captain Howdy typography.
   - Allows genuine interaction: dragging the planchette with haptic feedback, magnifying letters through the glass lens, and standalone board exploration.
   - Silently connects to a private room channel to listen for remote commands.
   - Includes a hidden admin menu (accessible by tapping the celestial Moon icon) to retrieve the unique 5-letter session code or switch to Controller mode.

2. **Server / Controller Mode (Prankster's Device)**:
   - Connects to the victim's device using the 5-letter session code.
   - Displays live telemetry: planchette coordinates, targeted letters, and connection state.
   - Features an **Answer Console** that remotely animates the client's planchette to spell words or respond (YES, NO, GOODBYE).
   - Equips a **Scare Control Panel** to trigger instant remote scares: audio clips, video jump scares, flashlight strobe, violent vibrations, screen cracks, demonic TTS, and visual glitches.

---

## ✨ Key Features

### 🕯️ Interactive Spirit Board Canvas
- **Procedural Canvas Rendering**: Built entirely with Jetpack Compose Canvas, featuring weathered grain splits, chiseled blade gouges, directional blood splashes, and coagulated blood drips.
- **Dynamic Atmosphere**: Animated flickering candle flames with melting wax trickles, drifting mist overlays, and poltergeist screen shake effects.
- **Planchette Physics & Feedback**: Smooth dragging, idle wobble physics, optical lens letter magnification, and arrival/tick haptics.
- **Custom Gothic Typography**: Styled after classic spirit boards using the embedded *Captain Howdy* font family along dual celestial letter arches.

### 🎭 Remote Prank & Scare Engine
- **Planchette Spell Casting**: Type custom text on the controller to guide the victim's planchette across the board letter-by-letter with adjustable speeds.
- **High-Impact Audio Scares**: Trigger localized sound effects including screams, heavy breathing, creaking doors, and ominous water drops with sudden burst audio.
- **Video Jump Scares**: Hardware-accelerated full-screen jump scare videos powered by AndroidX Media3 (ExoPlayer).
- **Hardware Integration**:
  - **Camera Torch**: Strobe patterns and sudden room blackouts using Android `CameraManager`.
  - **Advanced Haptics**: Heartbeat pulses, violent rumbles, and seismic shocks via `Vibrator` and `VibratorManager`.
  - **Demonic TTS**: Pitch-shifted Text-To-Speech engine speaking eerie phrases.
- **Fake System Glitches**: In-app overlays simulating fake 1% battery shutdowns, shattered screen cracks, and mysterious presence warnings.
- **Safety Panic Button**: A dedicated `STOP ALL` button to instantly terminate active video, audio, flashlight, and vibration effects.

---

## 🛠️ Tech Stack & Architecture

- **Platform**: Android (minSdk 26, targetSdk 35)
- **Language**: Kotlin 2.x
- **UI Framework**: Jetpack Compose, Material 3, Custom Canvas Drawing
- **Realtime Networking**: [Supabase Realtime](https://supabase.com/docs/guides/realtime) (Broadcast & Presence channels via `supabase-kt`)
- **HTTP Engine**: Ktor Client (`ktor-client-okhttp`)
- **Serialization**: Kotlinx Serialization JSON
- **Media Playback**: AndroidX Media3 ExoPlayer
- **Architecture**: Single-Activity Architecture (`MainActivity`), Clean Architecture, Unidirectional Data Flow (UDF) with Kotlin Coroutines and `SharedFlow`/`StateFlow`

---

## 📂 Project Structure

```text
app/src/main/
├── assets/fonts/               # Captain Howdy custom gothic font
├── java/in/ouija/
│   ├── MainActivity.kt         # Full-screen edge-to-edge container & navigation
│   ├── OuijaApp.kt             # Application class hosting Realtime and Effect singletons
│   ├── core/
│   │   ├── commands/           # Sealed Command classes, packet model, JSON parser
│   │   ├── effects/            # EffectManager orchestrating sound, video, torch, haptics, TTS
│   │   ├── realtime/           # RealtimeManager & Supabase channel lifecycle
│   │   ├── telemetry/          # Telemetry data models (coordinates, active letters)
│   │   └── utils/              # CodeGenerator (persistent 5-letter session codes)
│   ├── service/
│   │   └── ClientForegroundService.kt # Background service maintaining session persistence
│   └── ui/
│       ├── client/             # OuijaBoardCanvas, board layout math, ClientScreen
│       ├── server/             # ServerScreen (Controller console & scare dashboard)
│       ├── splash/             # Atmospheric safety disclaimer & splash sequence
│       └── theme/              # Gothic dark theme, typography, color palette
└── res/
    ├── raw/                    # Audio assets (screams, breath, creaks) & jump scare videos
    └── mipmap-*/               # Adaptive and legacy launcher icon sets
```

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio** (Ladybug / Jellyfish or newer recommended)
- **JDK 17**
- **Android SDK Platform API 35** (minimum device Android 8.0 / API 26)
- Two Android devices (or one physical device + one emulator) for testing client-controller interactions. *Note: Vibration and flashlight features require physical hardware.*

### Setup & Configuration

1. **Clone the repository:**
   ```bash
   git clone <repository-url>
   cd Ouija
   ```

2. **Configure Supabase Credentials:**
   Update your Supabase project URL and anonymous public key in `app/src/main/java/in/ouija/core/realtime/SupabaseConfig.kt`:
   ```kotlin
   object SupabaseConfig {
       const val SUPABASE_URL = "https://<your-project-id>.supabase.co"
       const val SUPABASE_ANON_KEY = "<your-anon-key>"
   }
   ```

3. **Build the project:**
   ```bash
   ./gradlew assembleDebug
   ```

---

## 🎮 How to Run the Prank

1. **Install the APK** on both the target phone and your controller phone.
2. **On the Target Phone (Client)**:
   - Launch the app and proceed past the splash screen.
   - The interactive Ouija board will load in full-screen mode.
   - Tap the **Crescent Moon** icon on the top-left of the board to open the secret menu.
   - Note the **5-letter Victim Code** (e.g. `KRZVM`) and close the dialog.
3. **On Your Phone (Controller)**:
   - Launch the app, open the secret menu via the Moon icon, and tap **CONTROLLER** (or launch `server_mode`).
   - Enter the target's 5-letter code and tap **CONNECT**.
   - Monitor live telemetry as they interact with the board.
   - Type answers to steer the planchette or trigger scare effects from the control panel.

---

## 🛡️ Safety & Responsible Use

> [!WARNING]
> This application is created strictly for harmless fun and entertainment between consenting friends.
> - **Never** use intense jump scares or flashing strobe effects on individuals with photosensitive epilepsy, cardiac conditions, or severe anxiety.
> - Always utilize the **STOP ALL** button if a participant feels uncomfortable.
> - Volume and haptic thresholds should be tested before initiating scares.

---

## 📄 License

This project is developed for personal and private entertainment purposes. Refer to the repository license terms for further details.
