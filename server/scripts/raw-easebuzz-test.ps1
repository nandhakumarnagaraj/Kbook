
$key = "ADNX3KYX5"
$salt = "Z4UFP4939"
$txnid = "KBTEST" + (Get-Random -Minimum 10000 -Maximum 99999).ToString()
$amount = "10.00"
$productinfo = "FoodBill"
$firstname = "John"
$email = "john@example.com"
$surl = "https://www.google.com"
$furl = "https://www.google.com"
$phone = "9876543210"

# key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5|udf6|udf7|udf8|udf9|udf10|salt
$raw = "$key|$txnid|$amount|$productinfo|$firstname|$email|||||||||||$salt"
Write-Host "Raw String: $raw"

$bytes = [System.Text.Encoding]::UTF_8.GetBytes($raw)
$sha = [System.Security.Cryptography.SHA512Managed]::new()
$hashBytes = $sha.ComputeHash($bytes)
$hash = [System.BitConverter]::ToString($hashBytes).Replace("-", "").ToLower()
Write-Host "Generated Hash: $hash"

$body = @{
    key = $key
    txnid = $txnid
    amount = $amount
    productinfo = $productinfo
    firstname = $firstname
    email = $email
    phone = $phone
    surl = $surl
    furl = $furl
    hash = $hash
    sub_merchant_id = "S360_BYPASS_TEST"
    udf1 = ""
    udf2 = ""
    udf3 = ""
    udf4 = ""
    udf5 = ""
    udf6 = ""
    udf7 = ""
    udf8 = ""
    udf9 = ""
    udf10 = ""
}

try {
    $res = Invoke-RestMethod -Uri "https://testpay.easebuzz.in/payment/initiateLink" -Method Post -Body $body
    Write-Host "`nResponse:"
    $res | ConvertTo-Json
} catch {
    Write-Host "`nError:"
    $_.Exception.Response | Select-Object -Property *
}
