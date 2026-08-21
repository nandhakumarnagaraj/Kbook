# Play Store Launch Checklist

## Trigger Conditions
- Preparing first Play Store release
- Updating store listing or metadata
- User asks about staged rollout strategy
- Setting up data safety declarations
- Responding to Play Console policy issues

---

## Store Listing Requirements

### App Details
```
Field               | Requirement
--------------------|------------------------------------------
App name            | Max 30 chars. "KhanaBook - Restaurant POS"
Short description   | Max 80 chars. Focus on key value prop.
Full description    | Max 4000 chars. Include keywords naturally.
Category            | Business / Food & Drink
Content rating      | Complete IARC questionnaire
```

### Screenshots (Mandatory)
```
Type          | Count  | Specs
--------------|--------|----------------------------------
Phone         | 4-8    | 16:9 or 9:16, min 320px, max 3840px
7" Tablet     | 4-8    | Landscape preferred
10" Tablet    | 4-8    | Landscape preferred
Feature graphic| 1     | 1024x500 PNG/JPG
App icon      | 1      | 512x512 PNG (32-bit, no alpha)
```

**KhanaBook Screenshot Plan:**
1. Billing screen with items added
2. Menu management grid
3. Daily sales report with chart
4. KOT print preview
5. Payment processing (UPI QR)
6. Offline mode indicator + sync status

### Store Listing Best Practices
- First 2 lines of description visible without expanding — hook users here
- Include localized descriptions (Hindi, Tamil, Telugu for KhanaBook)
- A/B test graphics using Play Console experiments
- Update screenshots with every major UI change

---

## Data Safety Declaration

```yaml
# KhanaBook Data Safety
data_collected:
  - category: "Personal info"
    types: ["Name", "Phone number"]
    purpose: ["App functionality", "Account management"]
    shared: false
    
  - category: "Financial info"
    types: ["Purchase history", "Payment info"]
    purpose: ["App functionality"]
    shared: false
    
  - category: "Device info"
    types: ["Device ID"]
    purpose: ["Analytics", "Crash reporting"]
    shared: true
    shared_with: "Firebase Crashlytics"

security_practices:
  encryption_in_transit: true
  deletion_mechanism: true  # User can request data deletion
  independent_review: false
```

**Actions Required:**
- [ ] Complete data safety form in Play Console
- [ ] Add privacy policy URL (hosted on khanabook.in/privacy)
- [ ] Add data deletion request mechanism
- [ ] Document all third-party SDKs and their data collection

---

## Content Rating (IARC)

Answer questionnaire honestly:
- Violence: None
- Sexual content: None
- Language: None
- Controlled substances: None
- User interaction: Limited (no public chat)
- Data sharing: Yes (analytics)
- Location: No

Expected rating: **Everyone (E)** / **PEGI 3**

---

## Staged Rollout Strategy

```
Phase  | %    | Duration | Gate Criteria
-------|------|----------|----------------------------------
1      | 1%   | 24-48h   | Crash-free >99.5%, no ANRs
2      | 5%   | 24h      | No critical bug reports
3      | 10%  | 48h      | Performance metrics stable
4      | 25%  | 48h      | User ratings ≥4.0
5      | 50%  | 24h      | Revenue metrics stable
6      | 100% | -        | Full rollout
```

**Halt Rollout If:**
- Crash-free rate drops below 99%
- ANR rate exceeds 0.5%
- Critical payment flow failure
- Data loss reported by any user
- Rating drops below 3.5 stars

**KhanaBook Priority Monitors:**
- Bill creation success rate
- Sync completion rate
- Payment transaction success
- Print job success rate

---

## Pre-Launch Report

Play Console runs automated tests on Firebase Test Lab:
- Checks for crashes on multiple devices
- Screenshots accessibility issues
- Identifies performance problems

**Before submitting:**
1. Fix all critical issues from pre-launch report
2. Address accessibility warnings (missing content descriptions)
3. Verify app works on Android 8.0+ (minSdk target)
4. Test on low-RAM devices (Go edition if applicable)

---

## Crash Monitoring Post-Launch

```kotlin
// Ensure Crashlytics is configured
// build.gradle.kts
plugins {
    id("com.google.firebase.crashlytics")
}

// Custom keys for debugging
FirebaseCrashlytics.getInstance().apply {
    setCustomKey("merchant_id", merchantId)
    setCustomKey("app_variant", BuildConfig.FLAVOR)
    setCustomKey("sync_status", syncRepo.lastSyncStatus())
}
```

**Dashboard Checks (Daily for first week):**
- [ ] Crash-free users >99.5%
- [ ] No new crash clusters
- [ ] ANR-free rate >99.5%
- [ ] Vitals: startup time, frame rate, permission denials

---

## Anti-patterns
- ❌ 100% rollout on day one
- ❌ Ignoring pre-launch report issues
- ❌ Incomplete data safety declaration (causes policy strikes)
- ❌ Generic screenshots that don't show real app value
- ❌ Not localizing store listing for target market
- ❌ Releasing without crash monitoring configured

## Verification Checklist
- [ ] All store listing fields completed
- [ ] Screenshots for all device types uploaded
- [ ] Data safety declaration matches actual SDK usage
- [ ] Content rating questionnaire completed
- [ ] Privacy policy URL valid and accessible
- [ ] Staged rollout configured (not 100%)
- [ ] Firebase Crashlytics verified in debug build
- [ ] Pre-launch report issues addressed
- [ ] App tested on 3+ physical devices
