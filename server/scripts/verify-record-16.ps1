
$baseUrl = "http://localhost:8081/api/v2"
$restaurantId = "933614260070140750" 
$phone = "923150992"

Write-Host "--- 1. Login as Owner ($phone) ---"
$loginBody = @{ loginId = $phone; password = "admin123" } | ConvertTo-Json
$authRes = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$token = $authRes.token
$headers = @{ Authorization = "Bearer $token" }

Write-Host "`n--- 2. Create a unique Bill ---"
$billIdLocal = Get-Random -Minimum 1000000 -Maximum 9999999
$bill = @{
    localId = [long]$billIdLocal
    restaurantId = "$restaurantId"
    deviceId = "CLI_TEST_FINAL"
    dailyOrderId = [long]1
    dailyOrderDisplay = "F-$billIdLocal"
    lifetimeOrderId = [long]$billIdLocal
    totalAmount = 150.00
    paymentStatus = "pending"
    orderStatus = "active"
    createdAt = [long]1712345678
    updatedAt = [long]1712345678
}
$billBody = "[" + ($bill | ConvertTo-Json) + "]"
$billRes = Invoke-RestMethod -Uri "$baseUrl/sync/bills/push" -Method Post -Body $billBody -ContentType "application/json" -Headers $headers
$billIdServer = $billRes.localToServerIdMap."$billIdLocal"
Write-Host "Bill Created! Server ID: $billIdServer"

Write-Host "`n--- 3. Create Payment Order ---"
$orderBody = @{ billId = [long]$billIdServer; restaurantId = "$restaurantId" } | ConvertTo-Json
$orderRes = Invoke-RestMethod -Uri "$baseUrl/payments/easebuzz/create-order" -Method Post -Body $orderBody -ContentType "application/json" -Headers $headers
Write-Host "SUCCESS! Payment URL Generated."
$orderRes | ConvertTo-Json
