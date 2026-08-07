# TalkBack Accessibility Test Checklist - KhanaBook POS

## Global Requirements (All Screens)

### Content Descriptions
- [ ] All images have descriptive contentDescription
- [ ] Icons without text have contentDescription
- [ ] Decorative elements have null or empty contentDescription
- [ ] Dynamic content updates are announced (LiveRegion)

### Focus Order
- [ ] Focus follows logical reading order (top-to-bottom, left-to-right)
- [ ] Tab navigation cycles through all interactive elements
- [ ] Modal dialogs trap focus correctly
- [ ] Back navigation returns focus to previous element

### Touch Targets
- [ ] All interactive elements ≥ 48×48dp
- [ ] Adequate spacing between touch targets (≥8dp)
- [ ] No overlapping touch targets

### Text
- [ ] All text is readable at 100%, 130%, 200% font scales
- [ ] Sufficient color contrast (WCAG AA: 4.5:1 for normal text, 3:1 for large text)
- [ ] Text can be resized without loss of functionality

### Announcements
- [ ] Loading states are announced
- [ ] Success/error messages are announced
- [ ] Navigation changes are announced
- [ ] Form validation errors are announced

---

## Screen-Specific Checklist

### [SplashScreen]
- [ ] Logo has contentDescription="App logo"
- [ ] Loading indicator announced as "Loading app"
- [ ] Transition to Login announced

### [LoginScreen]
- [ ] Phone input field: contentDescription="Phone number input"
- [ ] Password input field: contentDescription="Password input"
- [ ] Login button: contentDescription="Sign in"
- [ ] Sign up link: contentDescription="Create new account"
- [ ] Validation errors announced
- [ ] Password field masks characters correctly
- [ ] Show/hide password toggle announced

### [SignUpScreen]
- [ ] All input fields have proper labels
- [ ] Error messages linked to fields
- [ ] Submit button announced correctly
- [ ] Terms/privacy links accessible

### [MainScreen/Bottom Navigation]
- [ ] Home tab: contentDescription="Home tab"
- [ ] Orders tab: contentDescription="Orders tab"
- [ ] Reports tab: contentDescription="Reports tab"
- [ ] Search tab: contentDescription="Search tab"
- [ ] Settings tab: contentDescription="Settings tab"
- [ ] Selected state announced

### [HomeScreen]
- [ ] Dashboard title announced
- [ ] Quick action buttons have contentDescription
- [ ] New Bill button: contentDescription="Create new bill"
- [ ] Metrics cards announced with values
- [ ] Pull-to-refresh announced

### [NewBillScreen]
- [ ] Add Item button: contentDescription="Add item to cart"
- [ ] Category items announced with name and price
- [ ] Quantity controls: contentDescription="Increase quantity" / "Decrease quantity"
- [ ] Cart items announced with name, quantity, price
- [ ] Remove item announced
- [ ] Total amount announced
- [ ] Checkout button: contentDescription="Proceed to checkout"

### [CheckoutScreen]
- [ ] Payment method options announced with names
- [ ] Selected payment method announced
- [ ] Amount fields have labels
- [ ] Discount field announced
- [ ] Complete payment button: contentDescription="Complete payment"
- [ ] Success/failure announced

### [OrdersScreen]
- [ ] Orders list announced with count
- [ ] Order items announced with ID, status, total
- [ ] Filter button: contentDescription="Filter orders"
- [ ] Search field: contentDescription="Search orders"
- [ ] Empty state announced

### [OrderDetailScreen]
- [ ] Order ID announced
- [ ] Status announced
- [ ] Items list announced
- [ ] Total amount announced
- [ ] Print button: contentDescription="Print bill"
- [ ] Share button: contentDescription="Share order"
- [ ] Status update actions announced

### [ReportsScreen]
- [ ] Reports title announced
- [ ] Date range selector announced
- [ ] Chart data announced when focused
- [ ] Filter options announced
- [ ] Export button: contentDescription="Export report"

### [MenuConfigurationScreen]
- [ ] Mode selection announced
- [ ] Manual mode: contentDescription="Add items manually"
- [ ] Smart Import: contentDescription="Import from PDF or scan"
- [ ] PDF picker: contentDescription="Select PDF file"
- [ ] Camera button: contentDescription="Scan menu with camera"
- [ ] Import progress announced

### [SettingsScreen]
- [ ] Settings sections announced
- [ ] Each setting item announced
- [ ] Profile section announced with details
- [ ] Menu section announced
- [ ] Payment section announced
- [ ] Printer section announced
- [ ] Logout button: contentDescription="Sign out"
- [ ] Logout confirmation announced

---

## Common Patterns to Test

### Dialogs/Modals
- [ ] Focus trapped within dialog
- [ ] Escape/back closes dialog
- [ ] Confirm/Cancel actions announced
- [ ] Focus returns to trigger element on close

### Lists
- [ ] List item count announced
- [ ] Individual items announced with context
- [ ] Swipe actions announced
- [ ] Selection state announced

### Forms
- [ ] Field labels announced
- [ ] Required fields indicated
- [ ] Error messages linked to fields
- [ ] Submit button announced with form context

### Navigation
- [ ] Screen title announced on arrival
- [ ] Back navigation announced
- [ ] Tab changes announced
- [ ] Deep link destinations announced

---

## Accessibility Testing Commands

```bash
# Enable TalkBack
adb shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.accessibility.talkback.TalkBackService

# Check accessibility settings
adb shell settings get secure enabled_accessibility_services

# Get screen content with accessibility info
adb shell dumpsys accessibility

# Test with TalkBack on specific device
./gradlew connectedAndroidTest -PtestDevice="Pixel7"
```

---

## Known Accessibility Issues

| Issue ID | Screen | Description | Priority |
|----------|--------|-------------|----------|
| A11Y-001 | NewBillScreen | Quantity buttons too small (36dp) | High |
| A11Y-002 | MenuConfig | OCR progress not announced | Medium |
| A11Y-003 | Checkout | Payment method icons lack descriptions | High |
| A11Y-004 | OrdersScreen | Empty state lacks announcement | Low |

---

## Resources

- [Android Accessibility Guide](https://developer.android.com/guide/topics/ui/accessibility)
- [WCAG 2.1 Guidelines](https://www.w3.org/WAI/WCAG21/quickref/)
- [Material Design Accessibility](https://material.io/design/usability/accessibility.html)
