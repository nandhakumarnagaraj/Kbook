param(
    [string]$BaseUrl = "http://localhost:8081/api/v2"
)

$rand = Get-Random -Minimum 10000 -Maximum 99999
$phone = "9" + (Get-Random -Minimum 100000000 -Maximum 999999999).ToString()

Write-Host "=== Step 1: Dev Signup ===" -ForegroundColor Cyan
$signupBody = @{
    phoneNumber = $phone
    name = "Debug $rand"
    password = "admin123"
    deviceId = "DEBUG"
} | ConvertTo-Json

try {
    $authRes = Invoke-RestMethod -Uri "$BaseUrl/auth/signup/dev" -Method Post -Body $signupBody -ContentType "application/json"
    Write-Host "Restaurant ID: $($authRes.restaurantId)" -ForegroundColor Green
    Write-Host "Token: $($authRes.token.Substring(0, 20))..." -ForegroundColor Gray
} catch {
    Write-Host "Signup failed: $_" -ForegroundColor Red
    exit 1
}

$ownerHeaders = @{ Authorization = "Bearer " + $authRes.token }

Write-Host ""
Write-Host "=== Step 2: Create Bill ===" -ForegroundColor Cyan

$bill = @{
    localId = [long]$rand
    restaurantId = "$($authRes.restaurantId)"
    deviceId = "DEBUG"
    dailyOrderId = [long]1
    dailyOrderDisplay = "DBG-$rand"
    lifetimeOrderId = [long]$rand
    orderType = "DINE_IN"
    subtotal = 100.00
    totalAmount = 100.00
    paymentMode = "CASH"
    paymentStatus = "pending"
    orderStatus = "active"
    customerName = "Debug User"
    customerWhatsapp = $phone
    lastResetDate = "2024-05-18"
    createdAt = [long]1712345678
    updatedAt = [long]1712345678
}

$billJson = $bill | ConvertTo-Json
Write-Host "Sending bill payload:" -ForegroundColor Yellow
$billJson

$billBody = "[" + $billJson + "]"

try {
    $res = Invoke-RestMethod -Uri "$BaseUrl/sync/bills/push" -Method Post -Body $billBody -ContentType "application/json" -Headers $ownerHeaders
    Write-Host ""
    Write-Host "Bill created SUCCESSFULLY!" -ForegroundColor Green
    $res | ConvertTo-Json -Depth 5
} catch {
    Write-Host ""
    Write-Host "BILL CREATION FAILED!" -ForegroundColor Red
    Write-Host "Status Code: $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Red
    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    $body = $reader.ReadToEnd()
    Write-Host "Error Body: $body" -ForegroundColor Red
    exit 1
}
