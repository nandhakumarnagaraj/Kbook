$url = "https://9c44e632-cfd8-462d-8f18-f34edaa5ef67-00-38so65l9sg8yr.sisko.replit.dev/api/mcp"
$payload = @{ jsonrpc = "2.0"; id = 1; method = "list_directory"; params = @{ path = "." } } | ConvertTo-Json -Compress
Invoke-WebRequest -Uri $url -Method POST -Body $payload -ContentType "application/json" -Headers @{'Accept'='text/event-stream'} -UseBasicParsing
