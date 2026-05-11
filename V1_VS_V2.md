# KhanaBook V1 vs V2 Differences

## Overview
- **V1**: KhanaBook Lite (`com.piquantservices.khanabooklite`) - Current production app
- **V2**: KhanaBook (`com.piquantservices.khanabook`) - New version with rebranded name

## Key Differences

| Aspect | V1 (KhanaBook Lite) | V2 (KhanaBook) |
|--------|----------------------|------------------|
| **App Name** | KhanaBook Lite | KhanaBook |
| **Package Name** | `com.piquantservices.khanabooklite` | `com.piquantservices.khanabook` |
| **Namespace** | `com.khanabook.lite.pos` | `com.khanabook.pos` |
| **Theme** | `KhanaBookLiteTheme` | `KhanaBookTheme` |
| **Play Console Status** | Under review (v8, 1.0.2) - SQLCipher fix | Not uploaded yet |
| **Features** | All current features | Same as V1 (ready for new features) |

## Web Admin Dashboard - Marketplace Integration

The web admin dashboard has **marketplace integration** configured for:

1. **Easebuzz** - Payment gateway integration
   - Model: `legacyEasebuzzActive` (api.models.ts:25)
   - Managed via Super Admin panel

2. **Zomato** - Online food delivery integration
   - Fields: `zomatoEnabled`, `zomatoOutletId`, `zomatoApiKey`, `zomatoWebhookUrl`, `zomatoWebhookSecret`
   - Config Location: `web-admin/src/app/pages/marketplace-setup/`
   - API Models: `web-admin/src/app/core/models/api.models.ts` (lines 36-40, 47)

3. **Swiggy** - Online food delivery integration
   - Fields: `swiggyEnabled`, `swiggyStoreId`, `swiggyApiKey`, `swiggyWebhookUrl`, `swiggyWebhookSecret`
   - Config Location: Same as Zomato (marketplace-setup page)
   - API Models: Same as above

**Access**: Super Admin only - accessible via Web Admin Dashboard → Marketplace Setup page

**Files involved**:
- `web-admin/src/app/core/models/api.models.ts`
- `web-admin/src/app/pages/marketplace-setup/marketplace-setup-page.component.ts`
- `web-admin/src/app/core/services/admin-api.service.ts`
- `web-admin/src/app/app.routes.ts` (lines 61-62)

## V2 Setup Requirements

Before deploying V2:
1. Update Firebase Console to add new package `com.piquantservices.khanabook`
2. Download new `google-services.json` for V2
3. Replace `google-services.json` in `KhanaBook - Copy/Android/app/`
4. Update Play Console with new app entry for V2

## Current Status (May 6, 2026)

- **V1**: Under review in Play Console (fix for SQLCipher crash)
- **V2**: Ready for new feature development
