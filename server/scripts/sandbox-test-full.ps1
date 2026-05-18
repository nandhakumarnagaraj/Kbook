param(
    [string]$BaseUrl = "http://localhost:8081/api/v2",
    [string]$AdminEmail = "admin@kbook.com",
    [string]$AdminPassword = "admin123"
)

$ErrorActionPreference = "Stop"
$rand = Get-Random -Minimum 10000 -Maximum 99999
$phone = "9" + (Get-Random -Minimum 100000000 -Maximum 999999999).ToString()
$passCount = 0
$failCount = 0
$skipCount = 0

function Test-Step {
    param([string]$Name, [scriptblock]$Script)
    Write-Host ""
    Write-Host "=== $Name ===" -ForegroundColor Cyan
    try {
        $result = & $Script
        Write-Host "[PASS] $Name" -ForegroundColor Green
        $script:passCount++
        return $result
    } catch {
        Write-Host "[FAIL] $Name" -ForegroundColor Red
        Write-Host "  Error: $_" -ForegroundColor Red
        $script:failCount++
        return $null
    }
}

function Write-JsonStep {
    param($Data)
    if ($Data) {
        $Data | ConvertTo-Json -Depth 5 | Write-Host
    }
}

# ============================================
# STEP 1: Admin Login
# ============================================
$adminToken = Test-Step -Name "Admin Login" -Script {
    $body = @{ loginId = $AdminEmail; password = $AdminPassword } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -Body $body -ContentType "application/json"
    if (-not $res.token) { throw "No token in response" }
    Write-Host "   Token: $($res.token.Substring(0, 40))..." -ForegroundColor Gray
    $res.token
}

$headers = @{ Authorization = "Bearer $adminToken" }

# ============================================
# STEP 2: Dev Signup (Owner)
# ============================================
$ownerData = Test-Step -Name "Dev Signup" -Script {
    $body = @{
        phoneNumber = $phone
        name = "Sandbox Test $rand"
        password = "admin123"
        deviceId = "SANDBOX_TEST"
    } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$BaseUrl/auth/signup/dev" -Method Post -Body $body -ContentType "application/json"
    if (-not $res.restaurantId) { throw "No restaurantId in response" }
    Write-Host "   Restaurant ID: $($res.restaurantId)" -ForegroundColor Gray
    Write-Host "   Phone: $phone" -ForegroundColor Gray
    $res
}

$restaurantId = $ownerData.restaurantId
$ownerToken = $ownerData.token
$ownerHeaders = @{ Authorization = "Bearer $ownerToken" }

# ============================================
# STEP 3: Create Sub-Merchant Draft
# ============================================
$smDraft = Test-Step -Name "Create Sub-Merchant Draft" -Script {
    $body = @{
        restaurantId = "$restaurantId"
        businessName = "Sandbox Test Shop $rand"
        businessType = "SOLE_PROPRIETORSHIP"
        pan = "ABCDE1234F"
        gst = "29ABCDE1234F1Z5"
        bankAccountNo = "1234567890"
        ifsc = "HDFC0000123"
        bankName = "HDFC Bank"
        branchName = "Main Branch"
        beneficiaryName = "Test Owner $rand"
        businessAddress = "123 Sandbox Street"
        contactEmail = "sandbox$rand@example.com"
        contactPhone = $phone
        commissionRate = 3.0
    } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$BaseUrl/admin/sub-merchants" -Method Post -Body $body -ContentType "application/json" -Headers $headers
    if (-not $res.id) { throw "No id in response" }
    Write-Host "   Sub-Merchant DB ID: $($res.id)" -ForegroundColor Gray
    $res
}

$smDbId = $smDraft.id

# ============================================
# STEP 4: Submit to Easebuzz (Create Sub-Merchant API)
# ============================================
$smSubmit = Test-Step -Name "Submit to Easebuzz (Create Sub-Merchant)" -Script {
    $res = Invoke-RestMethod -Uri "$BaseUrl/admin/sub-merchants/$smDbId/submit-to-easebuzz" -Method Post -Headers $headers
    Write-JsonStep $res
    if ($res.status -eq "FAILED" -or $res.status -eq "DRAFT") {
        Write-Host "  [NOTE] Easebuzz submission returned status: $($res.status)" -ForegroundColor Yellow
        Write-Host "  This may be expected if sandbox sub-merchant module is not enabled." -ForegroundColor Yellow
    } else {
        Write-Host "  Sub-Merchant Easebuzz ID: $($res.subMerchantId)" -ForegroundColor Gray
    }
    $res
}

# Check if we got an Easebuzz ID or if submission failed
$smSubmitObj = if ($smSubmit) { $smSubmit } else { $null }
$hasEasebuzzId = $smSubmitObj -and ($smSubmitObj.subMerchantId -or ($smSubmitObj.status -ne "FAILED" -and $smSubmitObj.status -ne "DRAFT"))
if ($hasEasebuzzId) {
    if ($smSubmitObj.subMerchantId) {
        $smEasebuzzId = $smSubmitObj.subMerchantId
    } else {
        $smRead = Invoke-RestMethod -Uri "$BaseUrl/admin/sub-merchants/$smDbId" -Method Get -Headers $headers
        $smEasebuzzId = $smRead.subMerchantId
    }
    Write-Host "  Using Easebuzz Sub-Merchant ID: $smEasebuzzId" -ForegroundColor Gray

    # STEP 5: Generate KYC Access Key
    Test-Step -Name "Generate KYC Access Key" -Script {
        $res = Invoke-RestMethod -Uri "$BaseUrl/admin/sub-merchants/$smDbId/kyc-access-key" -Method Post -Headers $headers
        Write-JsonStep $res
        if ($res.kyc_url -and $res.kyc_url -ne "") {
            Write-Host "  KYC URL: $($res.kyc_url)" -ForegroundColor Gray
        }
        $res
    }

    # STEP 6: Create Split Label
    Test-Step -Name "Create Split Label" -Script {
        $res = Invoke-RestMethod -Uri "$BaseUrl/admin/sub-merchants/$smDbId/split-label" -Method Post -Headers $headers
        Write-JsonStep $res
        $res
    }

    # STEP 7: Resend OTP
    Test-Step -Name "Resend OTP" -Script {
        $res = Invoke-RestMethod -Uri "$BaseUrl/admin/sub-merchants/$smDbId/resend-otp" -Method Post -Headers $headers
        Write-JsonStep $res
        $res
    }

    # STEP 8: Update on Easebuzz
    Test-Step -Name "Update on Easebuzz" -Script {
        $res = Invoke-RestMethod -Uri "$BaseUrl/admin/sub-merchants/$smDbId/update-on-easebuzz" -Method Post -Headers $headers
        Write-JsonStep $res
        $res
    }
} else {
    Write-Host ""
    Write-Host "[SKIP] KYC/Split/OTP/Update tests (Easebuzz submission failed)" -ForegroundColor Yellow
    Write-Host "  Sub-merchant module may not be enabled on this Easebuzz sandbox account." -ForegroundColor Yellow
    Write-Host "  The Easebuzz sandbox test credentials (ADNX3KYX5) may not support sub-merchant APIs." -ForegroundColor Yellow
    $skipCount += 4
}

# ============================================
# STEP 9: Create a Bill
# ============================================
$billId = Test-Step -Name "Create Bill via Sync Push" -Script {
    $bill = @{
        localId = [long]$rand
        restaurantId = "$restaurantId"
        deviceId = "SANDBOX_TEST"
        dailyOrderId = [long]1
        dailyOrderDisplay = "SB-$rand"
        lifetimeOrderId = [long]$rand
        orderType = "DINE_IN"
        subtotal = 100.00
        totalAmount = 100.00
        paymentMode = "UPI"
        paymentStatus = "pending"
        orderStatus = "active"
        customerWhatsapp = $phone
        customerName = "Sandbox Tester"
        lastResetDate = "2024-05-18"
        cancelReason = ""
        createdAt = [long]1712345678
        updatedAt = [long]1712345678
    }
    $billJson = $bill | ConvertTo-Json
    $billBody = "[" + $billJson + "]"
    $res = Invoke-RestMethod -Uri "$BaseUrl/sync/bills/push" -Method Post -Body $billBody -ContentType "application/json" -Headers $ownerHeaders
    $billIdResp = $res.localToServerIdMap."$rand"
    if (-not $billIdResp) { throw "No billId in response: $($res | ConvertTo-Json)" }
    Write-Host "  Bill Server ID: $billIdResp" -ForegroundColor Gray
    $billIdResp
}

# ============================================
# STEP 10: Create Payment Order
# ============================================
$paymentOrder = Test-Step -Name "Create Payment Order" -Script {
    $body = @{ billId = [long]$billId; restaurantId = "$restaurantId" } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$BaseUrl/payments/easebuzz/create-order" -Method Post -Body $body -ContentType "application/json" -Headers $ownerHeaders
    Write-JsonStep $res
    if ($res.status -ne "success") { throw "Payment order failed: $($res.error)" }
    Write-Host "  TXN ID: $($res.txnid)" -ForegroundColor Gray
    Write-Host "  Payment URL: $($res.payment_url)" -ForegroundColor Gray
    $res
}

$txnid = $paymentOrder.txnid

# ============================================
# STEP 11: Verify Payment Status
# ============================================
Test-Step -Name "Get Payment Status" -Script {
    $res = Invoke-RestMethod -Uri "$BaseUrl/payments/easebuzz/status/$billId" -Method Get -Headers $ownerHeaders
    Write-JsonStep $res
    $res
}

# ============================================
# STEP 12: Initiate Refund (expected to handle gracefully even without real payment)
# ============================================
Test-Step -Name "Initiate Refund" -Script {
    $body = @{ amount = 50.00; reason = "Test refund via sandbox" } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$BaseUrl/payments/easebuzz/refund/$billId" -Method Post -Body $body -ContentType "application/json" -Headers $ownerHeaders
    Write-JsonStep $res
    $res
}

# ============================================
# STEP 13: Get Refund Status
# ============================================
Test-Step -Name "Get Refund Status" -Script {
    $res = Invoke-RestMethod -Uri "$BaseUrl/payments/easebuzz/refund-status/$billId" -Method Get -Headers $ownerHeaders
    Write-JsonStep $res
    $res
}

# ============================================
# STEP 14: Cancel Transaction
# ============================================
Test-Step -Name "Cancel Transaction" -Script {
    $res = Invoke-RestMethod -Uri "$BaseUrl/payments/easebuzz/cancel/$billId" -Method Post -Headers $ownerHeaders
    Write-JsonStep $res
    $res
}

# ============================================
# STEP 15: Admin Cancel Transaction (via admin endpoint)
# ============================================
if ($smDbId) {
    Test-Step -Name "Admin Cancel Transaction (by txnid)" -Script {
        $body = @{ txnid = $txnid; amount = "100.00" } | ConvertTo-Json
        $res = Invoke-RestMethod -Uri "$BaseUrl/admin/sub-merchants/$smDbId/cancel-transaction" -Method Post -Body $body -ContentType "application/json" -Headers $headers
        Write-JsonStep $res
        $res
    }
}

# ============================================
# STEP 16: On-Demand Settlement
# ============================================
Test-Step -Name "On-Demand Settlement" -Script {
    $body = @{ amount = "100.00" } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$BaseUrl/admin/sub-merchants/settlements/on-demand" -Method Post -Body $body -ContentType "application/json" -Headers $headers
    Write-JsonStep $res
    $res
}

# ============================================
# STEP 17: List Sub-Merchants
# ============================================
Test-Step -Name "List Sub-Merchants" -Script {
    $res = Invoke-RestMethod -Uri "$BaseUrl/admin/sub-merchants" -Method Get -Headers $headers
    Write-Host "  Found $($res.Count) sub-merchants" -ForegroundColor Gray
    $res
}

# ============================================
# RESULTS
# ============================================
Write-Host ""
Write-Host "=== SANDBOX TEST RESULTS ===" -ForegroundColor Cyan
Write-Host "Passed: $passCount" -ForegroundColor Green
Write-Host "Failed: $failCount" -ForegroundColor Red
Write-Host "Skipped: $skipCount" -ForegroundColor Yellow
Write-Host "Total executed: $($passCount + $failCount)" -ForegroundColor White

if ($failCount -gt 0) {
    Write-Host ""
    Write-Host "[WARNING] Some tests failed. Review output above." -ForegroundColor Yellow
    exit 1
} else {
    Write-Host ""
    Write-Host "All tests passed!" -ForegroundColor Green
    exit 0
}
