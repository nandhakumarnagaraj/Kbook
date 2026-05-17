
$baseUrl = "http://localhost:8081/api/v2"

Write-Host "--- 1. Dev Signup (Owner) ---"
$signupBody = @{
    phoneNumber = "7000000888"
    name = "Post Split Shop"
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
    restaurantId = $restaurantId
    businessName = "Post Split Shop"
    businessType = "SOLE_PROPRIETORSHIP"
    pan = "ABCDE1234F"
    gst = "29ABCDE1234F1Z5"
    bankAccountNo = "1234567890"
    ifsc = "HDFC0000123"
    bankName = "HDFC Bank"
    branchName = "Main Branch"
    beneficiaryName = "Final Owner"
    businessAddress = "Final Verify St"
    contactEmail = "final@example.com"
    contactPhone = "7000000888"
    commissionRate = 3.0
} | ConvertTo-Json
$merchantRes = Invoke-RestMethod -Uri "$baseUrl/admin/sub-merchants" -Method Post -Body $merchantBody -ContentType "application/json" -Headers $adminHeaders
$smId = $merchantRes.id
Write-Host "Draft created! ID: $smId"

Write-Host "`n--- 4. Activate Sub-Merchant ---"
Invoke-RestMethod -Uri "$baseUrl/admin/sub-merchants/$smId/assign-id" -Method Post -Body (@{ subMerchantId = "S360_POST_SPLIT_TEST" } | ConvertTo-Json) -ContentType "application/json" -Headers $adminHeaders | Out-Null
Invoke-RestMethod -Uri "$baseUrl/admin/sub-merchants/$smId/status" -Method Put -Body (@{ status = "ACTIVE" } | ConvertTo-Json) -ContentType "application/json" -Headers $adminHeaders | Out-Null
Invoke-RestMethod -Uri "$baseUrl/admin/sub-merchants/$smId/split-label" -Method Post -Headers $adminHeaders | Out-Null
Write-Host "Sub-Merchant Activated & Split Label Created!"

Write-Host "`n--- 5. Create a Bill (as Owner) ---"
$ownerHeaders = @{ Authorization = "Bearer $ownerToken" }
$bill = @{
    localId = 2001
    restaurantId = $restaurantId
    deviceId = "CLI_VERIFY"
    dailyOrderId = 1
    dailyOrderDisplay = "PS-1"
    totalAmount = 500.00
    paymentStatus = "pending"
    orderStatus = "active"
    createdAt = 1712345678
    updatedAt = 1712345678
}
$billBody = @($bill) | ConvertTo-Json
$billRes = Invoke-RestMethod -Uri "$baseUrl/sync/bills/push" -Method Post -Body $billBody -ContentType "application/json" -Headers $ownerHeaders
# Parse Map instead of Array for localId to serverId
$billId = $billRes.localToServerIdMap."2001"
Write-Host "Bill Created! Server ID: $billId"

Write-Host "`n--- 6. Create Payment Order ---"
$orderBody = @{ billId = $billId; restaurantId = $restaurantId } | ConvertTo-Json
$orderRes = Invoke-RestMethod -Uri "$baseUrl/payments/easebuzz/create-order" -Method Post -Body $orderBody -ContentType "application/json" -Headers $ownerHeaders
Write-Host "Payment Order Success!"
Write-Host "Payment URL: $($orderRes.payment_url)"
Write-Host "Access Token: $($orderRes.access_token)"
