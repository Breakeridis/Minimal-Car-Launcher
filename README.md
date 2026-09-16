# Minimal Android Car Launcher (1080p + ZLink)

A lightweight, distraction-free, and high-contrast Android Home Launcher designed specifically for **1080p landscape built-in Android head units** with dedicated **ZLink (Android Auto / CarPlay)** integration, **customizable bottom dock**, and an **in-app GitHub updater**.

---

## Highlights & Features

- **Automotive Ergonomics**: High-contrast, dark OLED-friendly palette tailored for 1080p in-dash screens.
- **Dedicated ZLink / Android Auto Tile**: Auto-detects your head unit's ZLink installation (`com.zjinnova.zlink`, `com.xyauto.zlink`, etc.) and launches Android Auto in one tap.
- **Real-Time GPS Speedometer**: Displays vehicle speed with animated gauge arc and one-tap switching between **KM/H** and **MPH**.
- **Glanceable Digital Clock**: Large digital clock, seconds indicator, and localized day/date.
- **Customizable Bottom Dock**:
  - **Long-Press Any Icon**: Choose to **Remove** it or **Replace** it with another installed app.
  - Pinned apps are automatically saved across reboots and power cycles.
- **All Apps Drawer with Pinning**:
  - Open All Apps and **long-press any app** to quickly pin it to the bottom bar.
  - 6-column grid with instant search filtering.
- **Direct In-App GitHub Updater**:
  - Tap the update icon on the bottom dock to fetch new releases directly from this GitHub repository.
  - Downloads the latest APK with real-time progress and triggers the Android system installer.
- **Zero Local Installs Needed**: Automated GitHub Actions CI workflow compiles `app-debug.apk` and publishes releases in the cloud.

---

## How to Build the APK via GitHub Actions (Cloud Build)

Whenever you push to `main`, GitHub Actions automatically compiles the APK and creates a new GitHub Release:

1. Go to your repository on GitHub: `https://github.com/Breakeridis/Minimal-Car-Launcher`
2. Click on **"Releases"** (on the right sidebar) or the **"Actions"** tab.
3. Download `app-debug.apk` from the latest release.

---

## How to Install on Your Head Unit or Phone

1. **Copy to USB**:
   - Copy `app-debug.apk` onto a USB flash drive (or download it directly on your phone).
2. **Install**:
   - Open your head unit's **File Manager** (or phone Files app) and tap `app-debug.apk` to install.
3. **Set as Default Launcher (on Car)**:
   - Press the physical or on-screen **Home** button on your car unit.
   - Select **Car Launcher** and tap **"Always"**.
   - *(On a phone: tap "Just Once" to keep your standard phone launcher default).*
4. **Future Updates**:
   - Once installed, you can simply tap the **Update** icon on the bottom dock to fetch and install new versions directly!
