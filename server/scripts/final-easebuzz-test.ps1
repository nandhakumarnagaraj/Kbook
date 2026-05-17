
$baseUrl = "http://localhost:8081/api/v2"

Write-Host "--- 1. Login as Admin ---"
$loginBody = @{
    loginId = "admin@kbook.com"
    password = "admin123"
} | ConvertTo-Json
$adminRes = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$adminToken = $adminRes.token
$headers = @{ Authorization = "Bearer $adminToken" }

Write-Host "`n--- 2. Create fresh account ---"
$phone = "9" + (Get-Random -Minimum 100000000 -Maximum 999999999).ToString()
$signupBody = @{
    phoneNumber = $phone
    name = "Easebuzz Live Test"
    password = "admin123"
    deviceId = "TEST_SESSION"
} | ConvertTo-Json
$authRes = Invoke-RestMethod -Uri "$baseUrl/auth/signup/dev" -Method Post -Body $signupBody -ContentType "application/json"
$restaurantId = $authRes.restaurantId
Write-Host "Account created! Restaurant ID: $restaurantId"

Write-Host "`n--- 3. Create SM Draft ---"
$merchantBody = @{
    restaurantId = $restaurantId
    businessName = "Easebuzz Live Test"
    businessType = "SOLE_PROPRIETORSHIP"
    pan = "ABCDE1234F"
    gst = "29ABCDE1234F1Z5"
    bankAccountNo = "1234567890"
    ifsc = "HDFC0000123"
    bankName = "HDFC Bank"
    branchName = "Main Branch"
    beneficiaryName = "Test Owner"
    businessAddress = "Test Address 456"
    contactEmail = "eb.test@example.com"
    contactPhone = $phone
    commissionRate = 3.0
} | ConvertTo-Json
$merchantRes = Invoke-RestMethod -Uri "$baseUrl/admin/sub-merchants" -Method Post -Body $merchantBody -ContentType "application/json" -Headers $headers
$smId = $merchantRes.id
Write-Host "Draft created! ID: $smId"

Write-Host "`n--- 4. Final Submission to Easebuzz ---"
try {
    $submitRes = Invoke-RestMethod -Uri "$baseUrl/admin/sub-merchants/$smId/submit-to-easebuzz" -Method Post -Headers $headers
    $submitRes | ConvertTo-Json
} catch {
    Write-Host "ERROR: $_"
    $_.Exception.Response | Select-Object -Property *
}
