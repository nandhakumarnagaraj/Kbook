# MCP Install Links – Reference Guide

## Overview
Install links let users add your MCP server to Replit with a single click.  The link contains a **base64‑encoded JSON payload** that describes the server configuration.

---

## 1️⃣ Link format
The generic format is:

```
https://replit.com/integrations?mcp={payload}
```

*`{payload}`* is a **base64‑encoded** JSON object with the following fields:

| Field       | Type   | Description |
|-------------|--------|-------------|
| `displayName` | `string` | Human‑readable name shown during installation. |
| `baseUrl`   | `string` | The HTTPS endpoint of your MCP server. |
| `headers`   | `array` (optional) | List of HTTP headers needed for authentication. Each entry is an object with `key` and `value` strings. |

### Example payload (plain JSON)
```json
{
  "displayName": "KhanaBook Replit MCP",
  "baseUrl": "https://your-domain.com/mcp/replit",
  "headers": [
    {
      "key": "Authorization",
      "value": "Bearer YOUR_REPLIT_API_TOKEN"
    }
  ]
}
```

### Encode to base64 (CLI example)
```bash
# Save the JSON to a file called payload.json
cat > payload.json <<'EOF'
{
  "displayName": "KhanaBook Replit MCP",
  "baseUrl": "https://your-domain.com/mcp/replit",
  "headers": [{"key":"Authorization","value":"Bearer YOUR_REPLIT_API_TOKEN"}]
}
EOF

# Encode
base64 -w 0 payload.json
```
The output (single line) is the value you substitute for `{payload}`.

---

## 2️⃣ Using badges
Add a clickable badge to your README or any documentation page.  The badge image is hosted by Replit; clicking it opens the install link.

### Basic badge markdown
```markdown
[![Install My MCP](https://replit.com/badge?caption=Install%20My%20MCP)](https://replit.com/integrations?mcp={payload})
```
* Replace `{payload}` with the base64 payload you generated.
* `caption` is URL‑encoded text displayed on the badge (max 30 characters).

### Example badge for the current server
Assuming you have hosted the MCP server at `https://example.com/mcp/replit` and have a token `abc123`:

```markdown
[![Install KhanaBook MCP](https://replit.com/badge?caption=Install%20KhanaBook%20MCP)](https://replit.com/integrations?mcp=eyJkaXNwbGF5TmFtZSI6ICJLaGFuYUJvb2sgUmVwbGl0IE1DUCIsICJiYXNlVXJsIjogImh0dHBzOi8vZXhhbXBsZS5jb20vbWNwL3JlcGxpdCIsICJoZWFkZXJzIjogW3sia2V5IjogIkF1dGhvcml6YXRpb24iLCAidmFsdWUiOiAiQmVhcmVyIGFiYzEyMyJ9XX0=)
```
(The long string after `mcp=` is the base64‑encoded JSON payload.)

---

## 3️⃣ Badge caption customization
* Caption text must be URL‑encoded.
* Maximum length is **30 characters**.
* Example with a custom caption:

```markdown
[![Install Super‑MCP](https://replit.com/badge?caption=Install%20Super%20MCP)](https://replit.com/integrations?mcp={payload})
```

---

## 4️⃣ Quick checklist
1. **Host** an HTTPS endpoint that serves your MCP tools (e.g., the folder created under `mcp/replit`).
2. **Create** the JSON payload (display name, base URL, optional headers).
3. **Base64‑encode** the payload (no line breaks).
4. **Compose** the install URL and badge markdown.
5. **Add** the badge to your README or docs.

---

## 5️⃣ Real‑world example (for the server you just set up)
```json
{
  "displayName": "KhanaBook Replit MCP",
  "baseUrl": "https://your-public-domain.com/mcp/replit",
  "headers": [{"key":"Authorization","value":"Bearer <YOUR_REPLIT_API_TOKEN>"}]
}
```
Encode it (e.g., with PowerShell):
```powershell
$payload = @'
{
  "displayName": "KhanaBook Replit MCP",
  "baseUrl": "https://your-public-domain.com/mcp/replit",
  "headers": [{"key":"Authorization","value":"Bearer <YOUR_REPLIT_API_TOKEN>"}]
}
'@
[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($payload))
```
Insert the resulting string into the URL and badge markdown.

---

**That’s it!** You now have a ready‑to‑share reference for MCP install links, the required payload schema, and badge markup. Feel free to copy this file into your repository’s root (`MCP_INSTALL_LINKS.md`).
