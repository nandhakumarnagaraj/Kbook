# Identify Google client ID on server

**Session ID:** ses_1e973343affew8VlBJ3pIDSPCV
**Created:** 5/11/2026, 10:19:46 AM
**Updated:** 5/11/2026, 10:20:03 AM

---

## User

tell me which google client id is used on this server..

---

## Assistant (Build · Big Pickle · 4.0s)

_Thinking:_

The user wants to know which Google Client ID is used on this server. I need to search for Google Client ID or similar OAuth credentials in the codebase.

**Tool: grep**

**Input:**
```json
{
  "pattern": "GOOGLE.*CLIENT.*ID|google.*client.*id|GOOGLE_CLIENT_ID|client_id.*google|googleClientId",
  "path": "/var/www/kbook.iadv.cloud"
}
```

**Output:**
```
Found 30 matches
/var/www/kbook.iadv.cloud/server/src/main/resources/application.properties:
  Line 67: google.client.id=${GOOGLE_CLIENT_ID:}


/var/www/kbook.iadv.cloud/web-admin/src/app/pages/login/login-page.component.ts:
  Line 148:           client_id: environment.googleClientId,


/var/www/kbook.iadv.cloud/Android/app/google-services.json:
  Line 17:           "client_id": "836086274000-cfcn0q343fm5455bt7mhdt1sl0vmlv3c.apps.googleusercontent.com",

  Line 25:           "client_id": "836086274000-q9vpsq3ljrr69i2i50egpg1s8vf7l62v.apps.googleusercontent.com",

  Line 33:           "client_id": "836086274000-csivf8msgpphl69hn6ftqdeb1mjceaak.apps.googleusercontent.com",

  Line 46:               "client_id": "836086274000-csivf8msgpphl69hn6ftqdeb1mjceaak.apps.googleusercontent.com",


/var/www/kbook.iadv.cloud/Android/app/build.gradle.kts:
  Line 39: val googleWebClientId = configValue("GOOGLE_WEB_CLIENT_ID")

  Line 94:         buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")

  Line 181:                         "GOOGLE_WEB_CLIENT_ID is missing or still set to a placeholder. " +


/var/www/kbook.iadv.cloud/web-admin/src/environments/environment.ts:
  Line 5:   googleClientId: '836086274000-csivf8msgpphl69hn6ftqdeb1mjceaak.apps.googleusercontent.com'


/var/www/kbook.iadv.cloud/web-admin/src/environments/environment.prod.ts:
  Line 5:   googleClientId: '836086274000-csivf8msgpphl69hn6ftqdeb1mjceaak.apps.googleusercontent.com'


/var/www/kbook.iadv.cloud/Android/secrets.properties.example:
  Line 17: # Must match the production server GOOGLE_CLIENT_ID and Play production OAuth setup.

  Line 19: GOOGLE_WEB_CLIENT_ID=836086274000-csivf8msgpphl69hn6ftqdeb1mjceaak.apps.googleusercontent.com


/var/www/kbook.iadv.cloud/.env.example:
  Line 16: GOOGLE_CLIENT_ID=CHANGE_ME.apps.googleusercontent.com


/var/www/kbook.iadv.cloud/README.md:
  Line 92: GOOGLE_WEB_CLIENT_ID=your_google_web_client_id.apps.googleusercontent.com


/var/www/kbook.iadv.cloud/docker-compose.yml:
  Line 19:       GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID}


/var/www/kbook.iadv.cloud/docker-compose.production.yml:
  Line 35:       GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID}


/var/www/kbook.iadv.cloud/server/src/main/java/com/khanabook/saas/service/impl/AuthServiceImpl.java:
  Line 131: 			com.google.api.client.googleapis.auth.oauth2.GoogleIdToken idToken = googleIdTokenVerifier.verify(request.getIdToken());

  Line 133: 				com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload payload = idToken.getPayload();


/var/www/kbook.iadv.cloud/ops/PRODUCTION_STACK.md:
  Line 23: - `GOOGLE_CLIENT_ID`


/var/www/kbook.iadv.cloud/Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/LoginScreen.kt:
  Line 74:         val serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.takeIf { it.isNotBlank() }


/var/www/kbook.iadv.cloud/server/src/test/java/com/khanabook/saas/BaseIntegrationTest.java:
  Line 41:         registry.add("GOOGLE_CLIENT_ID", () -> "test-google-client-id");


/var/www/kbook.iadv.cloud/server/src/main/java/com/khanabook/saas/config/GoogleAuthConfig.java:
  Line 19:     @Value("${google.client.id:}")

  Line 20:     private String googleClientId;

  Line 24:         if (googleClientId == null || googleClientId.isBlank() || googleClientId.contains("${")) {

  Line 25:             log.warn("GOOGLE_CLIENT_ID is not configured. Google login will be unavailable.");

  Line 29:                 googleClientId.substring(Math.max(0, googleClientId.length() - 12)));

  Line 31:                 .setAudience(Collections.singletonList(googleClientId))


/var/www/kbook.iadv.cloud/Android/PLAYSTORE_RELEASE_CHECKLIST.md:
  Line 12: - Confirm `GOOGLE_WEB_CLIENT_ID` is set to the production web client ID expected by the server.

  Line 21: GOOGLE_WEB_CLIENT_ID=836086274000-csivf8msgpphl69hn6ftqdeb1mjceaak.apps.googleusercontent.com

```

**Tool: grep**

**Input:**
```json
{
  "pattern": "\\.googleusercontent\\.com",
  "path": "/var/www/kbook.iadv.cloud"
}
```

**Output:**
```
Found 11 matches
/var/www/kbook.iadv.cloud/Android/app/google-services.json:
  Line 17:           "client_id": "836086274000-cfcn0q343fm5455bt7mhdt1sl0vmlv3c.apps.googleusercontent.com",

  Line 25:           "client_id": "836086274000-q9vpsq3ljrr69i2i50egpg1s8vf7l62v.apps.googleusercontent.com",

  Line 33:           "client_id": "836086274000-csivf8msgpphl69hn6ftqdeb1mjceaak.apps.googleusercontent.com",

  Line 46:               "client_id": "836086274000-csivf8msgpphl69hn6ftqdeb1mjceaak.apps.googleusercontent.com",


/var/www/kbook.iadv.cloud/Android/app/build.gradle.kts:
  Line 28:         "apps.googleusercontent.com",


/var/www/kbook.iadv.cloud/web-admin/src/environments/environment.ts:
  Line 5:   googleClientId: '836086274000-csivf8msgpphl69hn6ftqdeb1mjceaak.apps.googleusercontent.com'


/var/www/kbook.iadv.cloud/web-admin/src/environments/environment.prod.ts:
  Line 5:   googleClientId: '836086274000-csivf8msgpphl69hn6ftqdeb1mjceaak.apps.googleusercontent.com'


/var/www/kbook.iadv.cloud/Android/secrets.properties.example:
  Line 19: GOOGLE_WEB_CLIENT_ID=836086274000-csivf8msgpphl69hn6ftqdeb1mjceaak.apps.googleusercontent.com


/var/www/kbook.iadv.cloud/.env.example:
  Line 16: GOOGLE_CLIENT_ID=CHANGE_ME.apps.googleusercontent.com


/var/www/kbook.iadv.cloud/README.md:
  Line 92: GOOGLE_WEB_CLIENT_ID=your_google_web_client_id.apps.googleusercontent.com


/var/www/kbook.iadv.cloud/Android/PLAYSTORE_RELEASE_CHECKLIST.md:
  Line 21: GOOGLE_WEB_CLIENT_ID=836086274000-csivf8msgpphl69hn6ftqdeb1mjceaak.apps.googleusercontent.com

```

---

## Assistant (Build · Big Pickle · 4.8s)

_Thinking:_

Let me check the actual `.env` file on the server to see what value is set for `GOOGLE_CLIENT_ID`, since the server reads from an env variable. The `.env.example` shows `CHANGE_ME` but the actual `.env` or environment on this server may have the real value.

**Tool: glob**

**Input:**
```json
{
  "pattern": "**/.env*",
  "path": "/var/www/kbook.iadv.cloud"
}
```

**Output:**
```
/var/www/kbook.iadv.cloud/.env
/var/www/kbook.iadv.cloud/.env.example
```

**Tool: bash**

**Input:**
```json
{
  "command": "docker exec $(docker ps -q --filter name=kbook-server) sh -c 'echo $GOOGLE_CLIENT_ID' 2>/dev/null || echo \"no container\"",
  "description": "Check GOOGLE_CLIENT_ID in running container"
}
```

**Output:**
```
no container

```

---

## Assistant (Build · Big Pickle · 4.5s)

_Thinking:_

Let me check the .env file to see the actual value.

**Tool: read**

**Input:**
```json
{
  "filePath": "/var/www/kbook.iadv.cloud/.env"
}
```

**Output:**
```
<path>/var/www/kbook.iadv.cloud/.env</path>
<type>file</type>
<content>
1: POSTGRES_DB=kbook_saas
2: POSTGRES_USER=kbookuser
3: POSTGRES_PASSWORD=Advocacy@Kbook2026
4: DB_URL=jdbc:postgresql://kbookiadvcloud-postgres-1:5432/kbook_saas
5: DB_USERNAME=kbookuser
6: DB_PASSWORD=Advocacy@Kbook2026
7: SERVER_PORT=8081
8: SERVER_FORWARD_HEADERS_STRATEGY=framework
9: SPRINGDOC_API_DOCS_ENABLED=true
10: SPRINGDOC_SWAGGER_UI_ENABLED=true
11: JWT_SECRET=e3ff5f077839c1331b1d893a728246685cb7dba9e3a77bffe7d52eaccf660988
12: GOOGLE_CLIENT_ID=836086274000-csivf8msgpphl69hn6ftqdeb1mjceaak.apps.googleusercontent.com
13: WHATSAPP_META_ACCESS_TOKEN=EAA0zVBpZCWqsBPp19B7sBuO22oIbW2sjAYeiC0ZCZACJby061clwMIZBnMzOK0cxv6qFIsZC4XC7d1AXy8rX5z5ZBAUaJvxn6P1sJAo2PgFPs3ZBu6t7DlWVZBE6DzrSHDMm0zH0ZBfAxZAZCcesBVQB5VuLhd7dVxayJTugaTVGckaNWjA6UqZCgbVa2bIHMDa1WchFTAZDZD
14: WHATSAPP_META_PHONE_NUMBER_ID=1127209130467068
15: WHATSAPP_META_OTP_TEMPLATE_NAME=verify_code
16: CORS_ALLOWED_ORIGINS=https://kbook.iadv.cloud,http://localhost:4200
17: PAYMENT_CRYPTO_SECRET=20998be989860cfc84f2623acff3506dd66d9d20b02066fe48badf2fe3ca3cd5
18: APP_BASE_URL=https://kbook.iadv.cloud

(End of file - total 18 lines)
</content>
```

---

## Assistant (Build · Big Pickle · 3.6s)

_Thinking:_

Found it. The GOOGLE_CLIENT_ID on this server is `836086274000-csivf8msgpphl69hn6ftqdeb1mjceaak.apps.googleusercontent.com`.

The Google Client ID used on this server is:

```
836086274000-csivf8msgpphl69hn6ftqdeb1mjceaak.apps.googleusercontent.com
```

Set via the `GOOGLE_CLIENT_ID` environment variable in `/.env` (line 12). The same ID is also used in the web admin frontend (`environment.ts` / `environment.prod.ts`), Android `secrets.properties.example`, and `google-services.json`.

---

