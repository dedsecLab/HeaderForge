# HeaderForge — Burp Suite Extension

**Author: dedsecLab**

A powerful Burp Suite extension that injects custom HTTP headers into requests via session handling rules. Supports **multiple header profiles**, **per-scope targeting**, **conditional injection**, **automatic header replacement**, **settings persistence**, and more. Ideal for JWT Bearer tokens, API keys, CSRF tokens, and any custom auth scheme.

---

## Credits

> This extension is built upon the original **[Add Custom Header](https://github.com/PortSwigger/add-custom-header)** Burp extension by **Lorenzo Grespan**.
>
> All thanks and credit goes to the original author for creating the foundation that made this project possible. HeaderForge extends the original with multi-profile support, settings persistence, conditional injection, and more.

---

## Features

| Feature | Description |
|---------|-------------|
| **Multiple Header Profiles** | Define unlimited header configurations — each with its own name, header, value source, and conditions. All enabled profiles are evaluated on every request. |
| **Settings Persistence** | All settings are automatically saved to your Burp project file. They survive restarts and extension reloads — no more re-entering tokens every time. |
| **Per-Scope Rules** | Each profile has a URL regex scope. Only matching requests get the header injected. Leave blank to match all URLs. |
| **Conditional Injection** | Filter by HTTP method (e.g. `GET,POST`), or only inject when the header doesn't already exist. |
| **Header Replacement** | If the request already contains a header with the same name (e.g. `Authorization: Basic xyz`), it is automatically replaced with your configured value — no duplicate headers. |
| **Import / Export** | Export all profiles to a JSON file and import them on another machine or project. Great for team sharing. |
| **Request Log** | A dedicated "Log" tab shows every header injection in real time — timestamp, URL, method, profile, header value, and source. |
| **Context Menu** | Right-click any selected text in a request or response viewer → "Use as Custom Header Value" → pick a profile. The value is set instantly. |

---

## Quick Start

### Install the extension

 1. Open the **Extender** tab in Burp Suite
 2. Select **Extensions** → **Add**
 3. Pick the `burpHeaderForge.jar` file from this repository

> If you don't trust the pre-built JAR, see [Compilation from source](#compilation-from-source) below.

### Create a header profile

 1. Go to the **HeaderForge** tab that appears after installation
 2. The **Profiles** tab shows a table of header profiles (a "Default" profile is created on first run)
 3. Click **Add** to create a new profile, or edit the default one

### Configure a profile

Fill in the profile editor form:

| Field | Description | Example |
|-------|-------------|---------|
| **Profile Name** | A friendly name for this configuration | `JWT Auth` |
| **Header Name** | The HTTP header to inject | `Authorization` |
| **Value Prefix** | Static prefix prepended to the value (don't forget the trailing space if needed) | `Bearer ` |
| **Value Source** | Choose one of three modes (see below) | — |

#### Value source modes

- **Disabled** — this profile is skipped during injection
- **Hard-Coded Value** — use a static token you paste into the text area
- **Regular Expression** — extract a token from a macro response using a regex capture group

### Wire it into Burp's session handling

 1. Go to **Project Options → Sessions**
 2. Add a **Session Handling Rule**
 3. Name it (e.g. "Inject custom headers") and click **Add** → **Invoke a Burp Extension**
 4. Select **HeaderForge** from the list
 5. Set the **Scope** to the URLs you want the rule to apply to (or "Include all URLs" for testing)

You're now ready to go. Every matching request will have your configured headers injected.

---

## Feature Details

### Multiple Profiles

You can define as many profiles as you need. Each one injects a different header independently.

**Example setup:**

| Profile | Header | Mode |
|---------|--------|------|
| JWT Auth | `Authorization: Bearer <token>` | Hard-Coded |
| API Key | `X-API-Key: <key>` | Hard-Coded |
| CSRF Token | `X-CSRF-Token: <extracted>` | RegExp |

Use the buttons below the table:
- **Add** — create a new blank profile
- **Remove** — delete the selected profile
- **Duplicate** — copy the selected profile (useful for similar configurations)

### Per-Scope Rules

Each profile has a **Scope (URL regex)** field under the "Conditions" section.

- Leave it **blank** to match all URLs
- Enter a Java regex to restrict injection to matching URLs only

**Examples:**

| Pattern | Matches |
|---------|---------|
| `https://api\.example\.com/.*` | Only requests to api.example.com |
| `https?://.*\.internal\.net/.*` | Any request to *.internal.net |
| `/api/v2/.*` | Any URL containing /api/v2/ |

### Conditional Injection

Two additional conditions are available per profile:

- **HTTP Methods** — comma-separated list of methods to match. Leave blank for all.
  - Example: `GET,POST,PUT` — only inject on these methods
- **Only inject if header doesn't already exist** — checkbox. When enabled, the header is only added if the request doesn't already contain a header with that name.

### Header Replacement

If the request already contains a header with the same name as one of your profiles, **HeaderForge automatically replaces it** rather than adding a duplicate. The matching is **case-insensitive by header name**.

**Example:** If a request has `Authorization: Basic dXNlcjpwYXNz` and your profile injects `Authorization: Bearer eyJhbG...`, the old `Basic` header is removed and replaced with the `Bearer` header.

This is especially useful when:
- A browser or client sends default auth headers that you want to override
- You're switching between auth schemes (Basic → Bearer, etc.)
- Multiple tools in the session handling pipeline set the same header

### Using a Regular Expression with a Macro

This is useful when the token is dynamic (e.g. fetched from a login endpoint):

 1. In Burp, record a **macro** that sends a request to the endpoint returning the token (e.g. `POST /login`)
 2. In the session handling rule, add an action to **Run a macro** and select your macro
 3. Enable **"After running the macro, invoke a Burp extension action handler"** and select **HeaderForge**
 4. In the profile, set mode to **Regular Expression** and enter a regex with a capture group

**Example regex for a JSON response like `{"access_token":"eyJhbG...","token_type":"Bearer"}`:**

```
access_token":"(.*?)"
```

The first capture group `(.*?)` is used as the header value.

### Import / Export

Share configurations across team members or Burp projects:

- **Export JSON** — saves all profiles to a `.json` file
- **Import JSON** — loads profiles from a `.json` file (replaces current profiles)

**Example JSON format:**

```json
{
  "version": 1,
  "profiles": [
    {
      "name": "JWT Auth",
      "enabled": true,
      "headerName": "Authorization",
      "headerValuePrefix": "Bearer ",
      "mode": "hardcoded",
      "regexpPattern": "",
      "hardcodedValue": "eyJhbGciOiJIUzI1NiIs...",
      "scopePattern": "",
      "httpMethods": "",
      "onlyIfNotExists": false
    }
  ]
}
```

### Request Log

Switch to the **Log** tab to see a live feed of every header injection:

| Column | Description |
|--------|-------------|
| Time | Timestamp of the injection |
| Method | HTTP method (GET, POST, etc.) |
| URL | Full request URL |
| Profile | Which profile was triggered |
| Header | Header name injected |
| Value | Full header value (prefix + token) |
| Source | `hardcoded` or `regexp` |

Use the **Clear Log** button to reset. The log auto-trims to the last 500 entries.

### Context Menu (Right-Click)

 1. Select any text in a request or response viewer in Burp (e.g. a token value)
 2. Right-click → **Use as Custom Header Value**
 3. Pick the target profile from the submenu
 4. The selected text is set as the profile's hard-coded value, and the profile switches to hard-coded mode

This is the fastest way to grab a token from a response and start using it immediately.

---

## Testing with the Mock Server

A Flask mock server is included for testing:

```bash
FLASK_DEBUG=1 FLASK_APP=server.py flask run
```

This starts a server on `127.0.0.1:5000`:

- `POST /login` — returns a JSON response with an `access_token`
- `GET /stuff` — echoes back the Authorization header it received

---

## Compilation from Source

### Prerequisites
- Java JDK 8 or later
- The Burp Extender API JAR (either from Maven or exported from Burp)

### Option 1: Using Gradle (if installed)

```bash
gradle jar
```

The JAR will be in `build/libs/`.

### Option 2: Manual compilation

 1. Clone this repo

 2. Download the Burp Extender API (or export from Burp → Extender → APIs → Save interface files):

    ```bash
    mkdir lib
    curl -o lib/burp-extender-api-1.7.22.jar \
      https://repo1.maven.org/maven2/net/portswigger/burp/extender/burp-extender-api/1.7.22/burp-extender-api-1.7.22.jar
    ```

 3. Compile all source files:

    ```bash
    mkdir build
    javac -source 8 -target 8 \
      -cp lib/burp-extender-api-1.7.22.jar \
      -d build \
      burp/HeaderProfile.java \
      burp/LogEntry.java \
      burp/ProfileManager.java \
      burp/ProfileTableModel.java \
      burp/LogTableModel.java \
      burp/MainPanel.java \
      burp/BurpExtender.java \
      burp/BurpTab.java
    ```

 4. Create the JAR:

    ```bash
    jar cvf burpHeaderForge.jar -C build .
    ```

 5. Load `burpHeaderForge.jar` into Burp

---

## Source Files

| File | Description |
|------|-------------|
| `burp/BurpExtender.java` | Main extension entry-point — session handling, context menu, lifecycle |
| `burp/HeaderProfile.java` | Data model for a header injection profile |
| `burp/ProfileManager.java` | Profile CRUD, Burp project persistence, JSON import/export |
| `burp/ProfileTableModel.java` | Swing table model for the profile list |
| `burp/LogEntry.java` | Data model for a log entry |
| `burp/LogTableModel.java` | Swing table model for the injection log |
| `burp/MainPanel.java` | Main UI — tabbed panel with Profiles and Log tabs |
| `burp/BurpTab.java` | Legacy UI panel (kept for reference) |
| `server.py` | Flask mock server for testing |

---

## Tips

- **Reload the extension quickly:** Hold the `CTRL` key when selecting the checkbox in the Extensions tab
- **Profile order matters:** Profiles are evaluated top-to-bottom. If two profiles target the same header, the last one wins
- **Existing headers are replaced:** If the request already has a header with the same name, it's replaced — not duplicated
- **Scope + Methods + Exists:** All three conditions must pass for a header to be injected. Use them together for precise control
- **Share configs with your team:** Export profiles as JSON, commit to your repo, and teammates can import them

---

## License

See [LICENSE](LICENSE) for details.
