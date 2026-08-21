# Penetration Testing with Strix

## Trigger Conditions
- Before production deployment
- After adding new API endpoints
- User asks to "pentest", "security scan", or "find vulnerabilities"
- Periodic security audit (monthly recommended)
- After dependency updates (new CVEs)
- Responding to security incident

---

## Setup: Strix for KhanaBook

```bash
# Strix targets the Spring Boot server
# Config in mcp/strix-mcp.json points to ./server

# Run against local instance
strix --target ./server -n

# Run against staging
strix --target https://staging-api.khanabook.in -n
```

### Prerequisites
- Server running locally on port 8080
- Test database with seed data (never scan production)
- Valid test credentials for authenticated endpoints
- Strix MCP configured (see mcp/strix-mcp.json)

---

## OWASP Top 10 Focus Areas

### A01: Broken Access Control
```
Tests:
- Access other merchant's bills: GET /api/v1/bills/{other-merchant-bill-id}
- IDOR on all resource endpoints (change UUID in path)
- Role escalation: staff user accessing admin endpoints
- Missing auth on new endpoints (common during development)
- Horizontal privilege escalation between merchants

Strix Command:
  strix scan --category access-control --target http://localhost:8080
```

**KhanaBook Specific Checks:**
```bash
# Can merchant A access merchant B's data?
curl -H "Authorization: Bearer $TOKEN_MERCHANT_A" \
  http://localhost:8080/api/v1/bills/$BILL_ID_MERCHANT_B
# Expected: 404 (not 403, to avoid confirming existence)
```

### A02: Cryptographic Failures
```
Tests:
- Check TLS configuration (min TLS 1.2)
- Verify payment hashes use SHA-512 (not MD5/SHA-1)
- Check JWT signing algorithm (reject "none", use RS256 or HS256)
- Verify passwords hashed with bcrypt (cost >= 12)
- Check for sensitive data in logs

Strix Command:
  strix scan --category crypto --target http://localhost:8080
```

### A03: Injection
```
Tests:
- SQL injection on search/filter parameters
- NoSQL injection (if applicable)
- Command injection on any file/path parameters
- Header injection (Host, X-Forwarded-For)

KhanaBook Priority: Search endpoints, report date filters, menu name fields

Strix Command:
  strix scan --category injection --target http://localhost:8080
```

**Manual SQL Injection Tests:**
```bash
# Parameter injection
curl "http://localhost:8080/api/v1/menu?search=rice' OR '1'='1"
curl "http://localhost:8080/api/v1/bills?date=2024-01-01' UNION SELECT--"
# Expected: 400 Bad Request (not 200 with data)
```

### A04: Insecure Design
```
Tests:
- Rate limiting on login/OTP endpoints
- Account lockout after N failed attempts
- Payment flow cannot be replayed
- No mass assignment (extra fields in JSON ignored)

Strix Command:
  strix scan --category design --target http://localhost:8080
```

### A05: Security Misconfiguration
```
Tests:
- Actuator endpoints exposed publicly (/actuator/env, /actuator/health)
- Stack traces in error responses
- Default credentials still active
- CORS allowing wildcard origins
- Missing security headers (CSP, HSTS, X-Frame-Options)

Strix Command:
  strix scan --category misconfig --target http://localhost:8080
```

**Verify Security Headers:**
```bash
curl -I http://localhost:8080/api/v1/health
# Required headers:
# X-Content-Type-Options: nosniff
# X-Frame-Options: DENY
# Strict-Transport-Security: max-age=31536000
# Cache-Control: no-store (on auth endpoints)
```

### A07: Authentication Failures
```
Tests:
- OTP brute force (should lock after 5 attempts)
- Token expiry respected
- Refresh token rotation (old tokens invalidated)
- Session fixation
- JWT manipulation (algorithm confusion, expired tokens)

KhanaBook Priority: Phone OTP login flow, API token handling
```

### A08: Data Integrity Failures
```
Tests:
- Verify payment webhook signatures
- Check for unsigned/unverified data from client
- Dependency vulnerabilities (npm audit, mvn dependency-check)

Strix Command:
  strix scan --category integrity --target http://localhost:8080
```

### A09: Logging & Monitoring
```
Tests:
- Failed login attempts logged
- Payment failures logged with txn details
- No sensitive data in logs (passwords, card numbers, OTPs)
- Log injection prevention (newline in user input)
```

---

## Running a Full Scan

```bash
# 1. Start local server
cd server && mvn spring-boot:run -Dspring.profiles.active=test

# 2. Run comprehensive Strix scan
strix scan --target http://localhost:8080 \
  --auth-token "$TEST_JWT_TOKEN" \
  --openapi ./server/src/main/resources/openapi.yaml \
  --output ./security-reports/scan-$(date +%Y%m%d).json

# 3. Review findings
strix report --input ./security-reports/scan-*.json --format html

# 4. Prioritize by severity
strix report --severity critical,high --format table
```

---

## Interpreting Findings

```
Severity | Action                           | Timeline
---------|----------------------------------|----------
CRITICAL | Stop release, fix immediately    | Same day
HIGH     | Fix before next release          | This sprint
MEDIUM   | Schedule fix                     | Next sprint
LOW      | Track in backlog                 | When convenient
INFO     | Document as accepted risk        | No fix needed
```

---

## Fix Patterns

### SQL Injection Fix
```java
// BAD: String concatenation
String query = "SELECT * FROM bills WHERE merchant_id = '" + merchantId + "'";

// GOOD: Parameterized query
@Query("SELECT b FROM Bill b WHERE b.merchantId = :merchantId")
List<Bill> findByMerchant(@Param("merchantId") UUID merchantId);
```

### IDOR Fix
```java
// BAD: No ownership check
public Bill getBill(UUID billId) {
    return billRepository.findById(billId).orElseThrow();
}

// GOOD: Scoped to authenticated merchant
public Bill getBill(UUID billId, UUID merchantId) {
    return billRepository.findByIdAndMerchantId(billId, merchantId)
        .orElseThrow(() -> new ResourceNotFoundException("Bill", billId));
}
```

---

## CI/CD Integration

```yaml
# .github/workflows/security-scan.yml
security-scan:
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - name: Start server
      run: docker-compose up -d server
    - name: Run Strix scan
      run: |
        strix scan --target http://localhost:8080 \
          --fail-on high \
          --output security-report.json
    - name: Upload report
      uses: actions/upload-artifact@v4
      with:
        name: security-report
        path: security-report.json
```

---

## Anti-patterns
- ❌ Running scans against production
- ❌ Ignoring MEDIUM severity findings indefinitely
- ❌ Only scanning before first release (do it continuously)
- ❌ Not retesting after fixes (verify the fix works)
- ❌ Treating security scan as one-time checkbox
- ❌ Skipping authenticated endpoint testing

## Verification Checklist
- [ ] Strix configured and scanning successfully
- [ ] All CRITICAL/HIGH findings resolved
- [ ] IDOR tested on all resource endpoints
- [ ] Payment hash verification confirmed working
- [ ] Rate limiting active on auth endpoints
- [ ] Security headers present on all responses
- [ ] No sensitive data in application logs
- [ ] CI/CD pipeline includes security scan step
- [ ] Scan report archived for compliance
