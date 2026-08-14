
$baseUrl = "http://localhost:8081/api/v2"
$restaurantId = "1139426900345307909"

# Admin Token
$adminToken = "eyJhbGciOiJIUzUxMiJ9.eyJqdGkiOiI1MDY4YmJhYS01MGI4LTQzZmYtOTRkMC1lMzI0ZDg2ZTYyYTkiLCJzdWIiOiJhZG1pbkBrYm9vay5jb20iLCJyZXN0YXVyYW50SWQiOjAsInJvbGUiOiJLQk9PS19BRE1JTiIsImlhdCI6MTc3OTA0NTMzMCwiZXhwIjoxNzgxNjM3MzMwfQ.q_8ukCeRRQGhmazehwSbGPUNg2Zgiyn0-7mCi3HuKzgB2I-k66ew9mXmubop6VR4-Wk4_zQOnqPg4VmEbzFkrQ"
$headers = @{ Authorization = "Bearer $adminToken" }

Write-Host "--- 1. Creating a Bill ---"
$bill = @{
    localId = 999
    restaurantId = $restaurantId
    deviceId = "CLI"
    dailyOrderId = 1
    dailyOrderDisplay = "CLI-1"
    totalAmount = 100.00
    paymentStatus = "pending"
    orderStatus = "active"
    createdAt = 1712345678
    updatedAt = 1712345678
}
$billBody = @($bill) | ConvertTo-Json
$billRes = Invoke-RestMethod -Uri "$baseUrl/sync/bills" -Method Post -Body $billBody -ContentType "application/json" -Headers $headers
$billId = $billRes[0].serverId
Write-Host "Bill Created! Server ID: $billId"

Write-Host "`n--- 2. Login as Owner ---"
$loginBody = @{ loginId = "9876543210"; password = "admin123" } | ConvertTo-Json
$authRes = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$token = $authRes.token
$ownerHeaders = @{ Authorization = "Bearer $token" }

Write-Host "`n--- 3. Create Payment Order ---"
$orderBody = @{ billId = $billId; restaurantId = $restaurantId } | ConvertTo-Json
$orderRes = Invoke-RestMethod -Uri "$baseUrl/payments/easebuzz/create-order" -Method Post -Body $orderBody -ContentType "application/json" -Headers $ownerHeaders
$orderRes | ConvertTo-Json
