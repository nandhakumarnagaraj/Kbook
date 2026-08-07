# Flashlight Performance Monitoring Integration

## Overview

Flashlight (powered by Flipper) is now integrated into KhanaBook Android app for performance monitoring. It only runs in **debug builds** to avoid any performance overhead in production.

## What Flashlight Provides

1. **FPS Monitoring** - Real-time frame rate tracking
2. **Network Inspection** - View all API calls and responses
3. **Layout Inspector** - Inspect UI hierarchy
4. **Performance Profiling** - Identify bottlenecks

## Setup Instructions

### 1. Install Flashlight CLI

```bash
npm install -g @perf-profiler/profiler
```

Or with yarn:
```bash
yarn global add @perf-profiler/profiler
```

### 2. Install Flipper Desktop (Optional but Recommended)

Download from: https://fbflipper.com/

### 3. Run Your Debug Build

```bash
cd Android
./gradlew installDebug
```

### 4. Start Monitoring

#### Option A: Using Flashlight CLI
```bash
flashlight measure
```

#### Option B: Using Flipper Desktop
1. Open Flipper
2. Your app should appear automatically
3. Enable plugins: Network, Layout Inspector

## What Was Changed

### Files Modified
1. `app/build.gradle.kts` - Added Flipper dependencies (debug only)
2. `KhanaBookApplication.kt` - Added Flashlight initialization
3. `app/src/debug/java/.../FlashlightInitializer.kt` - Debug implementation
4. `app/src/release/java/.../FlashlightInitializer.kt` - Release stub (no-op)

### Dependencies Added
```kotlin
debugImplementation("com.facebook.flipper:flipper:0.182.0")
debugImplementation("com.facebook.soloader:soloader:0.10.5")
debugImplementation("com.facebook.flipper:flipper-network-plugin:0.182.0")
```

## Usage Examples

### Measure Screen Performance
```bash
flashlight measure --bundleId com.piquantservices.khanabooklite.debug
```

### Compare Two Builds
```bash
flashlight measure --bundleId com.piquantservices.khanabooklite.debug --iterationCount 10
```

### Monitor Specific Screen
```bash
flashlight measure --testCommand "adb shell input tap 500 1000" --duration 20000
```

## Key Metrics to Monitor

### Login Screen
- **Target FPS**: 60 FPS
- **Login API Time**: < 2000ms
- **Screen Render Time**: < 500ms

### Billing Screen
- **Target FPS**: 60 FPS
- **Cart Update Time**: < 100ms
- **Payment Processing**: < 3000ms

### Home Dashboard
- **Target FPS**: 60 FPS
- **Data Load Time**: < 1000ms
- **Animation Smoothness**: No dropped frames

## Troubleshooting

### Flipper Not Detecting App
1. Ensure you're on the same WiFi network
2. Check ADB connection: `adb devices`
3. Restart the app

### Performance Impact
- Flashlight only runs in **debug builds**
- Release builds have zero overhead
- Safe to keep integrated permanently

## Best Practices

1. **Before Every Release**: Run performance tests
2. **After UI Changes**: Check FPS during animations
3. **After API Changes**: Monitor network response times
4. **Weekly Reviews**: Track performance trends

## CI/CD Integration

Add to your build pipeline:
```yaml
- name: Performance Test
  run: |
    flashlight measure --bundleId com.piquantservices.khanabooklite.debug --iterationCount 5
    flashlight report
```

## Performance Benchmarks (v2 Branch)

| Screen | Target FPS | Current FPS | Status |
|--------|-----------|-------------|--------|
| Login | 60 | TBD | ⏳ |
| Home | 60 | TBD | ⏳ |
| Billing | 60 | TBD | ⏳ |
| Menu | 60 | TBD | ⏳ |

## Resources

- Flashlight Documentation: https://github.com/bamlab/flashlight
- Flipper Documentation: https://fbflipper.com/docs/features/
- Android Performance Guide: https://developer.android.com/topic/performance

## Team Notes

- ✅ Only active in debug builds
- ✅ No production impact
- ✅ Easy to use with CLI or Desktop app
- ✅ Integrates with CI/CD
- ⚠️ Requires ADB connection for measurements
- ⚠️ May require additional permissions on some devices
