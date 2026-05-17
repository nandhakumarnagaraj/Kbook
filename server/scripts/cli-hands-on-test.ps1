
$baseUrl = "http://localhost:8081/api/v2"

Write-Host "--- 1. Auth: Dev Signup (Owner Account) ---"
$signupBody = @{
    phoneNumber = "8" + (Get-Random -Minimum 100000000 -Maximum 999999999).ToString()
    name = "HandsOn Shop"
    password = "admin123"
    deviceId = "CLI_TEST"
} | ConvertTo-Json

$authRes = Invoke-RestMethod -Uri "$baseUrl/auth/signup/dev" -Method Post -Body $signupBody -ContentType "application/json"
$restaurantId = $authRes.restaurantId
Write-Host "Owner Created! Restaurant ID: $restaurantId"

Write-Host "`n--- 2. Auth: Login as Admin ---"
$adminLoginBody = @{
    loginId = "admin@kbook.com"
    password = "admin123"
} | ConvertTo-Json
$adminRes = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $adminLoginBody -ContentType "application/json"
$adminToken = $adminRes.token
Write-Host "Admin Logged In!"

$headers = @{
    Authorization = "Bearer $adminToken"
}

Write-Host "`n--- 3. Sub-Merchant: Create Draft (as Admin) ---"
$merchantBody = @{
    restaurantId = "$restaurantId"
    businessName = "HandsOn Shop"
    businessType = "SOLE_PROPRIETORSHIP"
    pan = "ABCDE1234F"
    gst = "29ABCDE1234F1Z5"
    bankAccountNo = "1234567890"
    ifsc = "HDFC0000123"
    bankName = "HDFC Bank"
    branchName = "Main Branch"
    beneficiaryName = "CLI Test Owner"
    businessAddress = "123 Test Street, Bengaluru"
    contactEmail = "cli.test@example.com"
    contactPhone = "9876543210"
    commissionRate = 3.0
} | ConvertTo-Json

$merchantRes = Invoke-RestMethod -Uri "$baseUrl/admin/sub-merchants" -Method Post -Body $merchantBody -ContentType "application/json" -Headers $headers
$smId = $merchantRes.id
Write-Host "Draft Created! Local SM ID: $smId"

Write-Host "`n--- 4. Sub-Merchant: Submit to Easebuzz ---"
$submitRes = Invoke-RestMethod -Uri "$baseUrl/admin/sub-merchants/$smId/submit-to-easebuzz" -Method Post -Headers $headers
Write-Host "Easebuzz Submission Success! Status: $($submitRes.status)"
Write-Host "Easebuzz ID: $($submitRes.subMerchantId)"
Write-Host "Easebuzz Message: $($submitRes.easebuzzResponse)"
