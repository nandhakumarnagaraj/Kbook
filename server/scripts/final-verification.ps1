
$baseUrl = "http://localhost:8081/api/v2"

$rand = Get-Random -Minimum 100000 -Maximum 999999
$phone = "9" + (Get-Random -Minimum 100000000 -Maximum 999999999).ToString()

Write-Host "--- 1. Dev Signup (Owner) for Phone: $phone ---"
$signupBody = @{
    phoneNumber = $phone
    name = "Verified Shop $rand"
    password = "admin123"
    deviceId = "CLI_VERIFY"
} | ConvertTo-Json
$authRes = Invoke-RestMethod -Uri "$baseUrl/auth/signup/dev" -Method Post -Body $signupBody -ContentType "application/json"
$ownerToken = $authRes.token
$restaurantId = $authRes.restaurantId
Write-Host "Owner Created! Restaurant ID: $restaurantId"

Write-Host "`n--- 2. Login as Admin to Onboard ---"
$loginBody = @{ loginId = "admin@kbook.com"; password = "admin123" } | ConvertTo-Json
$adminRes = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$adminToken = $adminRes.token
$adminHeaders = @{ Authorization = "Bearer $adminToken" }

Write-Host "`n--- 3. Create Sub-Merchant Draft ---"
$merchantBody = @{
    restaurantId = "$restaurantId"
    businessName = "Verified Shop $rand"
    businessType = "SOLE_PROPRIETORSHIP"
    pan = "ABCDE1234F"
    gst = "29ABCDE1234F1Z5"
    bankAccountNo = "1234567890"
    ifsc = "HDFC0000123"
    bankName = "HDFC Bank"
    branchName = "Main Branch"
    beneficiaryName = "Owner $rand"
    businessAddress = "Verification Lane"
    contactEmail = "verify$rand@example.com"
    contactPhone = $phone
    commissionRate = 3.0
} | ConvertTo-Json
$merchantRes = Invoke-RestMethod -Uri "$baseUrl/admin/sub-merchants" -Method Post -Body $merchantBody -ContentType "application/json" -Headers $adminHeaders
$smId = $merchantRes.id
Write-Host "Draft created! ID: $smId"

Write-Host "`n--- 4. Activate Sub-Merchant Locally ---"
Invoke-RestMethod -Uri "$baseUrl/admin/sub-merchants/$smId/assign-id" -Method Post -Body (@{ subMerchantId = "S360_VERIFY_$rand" } | ConvertTo-Json) -ContentType "application/json" -Headers $adminHeaders | Out-Null
Invoke-RestMethod -Uri "$baseUrl/admin/sub-merchants/$smId/status" -Method Put -Body (@{ status = "ACTIVE" } | ConvertTo-Json) -ContentType "application/json" -Headers $adminHeaders | Out-Null
Write-Host "Sub-Merchant Activated!"

Write-Host "`n--- 5. Create a Bill (as Owner) ---"
$ownerHeaders = @{ Authorization = "Bearer $ownerToken" }
$bill = @{
    localId = [long]$rand
    restaurantId = "$restaurantId"
    deviceId = "CLI_VERIFY"
    dailyOrderId = [long]1
    dailyOrderDisplay = "V-$rand"
    lifetimeOrderId = [long]$rand
    orderType = "DINE_IN"
    subtotal = 100.00
    totalAmount = 100.00
    paymentMode = "CASH"
    paymentStatus = "pending"
    orderStatus = "active"
    customerWhatsapp = $phone
    customerName = "CLI Tester"
    lastResetDate = "2024-05-18"
    cancelReason = ""
    createdAt = [long]1712345678
    updatedAt = [long]1712345678
}
$billBody = "[" + ($bill | ConvertTo-Json) + "]"
$billRes = Invoke-RestMethod -Uri "$baseUrl/sync/bills/push" -Method Post -Body $billBody -ContentType "application/json" -Headers $ownerHeaders
$billId = $billRes.localToServerIdMap."$rand"
Write-Host "Bill Created! Server ID: $billId"

Write-Host "`n--- 6. Create Payment Order ---"
$orderBody = @{ billId = [long]$billId; restaurantId = "$restaurantId" } | ConvertTo-Json
$orderRes = Invoke-RestMethod -Uri "$baseUrl/payments/easebuzz/create-order" -Method Post -Body $orderBody -ContentType "application/json" -Headers $ownerHeaders
Write-Host "Payment Order Success!"
Write-Host "Payment URL: $($orderRes.payment_url)"
Write-Host "Access Token: $($orderRes.access_token)"
