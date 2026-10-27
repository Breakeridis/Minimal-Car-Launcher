# Car Head Unit Hardware, Firmware & Software Reference

**Vehicle**: Mitsubishi Lancer (2007–2013) — Junsun Head Unit  
**Platform Manufacturer**: Nowada (NWD) / Allwinner  
**Captured From Device**: 2026-10-27

---

## 1. Hardware, Motherboard & CPU Specifications

| Property | Value |
| :--- | :--- |
| **Manufacturer / Brand** | `Allwinner` |
| **Model** | `K2401 / K2401P / k2401M` |
| **NWD Platform Name** | `K2401` / `2401P` (`K24`) |
| **Motherboard / Board** | `exdroid` (`ro.product.board = exdroid`) |
| **Product / Device Codename** | `ceres_b3` / `ceres-b3` (`ro.board.platform = ceres`) |
| **SoC / CPU Hardware** | **Allwinner QuadCore-A133** (`sun50iw10p1`) |
| **CPU Cores & Architecture** | 4× ARMv8 Processor rev 4 (`v8l`) running in 32-bit mode (`armeabi-v7a`, `armeabi`) |
| **Memory (RAM)** | Reported as `4.00 GB Total`, but `/proc/meminfo` shows **4× spoofed counters** (`Slab=835MB`, `PageTables=601MB`, `KernelStack=262MB`, `SwapTotal=3071MB`) while real `MemAvailable=408MB` (`MemFree=52MB + Cached=345MB + Buffers=11MB`) indicates **1 GB (or 2 GB) true physical RAM** |
| **Screen Resolution & Density** | `1280×720` (`160 dpi`, `1.0x` scale, landscape `ro.boot.nwd.orientation = 90`) |
| **GPS Hardware** | `zkwAT6558R` (`persist.sys.nwd.gps-hw.version`) |
| **Bluetooth / Wi-Fi Chip** | `8800_BT5.4_WIFI6/2025:07:07:14:30:10` (`System.nwd_bt_version`) |
| **Camera Controller** | `pr2000` (`persist.sys.nwd.hacamera.ctrl`) |

---

## 2. OS, Kernel & MCU Firmware

| Property | Value |
| :--- | :--- |
| **Real Android SDK / Base** | **Android 10 (API 29)** (`QP1A.191105.004`, `A133-plus-q-v1.0`) |
| **Reported Android Version** | `Android 14` (`Build.VERSION.RELEASE` spoofed by vendor) |
| **Build Display ID** | `K2401_NWD_S212109.20260312.153253` |
| **NWD Customer ID** | `K2401_NWD_S212802` (`ro.product.nwd.customer`) |
| **Build Fingerprint** | `Allwinner/ceres_b3/ceres-b3:10/QP1A.191105.004/eng.xuxian.20260203.132731:user/test-keys` |
| **Linux Kernel Version** | `Linux version 4.9.170 (xuxiang@NWD-SERVER-N254) (gcc version 5.3.1 20160412 (Linaro GCC 5.3-2016.05)) #1 SMP PREEMPT Tue Feb 3 12:02:20 CST 2026` |
| **MCU Firmware Version** | `V3.6-FF01-20250423-A02-CAN0000-00-00-DVD00000000-00` |
| **NWD Hardware Version** | `40` (`System.nwd_hardware_version`) |
| **NWD Factory Feature** | `21000` (`persist.sys.nwd.factory.feature`) |

---

## 3. Radio Architecture (`com.nwd.radio` + `com.nwd.radio.service`)

The head unit separates the FM/AM radio into a **frontend UI app** and a **background Carkit service**:

### A. Radio Frontend UI App
- **App Label**: `Radio`
- **Package Name**: `com.nwd.radio`
- **APK Path**: `/system/app/com.nwd.radio/com.nwd.radio.apk`
- **Version**: `1.1.0.3` (`versionCode = 1103`)
- **Activities**:
  - `com.nwd.radio.home_horizontalActivity`
- **Receivers / Services**: None (pure UI client)

### B. Radio Background Tuner Service
- **App Label**: `FM_Service`
- **Package Name**: `com.nwd.radio.service`
- **APK Path**: `/system/app/8925_Carkit_RadioService_VP2.2.9/8925_Carkit_RadioService_VP2.2.9.apk`
- **Version**: `2.2.9` (`versionCode = 229`)
- **Services**:
  - `com.nwd.radio.service.RadioService`
- **Manifest Intent Actions**:
  - `com.nwd.radio.service.ACTION_RADIO_SERVICE`

### C. Verified `com.nwd.radio.service.RadioFeature` AIDL Interface
Binding to `Intent("com.nwd.radio.service.ACTION_RADIO_SERVICE").setPackage("com.nwd.radio.service")` returns an `IBinder` with descriptor `com.nwd.radio.service.RadioFeature` (`com.nwd.radio.service.RadioFeature$Stub$Proxy`):
- `setCurrentFrequency(int freq, byte band, int index): void` (`TRANSACTION = 1`, where FM `freq` is `8750..10800` in 10 kHz steps)
- `getCurrentFrequency(): Frequency` (`TRANSACTION = 2`)
- `seek(boolean isUp): void` (`TRANSACTION = 3`)
- `search(boolean isUp): void` (`TRANSACTION = 4`)
- `changeBand(): void` (`TRANSACTION = 5`)
- `AMS(): void` (`TRANSACTION = 6`)
- `INTRO(): void` (`TRANSACTION = 7`)
- `setRadioBackServiceOn(boolean): void` (`TRANSACTION = 13`)
- `isRadioBackServiceOn(): boolean` (`TRANSACTION = 14`)
- `saveCurrentFrequency(byte index): void` (`TRANSACTION = 20`)
- `getPrefabFrequency(): Frequency[]` (`TRANSACTION = 21`)
- `getRadioPoint(): RadioPoint[]` (`TRANSACTION = 22`)
- `getRadioState(): byte` (`TRANSACTION = 23`)
- `registCallback(RadioCallback): void` (`TRANSACTION = 24`)
- `unRegistCallback(RadioCallback): void` (`TRANSACTION = 25`)
- `prefeb(boolean): void` (`TRANSACTION = 26`)
- `sendRadioCommand(byte, byte): void` (`TRANSACTION = 27`)
- `getRtMessage(): String` (`TRANSACTION = 28`)
- `getRadioType(): int` (`TRANSACTION = 29`)
- `getCurrentScanState(): int` (`TRANSACTION = 30`)

### D. Verified `com.nwd.radio.service.RadioConstant` Broadcasts & Keys
- `ACTION_RADIO_SERVICE = "com.nwd.radio.service.ACTION_RADIO_SERVICE"`
- `ACTION_RADIO_STATE = "com.nwd.action.ACTION_RADIO_STATE"`
- `ACTION_REQUEST_RADIO_FREQUENCE = "com.nwd.action.radiowidget_request_frequence"`
- `ACTION_SEND_RADIO_FREQUENCE = "com.nwd.action.ACTION_SEND_RADIO_FREQUENCE"`
- `ACTION_SEND_RADIO_FREQUENCE_NEW = "com.nwd.action.ACTION_SEND_RADIO_FREQUENCE_NEW"`
- `ACTION_SEND_SCAN_RADIO_FREQUENCE = "com.nwd.action.ACTION_SEND_SCAN_RADIO_FREQUENCE"`
- `ACTION_SET_RADIO_FREQUENCE = "com.nwd.action.ACTION_SET_RADIO_FREQUENCE"`
- Extras:
  - `EXTRA_BAND = "extra_band"`
  - `EXTRA_FREQUENCE = "extra_frequence"` (`8750..10800` for FM, `531..1620` for AM)
  - `EXTRA_RADIO_FREQUENCE = "extra_radio_frequence"`
  - `EXTRA_RADIO_PRESTORE_NUM = "extra_radio_prestore_num"`
  - `EXTRA_RADIO_STATE = "nwd_radio_state"`
- Keys:
  - `KEY_RADIO_CURRENT_BAND = "nwd_radio_current_band"`
  - `KEY_RADIO_CURRENT_FREQ = "nwd_radio_current_freq"`
  - `KEY_RADIO_CURRENT_FREQ_INDEX = "nwd_radio_current_freq_index"`
  - `KEY_RADIO_CURRENT_PS_DATA = "nwd_radio_current_ps_data"`
  - `KEY_RADIO_FM1_PRESTORE_DATA = "nwd_radio_fm1_prestore_data"`
  - `KEY_RADIO_FM2_PRESTORE_DATA = "nwd_radio_fm2_prestore_data"`
  - `KEY_RADIO_FM3_PRESTORE_DATA = "nwd_radio_fm3_prestore_data"`

### E. Related Radio System Properties & Settings
- `sys.nwd.audio.channel = FM`
- `sys.nwd.cur.music.stream.proc = com.nwd.radio.service`
- `persist.radio.i2s.use = true`
- `persist.sys.nwd.radio.rate = 16000`
- `persist.sys.nwd.radio.source = 1998`
- `persist.sys.nwd.radio.stream = 3`
- `System.nwd_arm_radio_module = 12`
- `System.nwd_init_radio_area = 1`
- `System.nwd_radio_state = 1`
- `System.mcu_current_source = 4`
- `System.mcu_radio_area_current = 0`
- `System.mcu_radio_area_feature = 30719`
- `System.key_arm_fm_audio_mode = 1`
- `System.key_arm_fm_scan_state = 0`
- `System.fm_transmit = 9300`

---

## 4. Core NWD Carkit & Vendor System Packages

| App Label | Package Name | APK Path |
| :--- | :--- | :--- |
| **Radio** | `com.nwd.radio` | `/system/app/com.nwd.radio/com.nwd.radio.apk` |
| **FM_Service** | `com.nwd.radio.service` | `/system/app/8925_Carkit_RadioService_VP2.2.9/8925_Carkit_RadioService_VP2.2.9.apk` |
| **KernelService** | `com.nwd.kernel` | `/system/app/8925_Carkit_KernelService_VP2.4.2/8925_Carkit_KernelService_VP2.4.2.apk` |
| **CarkitSettingService** | `com.nwd.setting.service` | `/system/app/8925_Carkit_SettingService_VP4.4.9/8925_Carkit_SettingService_VP4.4.9.apk` |
| **Car Info (CANBus)** | `com.nwd.can.setting` | `/data/nwdappconfig/app/.app/CanAllInOne.apk` |
| **Stock Launcher** | `com.android.launcher` | `/system/app/com.launcher.apk` |
| **EQ / DSP Adjust** | `com.nwd.audioset` | `/system/app/DspAdjust1_VP1.9.6/DspAdjust1_VP1.9.6.apk` |
| **BT Music** | `com.nwd.bt.music` | `/system/app/com.nwd.bt.music/com.nwd.bt.music.apk` |
| **BT Phone** | `com.nwd.android.phone` | `/system/priv-app/com.nwd.android.phone/com.nwd.android.phone.apk` |
| **Music** | `com.nwd.android.music.ui` | `/system/app/com.nwd.android.music.ui/com.nwd.android.music.ui.apk` |
| **Video** | `com.nwd.android.video.ui` | `/system/app/com.nwd.android.video.ui/com.nwd.android.video.ui.apk` |
| **AUX** | `com.nwd.auxin` | `/system/app/com.nwd.auxin/com.nwd.auxin.apk` |
| **Backcar Service** | `com.nwd.backcar` | `/system/app/A20CarkitBackcarService_VP1.9.3/A20CarkitBackcarService_VP1.9.3.apk` |
| **CarSetting** | `com.android.car.setting` | `/system/app/com.android.car.setting/com.android.car.setting.apk` |
| **Factory_Setting** | `com.nwd.factory.setting` | `/system/app/New_Factory_Setting_VP3.8.0/New_Factory_Setting_VP3.8.0.apk` |
| **ZLINK5 (CarPlay/AA)** | `com.zjinnova.zlink` | `/data/app/com.zjinnova.zlink-Uopws0cYjfguVA6hz3WZ7Q==/base.apk` |
| **MAXOBD** | `com.jiawei.maxobd` | `/data/app/com.jiawei.maxobd-_kCepzmgRQUJHwpzynF5xQ==/base.apk` |
| **Fan / CPU Monitor** | `com.nwd.cpumonitor` | `/data/app/com.nwd.cpumonitor-gdsR6Tux7AvcnHGa4YSHmA==/base.apk` |

---

## 5. Detected `Settings.System` MCU & NWD Keys

```ini
System.fm_transmit = 9300
System.hicar_tts_state = 0
System.is_allow_mcu_switch_video = 0
System.key_arm_fm_audio_mode = 1
System.key_arm_fm_scan_state = 0
System.key_nwd_daymodergb = #ff31456b
System.key_nwd_nightmodergb = #fffffffe
System.mcu_System_setting_state = 32832
System.mcu_back_light_day = 100
System.mcu_back_light_max = 100
System.mcu_back_light_night = 6
System.mcu_back_light_state = 1
System.mcu_backcar_state = 0
System.mcu_balance = 7
System.mcu_bass = 7
System.mcu_current_controller = 1
System.mcu_current_source = 4
System.mcu_current_touch = 1
System.mcu_dsp_center = 0
System.mcu_dsp_definition = 0
System.mcu_dsp_focus = 0
System.mcu_dsp_space = 0
System.mcu_dsp_speaker = 0
System.mcu_dsp_srs = 0
System.mcu_dsp_trubass = 0
System.mcu_eq = 1
System.mcu_fader = 7
System.mcu_hand_break = 0
System.mcu_heavy_bass = 0
System.mcu_max_balance_fader = 14
System.mcu_max_treble_tenor_bass_volume = 14
System.mcu_max_volume = 40
System.mcu_mute_state = 0
System.mcu_no_source_device_state = 0
System.mcu_other_setting_default_volume = 10
System.mcu_other_setting_gps_setting_type = 1
System.mcu_other_setting_volume_mix = 5
System.mcu_radio_area_current = 0
System.mcu_radio_area_feature = 30719
System.mcu_smart_fan_switch = 0
System.mcu_software_mode = 0
System.mcu_state = 1
System.mcu_state_equal = 0
System.mcu_steering_wheel_state = 0
System.mcu_system_volume = 7
System.mcu_tenor = 7
System.mcu_treble = 7
System.mcu_version = V3.6-FF01-20250423-A02-CAN0000-00-00-DVD00000000-00
System.mcu_video_state = 0
System.nwd_360_none = 1
System.nwd_allapps_addtoworkspace = false
System.nwd_arm_radio_module = 12
System.nwd_arm_volume_type = 0
System.nwd_bt_version = 8800_BT5.4_WIFI6/2025:07:07:14:30:10
System.nwd_cvbs_out_type = 0
System.nwd_enable_android_key = 0
System.nwd_factory_init = 1
System.nwd_gsensor_feature_enable = 0
System.nwd_hardware_version = 40
System.nwd_have_ipod = 0
System.nwd_hw_overlay_enable = 0
System.nwd_init_default = 1
System.nwd_init_radio_area = 1
System.nwd_launcherdraging = 0
System.nwd_logo_name = boot_logo.bmp
System.nwd_panel_key = 77,67,65,29,17,85,84,42,41,2E,16,34,33,18,81,82,3D,77,67,65,29,17,85,84,42,41,2E,16,34,33,18,81,82,3D
System.nwd_panel_study = 0
System.nwd_radio_state = 1
System.nwd_restore_language = 1
System.nwd_vodka_clean_launcher_date = 0
System.remember_mcu_version = V3.6-FF01-20250423-A02-CAN0000-00-00-DVD00000000-00
```
