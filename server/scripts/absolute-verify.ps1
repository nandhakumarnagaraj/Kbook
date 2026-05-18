
$baseUrl = "http://localhost:8081/api/v2"

$rand = Get-Random -Minimum 100000 -Maximum 999999
$phone = "930$rand"

Write-Host "--- 1. Dev Signup (Owner) for Phone: $phone ---"
$signupBody = @{
    phoneNumber = $phone
    name = "Auto Success Shop"
    password = "admin123"
    deviceId = "HANDS_ON"
} | ConvertTo-Json
$authRes = Invoke-RestMethod -Uri "$baseUrl/auth/signup/dev" -Method Post -Body $signupBody -ContentType "application/json"
$ownerToken = $authRes.token
$restaurantId = $authRes.restaurantId
Write-Host "Account Created! Restaurant ID: $restaurantId"

# Wait to avoid rate limit
Start-Sleep -Seconds 2

Write-Host "`n--- 2. Login as Admin ---"
$loginBody = @{ loginId = "admin@kbook.com"; password = "admin123" } | ConvertTo-Json
$adminRes = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$adminToken = $adminRes.token
$adminHeaders = @{ Authorization = "Bearer $adminToken" }

Write-Host "`n--- 3. Create & Activate SM ---"
$merchantBody = @{
    restaurantId = "$restaurantId"
    businessName = "Auto Success Shop"
    businessType = "SOLE_PROPRIETORSHIP"
    bankAccountNo = "1234567890"
    ifsc = "HDFC0000123"
    contactEmail = "eb.success@example.com"
    contactPhone = $phone
    commissionRate = 3.0
} | ConvertTo-Json
$merchantRes = Invoke-RestMethod -Uri "$baseUrl/admin/sub-merchants" -Method Post -Body $merchantBody -ContentType "application/json" -Headers $adminHeaders
$smId = $merchantRes.id

Invoke-RestMethod -Uri "$baseUrl/admin/sub-merchants/$smId/assign-id" -Method Post -Body (@{ subMerchantId = "S360_AUTO_$rand" } | ConvertTo-Json) -ContentType "application/json" -Headers $adminHeaders | Out-Null
Invoke-RestMethod -Uri "$baseUrl/admin/sub-merchants/$smId/status" -Method Put -Body (@{ status = "ACTIVE" } | ConvertTo-Json) -ContentType "application/json" -Headers $adminHeaders | Out-Null
Write-Host "Sub-merchant $smId activated!"

Write-Host "`n--- 4. Create Bill & Order ---"
$ownerHeaders = @{ Authorization = "Bearer $ownerToken" }
$billBody = "[{ `"localId`" : $rand, `"restaurantId`" : `"$restaurantId`", `"deviceId`" : `"HANDS_ON`", `"dailyOrderId`" : 1, `"dailyOrderDisplay`" : `"A-$rand`", `"lifetimeOrderId`" : $rand, `"orderType`" : `"TAKEAWAY`", `"subtotal`" : 10.00, `"totalAmount`" : 10.00, `"paymentMode`" : `"UPI`", `"paymentStatus`" : `"pending`", `"orderStatus`" : `"active`", `"lastResetDate`" : `"2024-05-18`", `"cancelReason`" : `"`", `"customerWhatsapp`" : `"9876543210`", `"createdAt`" : 1712345678, `"updatedAt`" : 1712345678 }]"
$billRes = Invoke-RestMethod -Uri "$baseUrl/sync/bills/push" -Method Post -Body $billBody -ContentType "application/json" -Headers $ownerHeaders
$billId = $billRes.localToServerIdMap."$rand"

$orderBody = @{ billId = [long]$billId; restaurantId = "$restaurantId" } | ConvertTo-Json
$orderRes = Invoke-RestMethod -Uri "$baseUrl/payments/easebuzz/create-order" -Method Post -Body $orderBody -ContentType "application/json" -Headers $ownerHeaders
Write-Host "`nFINAL RESULT:"
$orderRes | ConvertTo-Json
