# Play Store Release Checklist

## Release Config
- Set `RELEASE_VERSION_CODE` in `local.properties` to a value higher than the last uploaded Play build.
- Set `RELEASE_VERSION_NAME` in `local.properties` to the human-readable release version.
- Confirm release signing values are present in `local.properties`:
  - `SIGNING_STORE_FILE`
  - `SIGNING_STORE_PASSWORD`
  - `SIGNING_KEY_ALIAS`
  - `SIGNING_KEY_PASSWORD`
- Confirm `BACKEND_URL` points to production.

Example `local.properties` entries:

```properties
RELEASE_VERSION_CODE=2
RELEASE_VERSION_NAME=1.0.1
BACKEND_URL=https://kbook.iadv.cloud/
```

## Security And Backend
- Verify the backend is healthy for login, signup, forgot-password OTP, and sync flows.
- Rotate and verify the certificate pin in `app/src/main/res/xml/network_security_config.xml` before the current pin expiration date.
- Confirm Google sign-in client ID matches the Play signing setup.
- Review whether `android:largeHeap="true"` is still required before public scale.
- Review whether `androidx.security:security-crypto:1.1.0-alpha06` should be upgraded after compatibility testing.

## Permissions And Policy
- Prepare Play Console Data safety answers for:
  - account/login data
  - phone/WhatsApp-related identifiers entered by users
  - camera usage for OCR/menu scanning
  - Bluetooth usage for printer pairing
- Ensure runtime permission prompts are contextual:
  - camera only when scanning
  - Bluetooth only when configuring printers
- Prepare store listing explanations for camera and Bluetooth features.

## Functional Release Test
- Test login with phone/email.
- Test signup OTP flow.
- Test forgot-password OTP flow.
- Test menu configuration dialogs and editing flows.
- Test new bill creation online.
- Test new bill creation offline.
- Test sync after reconnect.
- Test OCR scan flow with camera permission grant and deny.
- Test customer printer pairing and test print.
- Test kitchen printer pairing and queue flush.
- Test reports, orders, and search screens.

## Build And Upload
- Run:

```powershell
.\gradlew.bat :app:compileReleaseKotlin
.\gradlew.bat :app:assembleRelease
```

- If using Play App Bundles, also run:

```powershell
.\gradlew.bat :app:bundleRelease
```

- Verify the generated artifact installs and launches on at least one physical device.
- Upload to internal testing first, then closed testing, then production.

## Post-Upload
- Monitor crash reports.
- Monitor login failure rate.
- Monitor OTP delivery success rate.
- Monitor sync failure rate.
