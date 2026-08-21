# Release Process

## Trigger Conditions
- Ready to cut a new release version
- User asks about version bumps, signing, or tagging
- Preparing AAB for Play Console upload
- Uploading ProGuard mappings or symbols
- Post-release git housekeeping

---

## Version Bump Strategy

### Semantic Versioning for KhanaBook
```
Format: MAJOR.MINOR.PATCH (versionName)
        Integer incrementing  (versionCode)

Examples:
  1.0.0 (1)  → Initial release
  1.1.0 (2)  → New feature: table management
  1.1.1 (3)  → Bugfix: sync race condition
  2.0.0 (4)  → Breaking: new DB schema, force upgrade
```

### Version Bump in build.gradle.kts
```kotlin
// app/build.gradle.kts
android {
    defaultConfig {
        versionCode = 15  // Always increment by 1
        versionName = "1.4.2"
    }
}
```

**Automated version bump script:**
```bash
#!/bin/bash
# scripts/bump-version.sh
CURRENT_CODE=$(grep "versionCode" app/build.gradle.kts | grep -o '[0-9]*')
NEW_CODE=$((CURRENT_CODE + 1))
sed -i "s/versionCode = $CURRENT_CODE/versionCode = $NEW_CODE/" app/build.gradle.kts
echo "Bumped versionCode to $NEW_CODE"
```

---

## Changelog Generation

```bash
# Generate changelog from git commits since last tag
LAST_TAG=$(git describe --tags --abbrev=0)
echo "## v$(date +%Y.%m.%d)" > CHANGELOG_ENTRY.md
echo "" >> CHANGELOG_ENTRY.md
git log $LAST_TAG..HEAD --pretty=format:"- %s" --no-merges >> CHANGELOG_ENTRY.md
```

**Changelog format (CHANGELOG.md):**
```markdown
## v1.4.2 (2026-08-21)
### Fixed
- Bill sync failing when items exceed 50 count
- KOT printer disconnection on Android 14

### Added
- Table-wise bill summary in daily report

### Changed
- Improved offline indicator visibility
```

---

## AAB Signing

### Keystore Setup (One-time)
```bash
keytool -genkey -v -keystore khanabook-upload.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias khanabook-upload
```

### Signing Configuration
```kotlin
// app/build.gradle.kts
android {
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "../keystore/khanabook-upload.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: "khanabook-upload"
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
```

### Build Release AAB
```bash
# Clean build
./gradlew clean bundleRelease

# Output: app/build/outputs/bundle/release/app-release.aab
# Verify signing:
jarsigner -verify -verbose app/build/outputs/bundle/release/app-release.aab
```

---

## ProGuard Mapping Upload

```bash
# Upload mapping file to Firebase Crashlytics
# Automatically handled by Gradle plugin if configured:
plugins {
    id("com.google.firebase.crashlytics")
}

android {
    buildTypes {
        release {
            // This enables automatic upload
            firebaseCrashlytics {
                mappingFileUploadEnabled = true
            }
        }
    }
}

# Manual upload if needed:
firebase crashlytics:mappingFile:upload \
  --app=1:123456:android:abc123 \
  --mapping-file=app/build/outputs/mapping/release/mapping.txt
```

---

## Firebase Crashlytics Setup

```kotlin
// Ensure non-debug builds report crashes
android {
    buildTypes {
        debug {
            firebaseCrashlytics { mappingFileUploadEnabled = false }
        }
        release {
            firebaseCrashlytics { mappingFileUploadEnabled = true }
        }
    }
}

// In Application class
class KhanaBookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseCrashlytics.getInstance().apply {
            setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        }
    }
}
```

---

## Git Tagging & Release

```bash
# Full release workflow
VERSION="1.4.2"
CODE=15

# 1. Ensure clean working tree
git status  # Must be clean

# 2. Create annotated tag
git tag -a "v$VERSION" -m "Release v$VERSION (code $CODE)

Changes:
- Fix bill sync for large orders
- Add table summary to daily report
- Improve offline indicator"

# 3. Push tag
git push origin "v$VERSION"

# 4. Create GitHub release (optional)
gh release create "v$VERSION" \
  --title "v$VERSION" \
  --notes-file CHANGELOG_ENTRY.md \
  app/build/outputs/bundle/release/app-release.aab
```

---

## Complete Release Workflow (Step by Step)

```bash
# 1. Ensure on main, up to date
git checkout main && git pull

# 2. Bump version
# Edit build.gradle.kts: versionCode + versionName

# 3. Update CHANGELOG.md
# Add new version entry

# 4. Commit version bump
git add app/build.gradle.kts CHANGELOG.md
git commit -m "chore: bump version to v1.4.2 (15)"

# 5. Build release
./gradlew clean bundleRelease

# 6. Verify APK/AAB
bundletool build-apks --bundle=app-release.aab --output=test.apks --mode=universal
# Install and smoke test on device

# 7. Tag and push
git tag -a v1.4.2 -m "Release v1.4.2"
git push origin main --tags

# 8. Upload to Play Console
# Manual: Play Console → Release → Production → Create new release
# Or via CI: fastlane supply --aab app-release.aab --track production
```

---

## Anti-patterns
- ❌ Forgetting to increment versionCode (Play Console rejects)
- ❌ Committing keystore passwords to git
- ❌ Skipping ProGuard mapping upload (unreadable crash logs)
- ❌ Tagging before verifying the build works
- ❌ Using debug signing for release builds
- ❌ Not testing the release AAB on a real device before upload

## Verification Checklist
- [ ] versionCode incremented from previous release
- [ ] versionName follows semver
- [ ] CHANGELOG.md updated with user-facing changes
- [ ] Release AAB builds without errors
- [ ] AAB is signed with upload keystore
- [ ] ProGuard mapping uploaded to Crashlytics
- [ ] Git tag created and pushed
- [ ] Smoke test passed on release build (real device)
- [ ] Play Console upload successful
