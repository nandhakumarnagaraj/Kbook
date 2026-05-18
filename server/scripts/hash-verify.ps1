$key = "ADNX3KYX5"
$salt = "Z4UFP4939"
$txnid = "KBTEST" + (Get-Random -Minimum 10000 -Maximum 99999).ToString()
$amount = "10.00"
$productinfo = "FoodBill"
$firstname = "John"
$email = "john@example.com"

Write-Host "=== Testing Hash Formats ==="
Write-Host ""

# Hash format: key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5|udf6|udf7|udf8|udf9|udf10|salt
# All 10 UDFs empty
$raw10Empty = $key + "|" + $txnid + "|" + $amount + "|" + $productinfo + "|" + $firstname + "|" + $email + "|||||||||||" + $salt
Write-Host "Format (10 empty UDFs): $raw10Empty"

# Try different hash format: key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5||||||salt
# 5 empty UDFs (udf6-udf10 only)
$raw5Empty = $key + "|" + $txnid + "|" + $amount + "|" + $productinfo + "|" + $firstname + "|" + $email + "|||||||||" + $salt
Write-Host "Format (5 empty UDFs): $raw5Empty"

Write-Host ""

$sha = [System.Security.Cryptography.SHA512]::Create()

$bytes10 = [System.Text.Encoding]::UTF8.GetBytes($raw10Empty)
$hashBytes10 = $sha.ComputeHash($bytes10)
$hash10 = [System.BitConverter]::ToString($hashBytes10).Replace("-", "").ToLower()
Write-Host "Hash (10 empty UDFs): $hash10"

$bytes5 = [System.Text.Encoding]::UTF8.GetBytes($raw5Empty)
$hashBytes5 = $sha.ComputeHash($bytes5)
$hash5 = [System.BitConverter]::ToString($hashBytes5).Replace("-", "").ToLower()
Write-Host "Hash (5 empty UDFs): $hash5"

Write-Host ""
Write-Host "=== Testing 10-UDF hash against Easebuzz sandbox ==="
Write-Host ""

# Test with plain form-urlencoded body
$body = @{
    key = $key
    txnid = $txnid
    amount = $amount
    productinfo = $productinfo
    firstname = $firstname
    email = $email
    phone = "9876543210"
    surl = "https://www.google.com"
    furl = "https://www.google.com"
    hash = $hash10
}

# Build form-urlencoded string manually
$formBody = ""
$body.Keys | ForEach-Object {
    $formBody += [System.Uri]::EscapeDataString($_) + "=" + [System.Uri]::EscapeDataString($body[$_]) + "&"
}
$formBody = $formBody.TrimEnd("&")

Write-Host "Form Body: $formBody"
Write-Host ""

try {
    $response = Invoke-WebRequest -Uri "https://testpay.easebuzz.in/payment/initiateLink" -Method Post -Body $formBody -ContentType "application/x-www-form-urlencoded" -UseBasicParsing
    Write-Host "Status: $($response.StatusCode)"
    Write-Host "Response: $($response.Content)"
} catch {
    Write-Host "Result for 10-UDF Format:"
    Write-Host "Status: $($_.Exception.Response.StatusCode.value__)"
    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    $errBody = $reader.ReadToEnd()
    $reader.Close()
    Write-Host "Body: $errBody"
}

Write-Host ""
Write-Host "=== Testing 5-UDF hash against Easebuzz sandbox ==="
Write-Host ""

$body5 = @{
    key = $key
    txnid = $txnid
    amount = $amount
    productinfo = $productinfo
    firstname = $firstname
    email = $email
    phone = "9876543210"
    surl = "https://www.google.com"
    furl = "https://www.google.com"
    hash = $hash5
}

$formBody5 = ""
$body5.Keys | ForEach-Object {
    $formBody5 += [System.Uri]::EscapeDataString($_) + "=" + [System.Uri]::EscapeDataString($body5[$_]) + "&"
}
$formBody5 = $formBody5.TrimEnd("&")

try {
    $response = Invoke-WebRequest -Uri "https://testpay.easebuzz.in/payment/initiateLink" -Method Post -Body $formBody5 -ContentType "application/x-www-form-urlencoded" -UseBasicParsing
    Write-Host "Status: $($response.StatusCode)"
    Write-Host "Response: $($response.Content)"
} catch {
    Write-Host "Result for 5-UDF Format:"
    Write-Host "Status: $($_.Exception.Response.StatusCode.value__)"
    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    $errBody = $reader.ReadToEnd()
    $reader.Close()
    Write-Host "Body: $errBody"
}
