# HTTP client writes

Every HTTP `POST`, `PUT`, `PATCH`, or `DELETE` requires a matching CSRF token and
cookie. This applies to local open mode, authenticated deployments, JSON bodies,
multipart photo uploads, and the opt-in HTTP AI harness. Normal role authorization
and the harness token still apply. Safe `GET` requests need no CSRF header.
Desktop, CLI, and MCP stdio calls use services directly and need no HTTP handshake.

Before a write, call `GET /api/auth/csrf` on the same server with the same cookie
jar used for the write. The public response has `Cache-Control: no-store` and sets
a host-only `XSRF-TOKEN` cookie with `HttpOnly` and `SameSite=Strict`. HTTPS requests
also receive `Secure`. Its JSON body contains:

```json
{"headerName":"X-XSRF-TOKEN","token":"<masked token>"}
```

Send the returned `token` in the returned `headerName`, together with that cookie.
Use the response token, rather than the raw cookie value: Spring Security's
default XOR handler masks the response token for BREACH protection. The cookie
is deliberately unavailable to browser JavaScript. See the official
[Spring Security CSRF documentation](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

For a browser client on the same origin:

```javascript
async function write(url, payload) {
  const tokenResponse = await fetch("/api/auth/csrf", {
    credentials: "same-origin", cache: "no-store"
  });
  if (!tokenResponse.ok) throw new Error("Token handshake failed");
  const csrf = await tokenResponse.json();
  return fetch(url, {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
    body: JSON.stringify(payload)
  });
}
```

For a Python machine client, an in-memory cookie jar carries the token cookie:

```python
import http.cookiejar
import json
import urllib.request

base_url = "http://127.0.0.1:8080"
client = urllib.request.build_opener(
    urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
auth_headers = {}  # Add the deployment's Authorization/harness headers as needed.

def write(path, payload, method="POST"):
    token_request = urllib.request.Request(
        base_url + "/api/auth/csrf", headers={**auth_headers, "Cache-Control": "no-store"})
    with client.open(token_request) as response:
        csrf = json.load(response)
    request = urllib.request.Request(
        base_url + path, method=method,
        data=json.dumps(payload).encode("utf-8"),
        headers={**auth_headers, "Content-Type": "application/json",
                 csrf["headerName"]: csrf["token"]})
    return client.open(request)
```

For multipart uploads, add the token header and let the HTTP library generate
the multipart content type and boundary. Keep authentication and CSRF tokens
out of URLs, saved drafts, offline queues, and logs. Fetch a fresh token immediately
before dispatching each write; do not cache the handshake response.

A `403` must be shown to the caller rather than automatically replaying a write.
For interrupted or uncertain writes, retain the original operation key or request
ID and use the endpoint's receipt/replay contract when the operator retries.
The worker obtains a new CSRF token before a queued operation is replayed and
keeps its original business operation key.
