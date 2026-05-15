# Create test user in PostgreSQL directly
$JWT_SECRET = "9a4f2c3d5e6f7g8h9i0j1k2l3m4n5o6p7q8r9s0t1u2v3w4x5y6z7a8b9c0d1e2f"
$JWT_SALT = "test-salt-for-jwt"

# Generate a token using Python
$token = & "C:\Users\nandh\AppData\Local\Python\bin\python.exe" -c "
import hashlib, hmac, json, base64, time

# Create a simple token for testing
header = base64.urlsafe_b64encode(json.dumps({'alg':'HS256','typ':'JWT'}).encode()).rstrip(b'=').decode()
payload_data = {
    'sub': '7000000001',
    'restaurantId': 1001,
    'role': 'KBOOK_ADMIN',
    'iat': int(time.time()),
    'exp': int(time.time()) + 86400 * 365
}
payload = base64.urlsafe_b64encode(json.dumps(payload_data).encode()).rstrip(b'=').decode()
message = f'{header}.{payload}'.encode()
signature = base64.urlsafe_b64encode(hmac.new('$JWT_SECRET'.encode(), message, hashlib.sha256).digest()).rstrip(b'=').decode()
print(f'{header}.{payload}.{signature}')
"

Write-Host "Test JWT Token:"
Write-Host $token
Write-Host ""
Write-Host "Use this token in the browser:"
Write-Host "1. Open DevTools (F12)"
Write-Host "2. In Application tab → Local Storage → http://localhost:4200"
Write-Host "3. Set key: auth_token , value: $token"
Write-Host "4. Refresh the page"
