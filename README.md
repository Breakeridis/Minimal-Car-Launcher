# Minimal Android Car Launcher (1080p + ZLink)

A lightweight, distraction-free, and high-contrast Android Home Launcher designed specifically for **1080p landscape built-in Android head units** with dedicated **ZLink (Android Auto / CarPlay)** integration.

---

## Highlights & Features

- **Automotive Ergonomics**: High-contrast, dark OLED-friendly palette tailored for 1080p in-dash screens.
- **Dedicated ZLink / Android Auto Tile**: Auto-detects your head unit's ZLink installation (`com.zjinnova.zlink`, `com.xyauto.zlink`, etc.) and launches Android Auto in one tap.
- **Real-Time GPS Speedometer**: Displays vehicle speed with animated gauge arc and one-tap switching between **KM/H** and **MPH**.
- **Glanceable Digital Clock**: Large digital clock, seconds indicator, and localized day/date.
- **Quick-Access Dock & App Drawer**: Fast access to navigation, music, car settings, plus an overlay drawer listing all installed car apps (FM Radio, Equalizer, etc.) with search.
- **Zero Local Installs Needed**: Automated GitHub Actions CI workflow compiles `app-debug.apk` directly in the cloud.

---

## How to Build the APK via GitHub Actions (Cloud Build)

You do **not** need Android Studio or Java installed on your computer. Follow these steps:

### Step 1: Push This Code to GitHub
1. Create a new repository on [GitHub](https://github.com/new) (can be Public or Private), e.g., named `minimal-car-launcher`.
2. Open PowerShell or Command Prompt in this folder (`C:\My\car_launcher\gemini`):
   ```powershell
   git init
   git add .
   git commit -m "Initial commit: Minimal Car Launcher with ZLink & 1080p support"
   git branch -M main
   git remote add origin https://github.com/YOUR_USERNAME/minimal-car-launcher.git
   git push -u origin main
   ```

### Step 2: Download Your Compiled APK
1. Open your repository on GitHub in any web browser.
2. Click the **"Actions"** tab at the top.
3. You will see a workflow running named **"Compile & Package APK"**.
4. Once finished (usually takes 1.5 – 2 minutes, marked with a green checkmark):
   - Click on the workflow run.
   - Scroll down to the **"Artifacts"** section.
   - Click on **`minimal-car-launcher-debug`** to download the zip file.
   - Extract `app-debug.apk` from the zip!

---

## How to Install on Your Built-In Android Head Unit

1. **Copy to USB**:
   - Copy `app-debug.apk` onto any USB flash drive formatted in FAT32 or exFAT.
2. **Connect to Car**:
   - Plug the USB flash drive into your car unit's USB port.
3. **Install**:
   - Open your head unit's **File Manager** (or **ApkInstaller** app).
   - Locate `app-debug.apk` on the USB drive and tap **Install**.
4. **Set as Default Launcher**:
   - Press the physical or on-screen **Home** button on your car unit.
   - Android will prompt:
     > *"Select a Home app: [Factory Launcher] or [Car Launcher]"*
   - Select **Car Launcher** and tap **"Always"**.
5. **Grant GPS Permission**:
   - When opened for the first time, allow location access so the speedometer can read real-time GPS speed.

---

## Switching Back to Stock Launcher
If you ever want to return to the original manufacturer launcher:
1. Tap the **Settings** icon on the bottom right of the dock.
2. Go to **Apps & notifications** &rarr; **Default apps** &rarr; **Home app**.
3. Select your factory head unit launcher.

---

## Project Structure
- `.github/workflows/build.yml` — Automated cloud build script
- `app/src/main/AndroidManifest.xml` — Android manifest with `CATEGORY_HOME` and landscape lock
- `app/src/main/java/com/minimal/carlauncher/`
  - `ui/MainActivity.kt` — Entry point activity
  - `ui/dashboard/` — Dashboard widgets (Clock, Speedometer, ZLink Hero, Dock)
  - `ui/drawer/` — App drawer grid dialog
  - `service/SpeedometerManager.kt` — GPS speed tracker
  - `data/AppRepository.kt` — ZLink & installed apps manager
