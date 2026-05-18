$BaseUrl = "http://localhost:8081/api/v2"
$rand = Get-Random -Minimum 10000 -Maximum 99999

# Login as admin
$loginBody = @{ loginId = "admin@kbook.com"; password = "admin123" } | ConvertTo-Json
$adminRes = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$headers = @{ Authorization = "Bearer $($adminRes.token)" }

# Create owner
$phone = "9" + (Get-Random -Minimum 100000000 -Maximum 999999999).ToString()
$signupBody = @{ phoneNumber = $phone; name = "Debug $rand"; password = "admin123"; deviceId = "DEBUG" } | ConvertTo-Json
$authRes = Invoke-RestMethod -Uri "$BaseUrl/auth/signup/dev" -Method Post -Body $signupBody -ContentType "application/json"
$restaurantId = $authRes.restaurantId
$ownerHeaders = @{ Authorization = "Bearer $($authRes.token)" }

Write-Host "restaurantId=$restaurantId phone=$phone"

# Create bill
$bill = @{
    localId = [long]$rand
    restaurantId = "$restaurantId"
    deviceId = "DEBUG"
    dailyOrderId = [long]1
    dailyOrderDisplay = "DB-$rand"
    lifetimeOrderId = [long]$rand
    orderType = "DINE_IN"
    subtotal = 100.00
    totalAmount = 100.00
    paymentMode = "UPI"
    paymentStatus = "pending"
    orderStatus = "active"
    customerWhatsapp = $phone
    customerName = "Debug Tester"
    lastResetDate = "2024-05-18"
    cancelReason = ""
    createdAt = [long]1712345678
    updatedAt = [long]1712345678
}
$billJson = $bill | ConvertTo-Json
$billBody = "[" + $billJson + "]"
$billRes = Invoke-RestMethod -Uri "$BaseUrl/sync/bills/push" -Method Post -Body $billBody -ContentType "application/json" -Headers $ownerHeaders
$billId = $billRes.localToServerIdMap."$rand"
Write-Host "billId=$billId"

# Try create-order with full error capture
$orderBody = @{ billId = [long]$billId; restaurantId = "$restaurantId" } | ConvertTo-Json
Write-Host "Order Request: $orderBody"

try {
    $response = Invoke-WebRequest -Uri "$BaseUrl/payments/easebuzz/create-order" -Method Post -Body $orderBody -ContentType "application/json" -Headers $ownerHeaders -UseBasicParsing
    Write-Host "Status: $($response.StatusCode)"
    Write-Host "Response: $($response.Content)"
} catch {
    Write-Host "Exception caught: $_"
    if ($_.Exception.Response) {
        Write-Host "HTTP Status: $($_.Exception.Response.StatusCode.value__)"
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $errBody = $reader.ReadToEnd()
        $reader.Close()
        Write-Host "Error Body: $errBody"
    }
}
