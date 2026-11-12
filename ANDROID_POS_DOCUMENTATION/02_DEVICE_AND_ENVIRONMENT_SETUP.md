# 02 Device & Environment Setup

## 1. Verified Target Device Profile

| Property | Value | Notes |
| :--- | :--- | :--- |
| **Model** | Motorola moto g34 5G | Physical hardware device |
| **ADB Serial** | `ZA222J5KT7` | USB / Wireless interface |
| **Display Resolution** | `720 x 1600 px` | 20:9 Aspect Ratio |
| **Android Version** | Android 14 (API 34) | Target OS runtime |
| **Application Package** | `com.piquantservices.khanabooklite.debug` | Development debug build |
| **Launcher Activity** | `com.khanabook.lite.pos.MainActivity` | Single activity Jetpack Compose host |

---

## 2. ADB Diagnostic Toolchain

All live UI validation is driven through standard ADB utilities:

### Screen Hierarchy & Semantics
```powershell
# Dump UI hierarchy to on-device storage
adb shell uiautomator dump /sdcard/window_dump.xml

# Extract interactive nodes with text and bounds
$xml = [xml](adb shell cat /sdcard/window_dump.xml); $xml.SelectNodes('//*[@text!=""]') | Select-Object text, bounds
```

### Visual Capture
```powershell
# Stream PNG screencap directly to artifacts directory
adb exec-out screencap -p > "artifacts/android-ui-tests/<step_name>.png"
```

### Touch & Keyboard Automation
```powershell
# Tap element center coordinates
adb shell input tap <X> <Y>

# Enter text into focused inputs
adb shell input text "<value>"

# Back navigation gesture
adb shell input keyevent 4
```

---

## 3. Disconnection & Reconnection Troubleshooting
If `adb devices` returns an empty device list:
1. Ensure the USB cable is firmly seated and the phone display is powered on.
2. Verify that **USB Debugging** remains toggled ON in Developer Options.
3. Check Windows Device Manager (`Get-PnpDevice | Where-Object { $_.InstanceId -like "*ZA222J5KT7*" }`) to confirm the composite driver is present.
4. Restart ADB daemon if needed: `adb kill-server; adb start-server; adb devices`.
