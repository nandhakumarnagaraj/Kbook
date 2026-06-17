$key = "ADNX3KYX5"
$salt = "Z4UFP4939"
$txnid = "KBTEST" + (Get-Random -Minimum 100000 -Maximum 999999).ToString()
$amount = "10.00"
$productinfo = "test"
$firstname = "test"
$email = "test@test.com"
$phone = "9999999999"
$surl = "https://kbook.iadv.cloud/api/v2/payments/easebuzz/return"
$furl = "https://kbook.iadv.cloud/api/v2/payments/easebuzz/return"

$udf1 = "111"
$udf2 = "4160018589723508532"
$udf3 = ""
$udf4 = ""
$udf5 = ""
$udf6 = ""
$udf7 = ""
$udf8 = ""
$udf9 = ""
$udf10 = ""

# Generate Hash
$hashStr = "$key|$txnid|$amount|$productinfo|$firstname|$email|$udf1|$udf2|$udf3|$udf4|$udf5|$udf6|$udf7|$udf8|$udf9|$udf10|$salt"
Write-Host "Hash String: $hashStr"

$hasher = [System.Security.Cryptography.HashAlgorithm]::Create("SHA512")
$hashBytes = $hasher.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($hashStr))
$hash = [System.BitConverter]::ToString($hashBytes).Replace("-", "").ToLower()
Write-Host "Generated Hash: $hash"

# 1. Test initiation WITHOUT sub_merchant_id
Write-Host "--------------------------------------------------"
Write-Host "1. Initiating WITHOUT sub_merchant_id..."
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
    udf1 = $udf1
    udf2 = $udf2
    udf3 = $udf3
    udf4 = $udf4
    udf5 = $udf5
    udf6 = $udf6
    udf7 = $udf7
    udf8 = $udf8
    udf9 = $udf9
    udf10 = $udf10
}

try {
    $response = Invoke-WebRequest -Uri "https://testpay.easebuzz.in/payment/initiateLink" -Method Post -Body $body -ContentType "application/x-www-form-urlencoded" -UseBasicParsing
    Write-Host "Status: $($response.StatusCode)"
    Write-Host "Response Body: $($response.Content)"
} catch {
    Write-Host "Exception caught: $_"
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $errBody = $reader.ReadToEnd()
        $reader.Close()
        Write-Host "Error Body: $errBody"
    }
}

# 2. Test initiation WITH sub_merchant_id
$txnid2 = "KBTEST" + (Get-Random -Minimum 100000 -Maximum 999999).ToString()
$hashStr2 = "$key|$txnid2|$amount|$productinfo|$firstname|$email|$udf1|$udf2|$udf3|$udf4|$udf5|$udf6|$udf7|$udf8|$udf9|$udf10|$salt"
$hashBytes2 = $hasher.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($hashStr2))
$hash2 = [System.BitConverter]::ToString($hashBytes2).Replace("-", "").ToLower()

Write-Host "--------------------------------------------------"
Write-Host "2. Initiating WITH sub_merchant_id = S280498QBR8..."
$body2 = @{
    key = $key
    txnid = $txnid2
    amount = $amount
    productinfo = $productinfo
    firstname = $firstname
    email = $email
    phone = $phone
    surl = $surl
    furl = $furl
    hash = $hash2
    udf1 = $udf1
    udf2 = $udf2
    udf3 = $udf3
    udf4 = $udf4
    udf5 = $udf5
    udf6 = $udf6
    udf7 = $udf7
    udf8 = $udf8
    udf9 = $udf9
    udf10 = $udf10
    sub_merchant_id = "S280498QBR8"
}

try {
    $response = Invoke-WebRequest -Uri "https://testpay.easebuzz.in/payment/initiateLink" -Method Post -Body $body2 -ContentType "application/x-www-form-urlencoded" -UseBasicParsing
    Write-Host "Status: $($response.StatusCode)"
    Write-Host "Response Body: $($response.Content)"
} catch {
    Write-Host "Exception caught: $_"
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $errBody = $reader.ReadToEnd()
        $reader.Close()
        Write-Host "Error Body: $errBody"
    }
}
