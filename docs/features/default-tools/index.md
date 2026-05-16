Description: Default Tools — 86 ready-to-call JavaScript tools across 5 source bundles, exposed through the built-in MCP server, OS-agnostic by design.

# Default Tools

Spring AI Playground ships with **86 default tools** spread across five JSON source bundles. They are ready to call the moment a model provider is connected — you do not need to author anything yourself to see agentic workflows work end-to-end. They also serve as editable references when you start writing your own tools.

The MCP server does not expose all of them by default — a **preset** decides the starting subset, and per-tool include / exclude rules layer on top. That preference lives in `<home>/spring-ai-playground/tool/save/default-tools-preference.json` and is editable from two surfaces (the desktop launcher's Default MCP Tools card, and Tool Studio's Tool MCP Server Setting drawer — full breakdown in [Tool Studio → Where preset choices live](../tool-studio.md#where-preset-choices-live)).

## Two ways to use these tools

### Expose and call

The simplest mode — pick a preset (or layer rules on top) and the built-in MCP server publishes that subset. The same tool inventory ends up reachable, but the two transports the server can run with serve different audiences:

- **Streamable HTTP** at `http://localhost:8282/mcp` — **always on**. The in-app **MCP Inspector** (MCP Server tab) and the in-app **Agentic Chat** use this transport exclusively, and so do external Streamable HTTP MCP clients (**Claude Code**, **Cursor**, **Claude Desktop** via `mcp-remote`).
- **STDIO** — **opt-in**, only useful when an external MCP client wants to host the process itself. Launch with the `mcp-stdio` Spring profile (Docker `-e SPRING_PROFILES_INCLUDE=mcp-stdio`, or `java -jar` with the same env var); the client then **spawns the playground as its child process** and talks JSON-RPC over its stdin/stdout. The server's lifetime is tied to that client process — when the client exits, the server exits with it. This is how Claude Desktop / Claude Code can host the built-in server with no HTTP at all. The in-app surfaces are unaffected — they keep talking Streamable HTTP whether or not the STDIO profile is layered in.

Before exposing to an external client, the in-app **MCP Inspector** is the practical place to verify each tool's contract — run the tool, look at its schema, prompts, and resources, all isolated from chat.

No JS authoring is required for any of this mode — pick a preset, expose, call.

- → [Tool Studio: Key Tool Studio Capabilities](../tool-studio.md#key-tool-studio-capabilities) — Tool MCP Server Setting drawer overview
- → [Tool Studio: Connect to the Built-in MCP Server](../tool-studio.md#connect-to-the-built-in-mcp-server) — wiring external clients over Streamable HTTP
- → [Getting Started: distribution channels and MCP transports](../../getting-started.md#how-distribution-channels-map-to-mcp-transports) — Docker container and fat-JAR launchers for STDIO mode
- → [MCP Server: MCP Inspector](../mcp-server.md#mcp-inspector) — exercise tools, resources, prompts before exposing externally
- → [Agentic Chat](../agentic-chat.md) — call them from a model conversation

### Author and compose

The deeper mode — each default tool's JS source is a working reference for the cross-bridged helpers (`fetch`, `safety.fs.*`, `safety.parser.*`, `crypto.subtle`, `console.log`). Open one in Tool Studio, use **Copy to New Tool** to fork it, tweak the action, hit **Test & Publish**. The moment Local Pass succeeds, your tweaked tool joins the same built-in MCP server — Agentic Chat and external clients see it without a restart.

- → [Tool Studio: Built-in JavaScript Helpers](../tool-studio.md#built-in-javascript-helpers) — the full helper surface
- → [Tool Studio: Local Pass — Test Before Publish](../tool-studio.md#local-pass-test-before-publish) — the publish gate
- → [Tutorial 1: Author a Tool](../../tutorials/1-author-tool.md) — first walkthrough

## End-to-end flow

```text
[ Default tool · Custom tool ]
            │
            ▼
   [ Author in Tool Studio ]
            │
            ▼
   [ Local Pass test  ✅ ] ──── No pass, no run — the gate
            │
            ▼
   [ Built-in MCP server ]
            │
            ├── Streamable HTTP  ·  http://localhost:8282/mcp
            │   server runs independently; clients connect by URL
            │       │
            │       ├──▶  [ MCP Inspector ]  (in-app — verify the contract)
            │       ├──▶  [ Agentic Chat ]   (in-app)
            │       └──▶  External HTTP clients — Claude Code · Cursor ·
            │             Claude Desktop (via mcp-remote) · any Streamable HTTP MCP client
            │
            └── STDIO  ·  process stdin/stdout JSON-RPC
                client spawns the playground as its child process;
                its lifetime is tied to the client — when the client exits, the server exits with it
                (opt-in: mcp-stdio profile — Docker -e SPRING_PROFILES_INCLUDE=mcp-stdio,
                 or java -jar with the same env var)
                     │
                     └──▶  External STDIO clients — Claude Desktop · Claude Code
                           configured to spawn the process · any other STDIO MCP client
```

The two transports differ in **who owns the server's lifetime**: in Streamable HTTP mode the playground is a long-running daemon and clients come and go by URL; in STDIO mode the MCP client launches the playground as a child process and the server dies with the client. The in-app Inspector and Agentic Chat always reach the server over Streamable HTTP — STDIO is purely for external clients that want to host the process themselves. The same flow applies whether you expose a default tool unchanged or compose new tools from default-tool patterns, and the Inspector is where you exercise each tool against its schema before pointing an external client at it.

## Why these are different from other MCP tools

Most MCP server implementations ship one **native binary per OS** (Python wheels, Node binaries, Go / Rust executables) and require the user to install a platform-matching build, often plus a toolchain (Python, Node, Cargo) to author new tools.

Spring AI Playground's tool runtime is **OS-agnostic by design**. One JVM artifact — distributed as a JAR, a Docker image, or an Electron-packaged desktop launcher — runs identically on macOS, Windows, and Linux. All 86 default tools are pure JavaScript executed through GraalVM Polyglot, and so is every tool you author. There is no per-OS build step, no native dependency, no toolchain on the user's machine.

Full mechanics — including how every cross-bridged helper rides on JVM stdlib so `/` vs `\`, TLS, parsers, and crypto behave identically across OSes — in [Tool Studio → Cross-platform by design](../tool-studio.md#cross-platform-by-design).

## Composition recipes

The reference pages list what's available; composition recipes show how to chain them into a useful new tool. Three walk-throughs are in [Tutorial 8: Default Tool Recipes](../../tutorials/8-default-tool-recipes.md):

- **Filesystem pipeline** — `listDir` → `grepFile` → `sliceFile` → `writeTextFile`, ending in a custom tool that summarises a chunked log directory.
- **GitHub release → AI summary** — `getGithubLatestRelease` → `openaiResponseGenerator`, ending in a tool that posts release-notes digests.
- **City name → hourly forecast** — `geocodeAddress` → `getOpenMeteoForecast`, ending in a tool that answers "is it raining tomorrow in *city*?" without hard-coded coordinates.

## Environment variables — short list

Some default tools depend on environment-backed secrets and stay inert until those are set. The full per-tool breakdown lives on each reference page; the most common are:

| Env var | Used by | Why |
|---|---|---|
| `OPENAI_API_KEY` | `openaiResponseGenerator` | OpenAI Responses API |
| `GOOGLE_API_KEY` + `GOOGLE_PSE_ID` | `googlePseSearch` | Google Programmable Search Engine |
| `SLACK_WEBHOOK_URL` | `sendSlackMessage` | Incoming Webhook |
| `TOOL_STUDIO_FS_BASE` | All [Filesystem](filesystem.md) tools | Per-app `safety.fs` root (defaults to `${user.home}`) |
| Various Korean provider keys | KR network tools — Naver, Kakao, KMA, data.go.kr | Provider-specific (per-page) |

Secrets are masked from `console.log` output by substring replacement when the full resolved value appears, and they are not committed to the tool spec — they resolve at runtime from the JVM environment.

→ [Tool Studio: Key Tool Studio Capabilities](../tool-studio.md#key-tool-studio-capabilities) — Static Variables, secret masking, and how env-backed values reach the JS action.


## Browse all 86 tools { #browse-all-tools }

Click a card to jump to its full reference (with the JS source pre-expanded) on the right sub-page — same UX as the **Tool MCP Server Setting** drawer in Tool Studio. Five reference pages organise the catalog by source bundle and concern: [Examples](examples.md) · [Utilities](utilities.md) · [Filesystem](filesystem.md) · [Global](global.md) · [Korea](korea.md).

**Filter modes**: pick a **Preset** for an exclusive view (just the tools in that preset, all other filters cleared); or combine a **search** keyword with one or more **Tag** / **Category** chips — search is AND, tag and category are OR (a card is shown when it matches *any* selected tag OR category and also matches the search keyword).

<div class="tool-directory" markdown>
<div class="tool-directory__preset">
<span class="tool-directory__chip-label">Preset</span> <button class="tool-directory__chip" data-group="preset" data-value="starter5" aria-pressed="false">Starter 5</button> <button class="tool-directory__chip" data-group="preset" data-value="dev-essentials" aria-pressed="false">Dev Essentials</button> <button class="tool-directory__chip" data-group="preset" data-value="korea-toolkit" aria-pressed="false">Korea Toolkit</button> <button class="tool-directory__chip" data-group="preset" data-value="file-toolkit" aria-pressed="false">File Toolkit</button> <button class="tool-directory__chip" data-group="preset" data-value="everything" aria-pressed="false">Everything</button>
</div>
<div class="tool-directory__controls">
<input type="search" class="tool-directory__search" placeholder="Search by name or description…" aria-label="Search tools">
<div class="tool-directory__chips">
<span class="tool-directory__chip-label">Tag</span> <button class="tool-directory__chip" data-group="tag" data-value="example" aria-pressed="false">example</button> <button class="tool-directory__chip" data-group="tag" data-value="finance" aria-pressed="false">finance</button> <button class="tool-directory__chip" data-group="tag" data-value="geo" aria-pressed="false">geo</button> <button class="tool-directory__chip" data-group="tag" data-value="github" aria-pressed="false">github</button> <button class="tool-directory__chip" data-group="tag" data-value="korea" aria-pressed="false">korea</button> <button class="tool-directory__chip" data-group="tag" data-value="pipeline" aria-pressed="false">pipeline</button> <button class="tool-directory__chip" data-group="tag" data-value="search" aria-pressed="false">search</button> <button class="tool-directory__chip" data-group="tag" data-value="util" aria-pressed="false">util</button> <button class="tool-directory__chip" data-group="tag" data-value="weather" aria-pressed="false">weather</button>
</div>
<div class="tool-directory__chips">
<span class="tool-directory__chip-label">Category</span> <button class="tool-directory__chip" data-group="category" data-value="ai" aria-pressed="false">AI</button> <button class="tool-directory__chip" data-group="category" data-value="crypto" aria-pressed="false">CRYPTO</button> <button class="tool-directory__chip" data-group="category" data-value="data" aria-pressed="false">DATA</button> <button class="tool-directory__chip" data-group="category" data-value="datetime" aria-pressed="false">DATETIME</button> <button class="tool-directory__chip" data-group="category" data-value="encoding" aria-pressed="false">ENCODING</button> <button class="tool-directory__chip" data-group="category" data-value="file" aria-pressed="false">FILE</button> <button class="tool-directory__chip" data-group="category" data-value="math" aria-pressed="false">MATH</button> <button class="tool-directory__chip" data-group="category" data-value="messaging" aria-pressed="false">MESSAGING</button> <button class="tool-directory__chip" data-group="category" data-value="productivity" aria-pressed="false">PRODUCTIVITY</button> <button class="tool-directory__chip" data-group="category" data-value="security" aria-pressed="false">SECURITY</button> <button class="tool-directory__chip" data-group="category" data-value="text" aria-pressed="false">TEXT</button> <button class="tool-directory__chip" data-group="category" data-value="web" aria-pressed="false">WEB</button>
</div>
</div>
<div class="tool-directory__count">Showing 86 of 86 tools</div>
<div class="tool-directory__list" markdown>

<div class="tcg-grid tcg-grid--directory" markdown>

<div class="tcg-card tcg-card--directory" data-name="extractpagecontent" data-desc="fetches a web page and extracts its main readable content + outbound links. uses the host-injected fetch (ssrf-defended in `strict` mode) and safety.parser.html for parsing." data-category="web" data-tags="example" data-preset="starter5" data-env="" markdown>
<a class="tcg-stretched-link" href="examples/#extractPageContent" aria-label="Open extractPageContent in Examples">extractPageContent</a>
<div class="tcg-name"><span class="tcg-name__text">extractPageContent</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-language-html5:</div>
<div class="tcg-type">web · example <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Fetches a web page and extracts its main readable content + outbound links. Uses the host-injected fetch (SSRF-defended in `strict` mode) and safety.parser.html for parsing.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `pageUrl`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Examples</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="getcurrenttime" data-desc="returns the current time in iso 8601 format. if the user specifies a city, country, or location, the agent should first map it to an iana time zone and supply it via the timezone parameter. if no time" data-category="datetime" data-tags="example,util" data-preset="starter5,dev-essentials,korea-toolkit,file-toolkit" data-env="" markdown>
<a class="tcg-stretched-link" href="examples/#getCurrentTime" aria-label="Open getCurrentTime in Examples">getCurrentTime</a>
<div class="tcg-name"><span class="tcg-name__text">getCurrentTime</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-clock-outline:</div>
<div class="tcg-type">datetime · example · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Returns the current time in ISO 8601 format. If the user specifies a city, country, or location, the agent should first map it to an IANA time zone and supply it via the timeZone parameter. If no time zone is provided, UTC is used.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `timeZone`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Examples</div>
</div>

<div class="tcg-card tcg-card--directory t-google" data-name="buildgooglecalendarcreatelink" data-desc="builds a google calendar &quot;add event&quot; url with prefilled fields." data-category="productivity" data-tags="example,util" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="examples/#buildGoogleCalendarCreateLink" aria-label="Open buildGoogleCalendarCreateLink in Examples">buildGoogleCalendarCreateLink</a>
<div class="tcg-name"><span class="tcg-name__text">buildGoogleCalendarCreateLink</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-googlecalendar:</div>
<div class="tcg-type">productivity · example · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Builds a Google Calendar "Add Event" URL with prefilled fields.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `title` · `start` · `end` · `details` · `location` · `timeZone`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Examples</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="getweather" data-desc="free public weather lookup via wttr.in (no api key). returns a small json summary: { location, tempc, humidity, windspeed, winddirection }." data-category="web" data-tags="example,weather" data-preset="starter5" data-env="" markdown>
<a class="tcg-stretched-link" href="examples/#getWeather" aria-label="Open getWeather in Examples">getWeather</a>
<div class="tcg-name"><span class="tcg-name__text">getWeather</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-weather-partly-cloudy:</div>
<div class="tcg-type">web · example · weather <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Free public weather lookup via wttr.in (no API key). Returns a small JSON summary: { location, tempC, humidity, windSpeed, windDirection }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `location`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Examples</div>
</div>

<div class="tcg-card tcg-card--directory t-google" data-name="googlepsesearch" data-desc="google programmable search engine query (custom search api). requires `google_api_key` and `google_pse_id` env vars." data-category="web" data-tags="example,search" data-preset="" data-env="GOOGLE_API_KEY,GOOGLE_PSE_ID" markdown>
<a class="tcg-stretched-link" href="examples/#googlePseSearch" aria-label="Open googlePseSearch in Examples">googlePseSearch</a>
<div class="tcg-name"><span class="tcg-name__text">googlePseSearch</span> <span class="cost">🔑 × 2</span></div>
<div class="tcg-art" markdown>:simple-google:</div>
<div class="tcg-type">web · example · search <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Google Programmable Search Engine query (Custom Search API). Requires `GOOGLE_API_KEY` and `GOOGLE_PSE_ID` env vars.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `query` · `resultNum` · `startPage`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `GOOGLE_API_KEY` · `GOOGLE_PSE_ID`</div>
</div>
<div class="tcg-page">→ Examples</div>
</div>

<div class="tcg-card tcg-card--directory t-openai" data-name="openairesponsegenerator" data-desc="calls openai's responses api. requires `openai_api_key` env var." data-category="ai" data-tags="example" data-preset="" data-env="OPENAI_API_KEY" markdown>
<a class="tcg-stretched-link" href="examples/#openaiResponseGenerator" aria-label="Open openaiResponseGenerator in Examples">openaiResponseGenerator</a>
<div class="tcg-name"><span class="tcg-name__text">openaiResponseGenerator</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:material-creation:</div>
<div class="tcg-type">ai · example <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Calls OpenAI's Responses API. Requires `OPENAI_API_KEY` env var.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `prompt` · `model`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `OPENAI_API_KEY`</div>
</div>
<div class="tcg-page">→ Examples</div>
</div>

<div class="tcg-card tcg-card--directory t-slack" data-name="sendslackmessage" data-desc="posts a text message to a slack channel via incoming webhook. requires `slack_webhook_url` env var (the full https://hooks.slack.com/services/... url)." data-category="messaging" data-tags="example" data-preset="" data-env="SLACK_WEBHOOK_URL" markdown>
<a class="tcg-stretched-link" href="examples/#sendSlackMessage" aria-label="Open sendSlackMessage in Examples">sendSlackMessage</a>
<div class="tcg-name"><span class="tcg-name__text">sendSlackMessage</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:material-pound-box-outline:</div>
<div class="tcg-type">messaging · example <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Posts a text message to a Slack channel via Incoming Webhook. Requires `SLACK_WEBHOOK_URL` env var (the full https://hooks.slack.com/services/... URL).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `SLACK_WEBHOOK_URL`</div>
</div>
<div class="tcg-page">→ Examples</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="timezoneconvert" data-desc="converts a moment in time between iana time zones. returns the same instant rendered in the target zone (iso with offset)." data-category="datetime" data-tags="util" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#timezoneConvert" aria-label="Open timezoneConvert in Utilities">timezoneConvert</a>
<div class="tcg-name"><span class="tcg-name__text">timezoneConvert</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-clock-alert-outline:</div>
<div class="tcg-type">datetime · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Converts a moment in time between IANA time zones. Returns the same instant rendered in the target zone (ISO with offset).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text` · `toTimeZone`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="datediff" data-desc="computes b - a in the requested unit (days|hours|minutes|seconds|milliseconds). returns a number which may be fractional." data-category="datetime" data-tags="util" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#dateDiff" aria-label="Open dateDiff in Utilities">dateDiff</a>
<div class="tcg-name"><span class="tcg-name__text">dateDiff</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-calendar-arrow-right:</div>
<div class="tcg-type">datetime · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Computes b - a in the requested unit (days|hours|minutes|seconds|milliseconds). Returns a number which may be fractional.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `a` · `b` · `unit`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="urlencode" data-desc="percent-encodes a string for use in a url component. equivalent to encodeuricomponent." data-category="text" data-tags="util" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#urlEncode" aria-label="Open urlEncode in Utilities">urlEncode</a>
<div class="tcg-name"><span class="tcg-name__text">urlEncode</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-link-variant:</div>
<div class="tcg-type">text · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Percent-encodes a string for use in a URL component. Equivalent to encodeURIComponent.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="datemath" data-desc="adds (or subtracts) a duration to a date and returns the resulting iso timestamp. unit: years|months|weeks|days|hours|minutes|seconds|milliseconds." data-category="datetime" data-tags="util" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#dateMath" aria-label="Open dateMath in Utilities">dateMath</a>
<div class="tcg-name"><span class="tcg-name__text">dateMath</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-calendar-clock:</div>
<div class="tcg-type">datetime · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Adds (or subtracts) a duration to a date and returns the resulting ISO timestamp. Unit: years|months|weeks|days|hours|minutes|seconds|milliseconds.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text` · `amount` · `unit`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="parsedate" data-desc="parses a date/time string (iso 8601 or rfc 2822) and returns its components plus epochmillis." data-category="datetime" data-tags="util" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#parseDate" aria-label="Open parseDate in Utilities">parseDate</a>
<div class="tcg-name"><span class="tcg-name__text">parseDate</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-calendar-import:</div>
<div class="tcg-type">datetime · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Parses a date/time string (ISO 8601 or RFC 2822) and returns its components plus epochMillis.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text` · `timeZone`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="cronnext" data-desc="computes the next datetime matching a standard 5-field cron expression (minute hour day month weekday). supports * , - / and ? (treated as *). returns iso timestamp in utc." data-category="datetime" data-tags="util" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#cronNext" aria-label="Open cronNext in Utilities">cronNext</a>
<div class="tcg-name"><span class="tcg-name__text">cronNext</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-timer-cog-outline:</div>
<div class="tcg-type">datetime · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Computes the next datetime matching a standard 5-field cron expression (minute hour day month weekday). Supports * , - / and ? (treated as *). Returns ISO timestamp in UTC.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `expression` · `from` · `count`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="difftext" data-desc="returns a line-by-line diff between two texts. each entry is {op, line} where op is one of '=' (unchanged), '-' (only in a), '+' (only in b)." data-category="text" data-tags="util" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#diffText" aria-label="Open diffText in Utilities">diffText</a>
<div class="tcg-name"><span class="tcg-name__text">diffText</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-file-compare:</div>
<div class="tcg-type">text · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Returns a line-by-line diff between two texts. Each entry is {op, line} where op is one of '=' (unchanged), '-' (only in a), '+' (only in b).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `a` · `b`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="sortlines" data-desc="sorts lines of text alphabetically. supports reverse and case-insensitive options." data-category="text" data-tags="util" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#sortLines" aria-label="Open sortLines in Utilities">sortLines</a>
<div class="tcg-name"><span class="tcg-name__text">sortLines</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-sort-alphabetical-ascending:</div>
<div class="tcg-type">text · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Sorts lines of text alphabetically. Supports reverse and case-insensitive options.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text` · `reverse` · `caseInsensitive`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="piidetect" data-desc="scans text for personally identifiable information patterns (email, us ssn, us phone, credit card, ipv4). returns an array of {type, masked, index}." data-category="security" data-tags="util" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#piiDetect" aria-label="Open piiDetect in Utilities">piiDetect</a>
<div class="tcg-name"><span class="tcg-name__text">piiDetect</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-shield-account-outline:</div>
<div class="tcg-type">security · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Scans text for personally identifiable information patterns (email, US SSN, US phone, credit card, IPv4). Returns an array of {type, masked, index}.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="regexextract" data-desc="returns all regex matches in the input. with the 'g' flag every match is returned; without it the first match (with capture groups) is returned." data-category="text" data-tags="util" data-preset="dev-essentials" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#regexExtract" aria-label="Open regexExtract in Utilities">regexExtract</a>
<div class="tcg-name"><span class="tcg-name__text">regexExtract</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-regex:</div>
<div class="tcg-type">text · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Returns all regex matches in the input. With the 'g' flag every match is returned; without it the first match (with capture groups) is returned.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text` · `pattern` · `flags`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="formatdate" data-desc="formats a date (iso string or epoch ms) using a pattern with tokens yyyy/mm/dd hh:mm:ss sss. time zone aware." data-category="datetime" data-tags="util" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#formatDate" aria-label="Open formatDate in Utilities">formatDate</a>
<div class="tcg-name"><span class="tcg-name__text">formatDate</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-calendar-text-outline:</div>
<div class="tcg-type">datetime · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Formats a date (ISO string or epoch ms) using a pattern with tokens yyyy/MM/dd HH:mm:ss SSS. Time zone aware.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text` · `pattern` · `timeZone`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="stats" data-desc="returns summary statistics (count, sum, min, max, mean, median, stddev) for an array of numbers." data-category="math" data-tags="util" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#stats" aria-label="Open stats in Utilities">stats</a>
<div class="tcg-name"><span class="tcg-name__text">stats</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-sigma:</div>
<div class="tcg-type">math · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Returns summary statistics (count, sum, min, max, mean, median, stddev) for an array of numbers.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `numbers`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="secretpatterndetect" data-desc="scans text for well-known secret patterns (aws keys, github tokens, slack tokens, openai keys, stripe keys, private keys). returns an array of {type, masked, index}." data-category="security" data-tags="util" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#secretPatternDetect" aria-label="Open secretPatternDetect in Utilities">secretPatternDetect</a>
<div class="tcg-name"><span class="tcg-name__text">secretPatternDetect</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-key-alert-outline:</div>
<div class="tcg-type">security · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Scans text for well-known secret patterns (AWS keys, GitHub tokens, Slack tokens, OpenAI keys, Stripe keys, private keys). Returns an array of {type, masked, index}.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="regexreplace" data-desc="replaces regex matches in the input with the given replacement string. supports $1, $2 group back-references." data-category="text" data-tags="util" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#regexReplace" aria-label="Open regexReplace in Utilities">regexReplace</a>
<div class="tcg-name"><span class="tcg-name__text">regexReplace</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-find-replace:</div>
<div class="tcg-type">text · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Replaces regex matches in the input with the given replacement string. Supports $1, $2 group back-references.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text` · `pattern` · `replacement` · `flags`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="evalexpression" data-desc="evaluates a safe arithmetic/logical expression (no eval, no host access). supports + - * / % ** parens, &&, ||, !, ==, !=, <, <=, >, >=, and numeric/string/boolean literals plus variables from the var" data-category="math" data-tags="util" data-preset="starter5,dev-essentials,korea-toolkit,file-toolkit" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#evalExpression" aria-label="Open evalExpression in Utilities">evalExpression</a>
<div class="tcg-name"><span class="tcg-name__text">evalExpression</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-calculator-variant-outline:</div>
<div class="tcg-type">math · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Evaluates a safe arithmetic/logical expression (no eval, no host access). Supports + - * / % ** parens, &&, ||, !, ==, !=, <, <=, >, >=, and numeric/string/boolean literals plus variables from the variables object.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `expression` · `variables`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="formatcsv" data-desc="serialises an array of rows into csv text. rows may be arrays (use header param) or objects (keys become the header). rfc 4180 quoting." data-category="data" data-tags="util" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#formatCsv" aria-label="Open formatCsv in Utilities">formatCsv</a>
<div class="tcg-name"><span class="tcg-name__text">formatCsv</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-file-table-outline:</div>
<div class="tcg-type">data · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Serialises an array of rows into CSV text. Rows may be arrays (use header param) or objects (keys become the header). RFC 4180 quoting.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `rows` · `header` · `delimiter`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="base64" data-desc="encodes utf-8 text to base64, or decodes base64 back to utf-8 text. use mode='encode' (default) or 'decode'. url-safe variant via urlsafe=true." data-category="encoding" data-tags="util" data-preset="dev-essentials" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#base64" aria-label="Open base64 in Utilities">base64</a>
<div class="tcg-name"><span class="tcg-name__text">base64</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-code-tags:</div>
<div class="tcg-type">encoding · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Encodes UTF-8 text to base64, or decodes base64 back to UTF-8 text. Use mode='encode' (default) or 'decode'. URL-safe variant via urlSafe=true.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text` · `mode` · `urlSafe`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="hex" data-desc="encodes utf-8 text to hex string, or decodes hex back to utf-8 text. use mode='encode' (default) or 'decode'." data-category="encoding" data-tags="util" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#hex" aria-label="Open hex in Utilities">hex</a>
<div class="tcg-name"><span class="tcg-name__text">hex</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-numeric:</div>
<div class="tcg-type">encoding · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Encodes UTF-8 text to hex string, or decodes hex back to UTF-8 text. Use mode='encode' (default) or 'decode'.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text` · `mode` · `upperCase`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="uuid" data-desc="generates a cryptographically random uuid v4 string." data-category="crypto" data-tags="util" data-preset="dev-essentials" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#uuid" aria-label="Open uuid in Utilities">uuid</a>
<div class="tcg-name"><span class="tcg-name__text">uuid</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-identifier:</div>
<div class="tcg-type">crypto · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Generates a cryptographically random UUID v4 string.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; —</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="hash" data-desc="computes the cryptographic hash of utf-8 text. algorithms: sha-256 (default), sha-384, sha-512. returns lowercase hex." data-category="crypto" data-tags="util" data-preset="dev-essentials" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#hash" aria-label="Open hash in Utilities">hash</a>
<div class="tcg-name"><span class="tcg-name__text">hash</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-pound:</div>
<div class="tcg-type">crypto · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Computes the cryptographic hash of UTF-8 text. Algorithms: SHA-256 (default), SHA-384, SHA-512. Returns lowercase hex.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text` · `algorithm`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="hmac" data-desc="computes an hmac signature over utf-8 text using a secret. algorithms: sha-256 (default), sha-384, sha-512. returns lowercase hex." data-category="crypto" data-tags="util" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#hmac" aria-label="Open hmac in Utilities">hmac</a>
<div class="tcg-name"><span class="tcg-name__text">hmac</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-key-variant:</div>
<div class="tcg-type">crypto · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Computes an HMAC signature over UTF-8 text using a secret. Algorithms: SHA-256 (default), SHA-384, SHA-512. Returns lowercase hex.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `secret` · `text` · `algorithm`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="securerandom" data-desc="generates cryptographically secure random bytes. encoding: 'hex' (default), 'base64', or 'base64url'." data-category="crypto" data-tags="util" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#secureRandom" aria-label="Open secureRandom in Utilities">secureRandom</a>
<div class="tcg-name"><span class="tcg-name__text">secureRandom</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-dice-6-outline:</div>
<div class="tcg-type">crypto · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Generates cryptographically secure random bytes. encoding: 'hex' (default), 'base64', or 'base64url'.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `bytes` · `encoding`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="passwordgenerate" data-desc="generates a strong random password from selected character classes. uses crypto.getrandomvalues for unbiased selection." data-category="crypto" data-tags="util" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#passwordGenerate" aria-label="Open passwordGenerate in Utilities">passwordGenerate</a>
<div class="tcg-name"><span class="tcg-name__text">passwordGenerate</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-form-textbox-password:</div>
<div class="tcg-type">crypto · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Generates a strong random password from selected character classes. Uses crypto.getRandomValues for unbiased selection.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `length` · `includeLowercase` · `includeUppercase` · `includeDigits` · `includeSymbols`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="jwtdecode" data-desc="decodes a jwt without verifying its signature. returns the header and payload as json, plus signaturepresent." data-category="crypto" data-tags="util" data-preset="dev-essentials" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#jwtDecode" aria-label="Open jwtDecode in Utilities">jwtDecode</a>
<div class="tcg-name"><span class="tcg-name__text">jwtDecode</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-shield-key-outline:</div>
<div class="tcg-type">crypto · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Decodes a JWT without verifying its signature. Returns the header and payload as JSON, plus signaturePresent.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `token`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="jwtverify" data-desc="verifies a hs256/hs384/hs512 jwt signature using a shared secret and returns the decoded payload on success. also checks exp and nbf claims when present." data-category="crypto" data-tags="util" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#jwtVerify" aria-label="Open jwtVerify in Utilities">jwtVerify</a>
<div class="tcg-name"><span class="tcg-name__text">jwtVerify</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-shield-check-outline:</div>
<div class="tcg-type">crypto · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Verifies a HS256/HS384/HS512 JWT signature using a shared secret and returns the decoded payload on success. Also checks exp and nbf claims when present.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `token` · `secret`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="parsecsv" data-desc="parses csv text into an array of rows. if header=true, each row is an object keyed by the first row." data-category="data" data-tags="util" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="utilities/#parseCsv" aria-label="Open parseCsv in Utilities">parseCsv</a>
<div class="tcg-name"><span class="tcg-name__text">parseCsv</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-file-import-outline:</div>
<div class="tcg-type">data · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Parses CSV text into an array of rows. If header=true, each row is an object keyed by the first row.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text` · `header` · `delimiter`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Utilities</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="readtextfile" data-desc="reads a utf-8 text file from disk and returns its contents as a single string." data-category="file" data-tags="pipeline" data-preset="file-toolkit" data-env="" markdown>
<a class="tcg-stretched-link" href="filesystem/#readTextFile" aria-label="Open readTextFile in Filesystem">readTextFile</a>
<div class="tcg-name"><span class="tcg-name__text">readTextFile</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-file-document-outline:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Reads a UTF-8 text file from disk and returns its contents as a single string.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `path`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Filesystem</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="listdir" data-desc="lists the immediate entries (files and subdirectories) of a directory under the fs base path. returns an array of relative names (not full paths). uses safety.fs.list()." data-category="file" data-tags="pipeline" data-preset="file-toolkit" data-env="" markdown>
<a class="tcg-stretched-link" href="filesystem/#listDir" aria-label="Open listDir in Filesystem">listDir</a>
<div class="tcg-name"><span class="tcg-name__text">listDir</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-folder-outline:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Lists the immediate entries (files and subdirectories) of a directory under the FS base path. Returns an array of relative names (not full paths). Uses safety.fs.list().
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `dir`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Filesystem</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="statfile" data-desc="returns size, last-modified timestamp, and a directory flag for a path inside the fs base. uses safety.fs.stat()." data-category="file" data-tags="pipeline" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="filesystem/#statFile" aria-label="Open statFile in Filesystem">statFile</a>
<div class="tcg-name"><span class="tcg-name__text">statFile</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-information-outline:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Returns size, last-modified timestamp, and a directory flag for a path inside the FS base. Uses safety.fs.stat().
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `path`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Filesystem</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="linecount" data-desc="counts the lines in a utf-8 text file. uses safety.fs.linecount()." data-category="file" data-tags="pipeline" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="filesystem/#lineCount" aria-label="Open lineCount in Filesystem">lineCount</a>
<div class="tcg-name"><span class="tcg-name__text">lineCount</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-counter:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Counts the lines in a UTF-8 text file. Uses safety.fs.lineCount().
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `path`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Filesystem</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="slicefile" data-desc="returns a slice of lines from a utf-8 text file (head / tail / range). `start` is 0-based inclusive, `end` is 0-based exclusive (python-style slice). negative values count from the end of the file. us" data-category="file" data-tags="pipeline" data-preset="file-toolkit" data-env="" markdown>
<a class="tcg-stretched-link" href="filesystem/#sliceFile" aria-label="Open sliceFile in Filesystem">sliceFile</a>
<div class="tcg-name"><span class="tcg-name__text">sliceFile</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-content-cut:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Returns a slice of lines from a UTF-8 text file (head / tail / range). `start` is 0-based inclusive, `end` is 0-based exclusive (Python-style slice). Negative values count from the end of the file. Uses safety.fs.slice().
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `path` · `start` · `end`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Filesystem</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="sortfile" data-desc="sorts the lines of a utf-8 text file and returns the sorted lines as an array. options: reverse / numeric / caseinsensitive / unique. uses safety.fs.sort()." data-category="file" data-tags="pipeline" data-preset="file-toolkit" data-env="" markdown>
<a class="tcg-stretched-link" href="filesystem/#sortFile" aria-label="Open sortFile in Filesystem">sortFile</a>
<div class="tcg-name"><span class="tcg-name__text">sortFile</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-sort:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Sorts the lines of a UTF-8 text file and returns the sorted lines as an array. Options: reverse / numeric / caseInsensitive / unique. Uses safety.fs.sort().
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `path` · `reverse` · `numeric` · `caseInsensitive` · `unique`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Filesystem</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="grepfile" data-desc="searches a utf-8 text file for lines matching a javascript regex. returns an array of matching lines (optionally numbered). uses safety.fs.grep()." data-category="file" data-tags="pipeline" data-preset="file-toolkit" data-env="" markdown>
<a class="tcg-stretched-link" href="filesystem/#grepFile" aria-label="Open grepFile in Filesystem">grepFile</a>
<div class="tcg-name"><span class="tcg-name__text">grepFile</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-file-find-outline:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Searches a UTF-8 text file for lines matching a JavaScript regex. Returns an array of matching lines (optionally numbered). Uses safety.fs.grep().
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `pattern` · `path` · `caseInsensitive` · `numbered` · `limit`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Filesystem</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="findfiles" data-desc="recursively finds files matching a glob inside a directory. glob supports `*` and `?`. optional max recursion depth and type filter ('file' or 'dir'). uses safety.fs.find()." data-category="file" data-tags="pipeline" data-preset="file-toolkit" data-env="" markdown>
<a class="tcg-stretched-link" href="filesystem/#findFiles" aria-label="Open findFiles in Filesystem">findFiles</a>
<div class="tcg-name"><span class="tcg-name__text">findFiles</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-folder-search-outline:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Recursively finds files matching a glob inside a directory. Glob supports `*` and `?`. Optional max recursion depth and type filter ('file' or 'dir'). Uses safety.fs.find().
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `dir` · `glob` · `maxDepth` · `type`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Filesystem</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="cutfilefields" data-desc="extracts selected fields from each line of a delimited file (csv/tsv/etc.). uses safety.fs.cut(). 1-based field numbers, comma-separated alternatives via the array." data-category="file" data-tags="pipeline" data-preset="file-toolkit" data-env="" markdown>
<a class="tcg-stretched-link" href="filesystem/#cutFileFields" aria-label="Open cutFileFields in Filesystem">cutFileFields</a>
<div class="tcg-name"><span class="tcg-name__text">cutFileFields</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-table-column:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Extracts selected fields from each line of a delimited file (CSV/TSV/etc.). Uses safety.fs.cut(). 1-based field numbers, comma-separated alternatives via the array.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `path` · `fields` · `delimiter` · `regex`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Filesystem</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="writetextfile" data-desc="writes a utf-8 text file inside the fs base path (creating parent directories as needed). overwrites any existing file. requires `filewrite` permission on the sandbox." data-category="file" data-tags="pipeline" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="filesystem/#writeTextFile" aria-label="Open writeTextFile in Filesystem">writeTextFile</a>
<div class="tcg-name"><span class="tcg-name__text">writeTextFile</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-file-edit-outline:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l4">L4</span></div>
<div class="tcg-body" markdown>
Writes a UTF-8 text file inside the FS base path (creating parent directories as needed). Overwrites any existing file. Requires `fileWrite` permission on the sandbox.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `path` · `content`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Filesystem</div>
</div>

<div class="tcg-card tcg-card--directory t-github" data-name="getgithubrepo" data-desc="fetches public metadata for a github repository (no authentication needed; subject to github's 60 requests/hour anonymous rate limit)." data-category="web" data-tags="github" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="global/#getGithubRepo" aria-label="Open getGithubRepo in Global">getGithubRepo</a>
<div class="tcg-name"><span class="tcg-name__text">getGithubRepo</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-github:</div>
<div class="tcg-type">web · github <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Fetches public metadata for a GitHub repository (no authentication needed; subject to GitHub's 60 requests/hour anonymous rate limit).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `owner` · `repo`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Global</div>
</div>

<div class="tcg-card tcg-card--directory t-wiki" data-name="searchwikipedia" data-desc="looks up a wikipedia page summary by title. no authentication required. uses the public rest api at en.wikipedia.org/api/rest_v1/page/summary." data-category="web" data-tags="search" data-preset="starter5" data-env="" markdown>
<a class="tcg-stretched-link" href="global/#searchWikipedia" aria-label="Open searchWikipedia in Global">searchWikipedia</a>
<div class="tcg-name"><span class="tcg-name__text">searchWikipedia</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-wikipedia:</div>
<div class="tcg-type">web · search <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Looks up a Wikipedia page summary by title. No authentication required. Uses the public REST API at en.wikipedia.org/api/rest_v1/page/summary.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `title` · `lang`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Global</div>
</div>

<div class="tcg-card tcg-card--directory t-hn" data-name="searchhackernews" data-desc="searches hacker news stories via the public algolia hn search api (no auth needed)." data-category="web" data-tags="search" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="global/#searchHackerNews" aria-label="Open searchHackerNews in Global">searchHackerNews</a>
<div class="tcg-name"><span class="tcg-name__text">searchHackerNews</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-ycombinator:</div>
<div class="tcg-type">web · search <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Searches Hacker News stories via the public Algolia HN Search API (no auth needed).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `query` · `hits` · `tag`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Global</div>
</div>

<div class="tcg-card tcg-card--directory t-stack" data-name="searchstackoverflow" data-desc="searches stack overflow questions via the public stack exchange api (anonymous, capped at 300 requests / ip / day)." data-category="web" data-tags="search" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="global/#searchStackOverflow" aria-label="Open searchStackOverflow in Global">searchStackOverflow</a>
<div class="tcg-name"><span class="tcg-name__text">searchStackOverflow</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-stackoverflow:</div>
<div class="tcg-type">web · search <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Searches Stack Overflow questions via the public Stack Exchange API (anonymous, capped at 300 requests / IP / day).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `query` · `pageSize` · `sort` · `tags`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Global</div>
</div>

<div class="tcg-card tcg-card--directory t-github" data-name="getgithubuser" data-desc="fetches public profile information for a github user or organisation (no auth — 60 req/h anonymous)." data-category="web" data-tags="github" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="global/#getGithubUser" aria-label="Open getGithubUser in Global">getGithubUser</a>
<div class="tcg-name"><span class="tcg-name__text">getGithubUser</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-github:</div>
<div class="tcg-type">web · github <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Fetches public profile information for a GitHub user or organisation (no auth — 60 req/h anonymous).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `login`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Global</div>
</div>

<div class="tcg-card tcg-card--directory t-github" data-name="listgithubrepoissues" data-desc="lists issues on a public github repository (no auth). excludes pull requests by default. anonymous quota 60 req/h." data-category="web" data-tags="github" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="global/#listGithubRepoIssues" aria-label="Open listGithubRepoIssues in Global">listGithubRepoIssues</a>
<div class="tcg-name"><span class="tcg-name__text">listGithubRepoIssues</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-github:</div>
<div class="tcg-type">web · github <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Lists issues on a public GitHub repository (no auth). Excludes pull requests by default. Anonymous quota 60 req/h.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `owner` · `repo` · `state` · `perPage` · `page`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Global</div>
</div>

<div class="tcg-card tcg-card--directory t-github" data-name="listgithubreporeleases" data-desc="lists releases on a public github repository (no auth)." data-category="web" data-tags="github" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="global/#listGithubRepoReleases" aria-label="Open listGithubRepoReleases in Global">listGithubRepoReleases</a>
<div class="tcg-name"><span class="tcg-name__text">listGithubRepoReleases</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-github:</div>
<div class="tcg-type">web · github <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Lists releases on a public GitHub repository (no auth).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `owner` · `repo` · `perPage`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Global</div>
</div>

<div class="tcg-card tcg-card--directory t-github" data-name="getgithublatestrelease" data-desc="fetches the latest non-draft, non-prerelease release of a public github repository (no auth)." data-category="web" data-tags="github" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="global/#getGithubLatestRelease" aria-label="Open getGithubLatestRelease in Global">getGithubLatestRelease</a>
<div class="tcg-name"><span class="tcg-name__text">getGithubLatestRelease</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-github:</div>
<div class="tcg-type">web · github <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Fetches the latest non-draft, non-prerelease release of a public GitHub repository (no auth).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `owner` · `repo`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Global</div>
</div>

<div class="tcg-card tcg-card--directory t-github" data-name="getgithubfilecontent" data-desc="fetches the raw text content of a file from a public github repository (no auth)." data-category="web" data-tags="github" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="global/#getGithubFileContent" aria-label="Open getGithubFileContent in Global">getGithubFileContent</a>
<div class="tcg-name"><span class="tcg-name__text">getGithubFileContent</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-github:</div>
<div class="tcg-type">web · github <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Fetches the raw text content of a file from a public GitHub repository (no auth).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `owner` · `repo` · `path` · `ref`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Global</div>
</div>

<div class="tcg-card tcg-card--directory t-github" data-name="searchgithubrepos" data-desc="searches public github repositories by query (no auth — anonymous limit 10 requests/minute)." data-category="web" data-tags="github,search" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="global/#searchGithubRepos" aria-label="Open searchGithubRepos in Global">searchGithubRepos</a>
<div class="tcg-name"><span class="tcg-name__text">searchGithubRepos</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-github:</div>
<div class="tcg-type">web · github · search <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Searches public GitHub repositories by query (no auth — anonymous limit 10 requests/minute).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `query` · `sort` · `perPage`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Global</div>
</div>

<div class="tcg-card tcg-card--directory t-github" data-name="listgithubrepocontributors" data-desc="lists top contributors to a public github repository (no auth)." data-category="web" data-tags="github" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="global/#listGithubRepoContributors" aria-label="Open listGithubRepoContributors in Global">listGithubRepoContributors</a>
<div class="tcg-name"><span class="tcg-name__text">listGithubRepoContributors</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-github:</div>
<div class="tcg-type">web · github <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Lists top contributors to a public GitHub repository (no auth).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `owner` · `repo` · `perPage`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Global</div>
</div>

<div class="tcg-card tcg-card--directory t-crypto" data-name="getcryptoprice" data-desc="fetches current crypto prices from coingecko's public simple price api (no auth, generous rate limit). pass coin ids like 'bitcoin,ethereum' and currency ids like 'usd,krw'." data-category="web" data-tags="finance" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="global/#getCryptoPrice" aria-label="Open getCryptoPrice in Global">getCryptoPrice</a>
<div class="tcg-name"><span class="tcg-name__text">getCryptoPrice</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-currency-btc:</div>
<div class="tcg-type">web · finance <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Fetches current crypto prices from CoinGecko's public Simple Price API (no auth, generous rate limit). Pass coin ids like 'bitcoin,ethereum' and currency ids like 'usd,krw'.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `ids` · `currencies`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Global</div>
</div>

<div class="tcg-card tcg-card--directory t-currency" data-name="convertcurrency" data-desc="converts between fiat currencies using exchangerate.host (no key, no rate limit listed)." data-category="web" data-tags="finance" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="global/#convertCurrency" aria-label="Open convertCurrency in Global">convertCurrency</a>
<div class="tcg-name"><span class="tcg-name__text">convertCurrency</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-currency-usd:</div>
<div class="tcg-type">web · finance <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Converts between fiat currencies using exchangerate.host (no key, no rate limit listed).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `from` · `to` · `amount`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Global</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="getipinfo" data-desc="returns geolocation and asn info for an ip address (or the caller's ip if `ip` is omitted) via ipapi.co (no auth, 1000 req/day)." data-category="web" data-tags="geo" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="global/#getIpInfo" aria-label="Open getIpInfo in Global">getIpInfo</a>
<div class="tcg-name"><span class="tcg-name__text">getIpInfo</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-ip-network-outline:</div>
<div class="tcg-type">web · geo <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Returns geolocation and ASN info for an IP address (or the caller's IP if `ip` is omitted) via ipapi.co (no auth, 1000 req/day).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `ip`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Global</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="getcountryinfo" data-desc="fetches country information from restcountries.com (no auth) by partial or full name." data-category="web" data-tags="geo" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="global/#getCountryInfo" aria-label="Open getCountryInfo in Global">getCountryInfo</a>
<div class="tcg-name"><span class="tcg-name__text">getCountryInfo</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-earth:</div>
<div class="tcg-type">web · geo <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Fetches country information from restcountries.com (no auth) by partial or full name.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `name`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Global</div>
</div>

<div class="tcg-card tcg-card--directory t-arxiv" data-name="searcharxiv" data-desc="searches arxiv preprints via the public atom-feed api (no auth). results are parsed from xml." data-category="web" data-tags="search" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="global/#searchArxiv" aria-label="Open searchArxiv in Global">searchArxiv</a>
<div class="tcg-name"><span class="tcg-name__text">searchArxiv</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-arxiv:</div>
<div class="tcg-type">web · search <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Searches arXiv preprints via the public Atom-feed API (no auth). Results are parsed from XML.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `query` · `max` · `sortBy`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Global</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="getpublicholidays" data-desc="returns public holidays for a given country and year via nager.date (no auth)." data-category="web" data-tags="" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="global/#getPublicHolidays" aria-label="Open getPublicHolidays in Global">getPublicHolidays</a>
<div class="tcg-name"><span class="tcg-name__text">getPublicHolidays</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-calendar-star-outline:</div>
<div class="tcg-type">web <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Returns public holidays for a given country and year via Nager.Date (no auth).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `year` · `countryCode`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Global</div>
</div>

<div class="tcg-card tcg-card--directory t-reddit" data-name="searchreddit" data-desc="searches a public subreddit via reddit's json api (no auth, but rate-limited and user-agent required)." data-category="web" data-tags="search" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="global/#searchReddit" aria-label="Open searchReddit in Global">searchReddit</a>
<div class="tcg-name"><span class="tcg-name__text">searchReddit</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-reddit:</div>
<div class="tcg-type">web · search <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Searches a public subreddit via Reddit's JSON API (no auth, but rate-limited and User-Agent required).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `subreddit` · `query` · `limit` · `sort`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Global</div>
</div>

<div class="tcg-card tcg-card--directory t-meteo" data-name="getopenmeteoforecast" data-desc="fetches a multi-day weather forecast from open-meteo (no auth, 10k req/day for non-commercial). open-meteo serves official ecmwf/gfs/icon model output — far richer than wttr.in but requires lat/lon (u" data-category="web" data-tags="weather" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="global/#getOpenMeteoForecast" aria-label="Open getOpenMeteoForecast in Global">getOpenMeteoForecast</a>
<div class="tcg-name"><span class="tcg-name__text">getOpenMeteoForecast</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-weather-cloudy-clock:</div>
<div class="tcg-type">web · weather <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Fetches a multi-day weather forecast from Open-Meteo (no auth, 10k req/day for non-commercial). Open-Meteo serves official ECMWF/GFS/ICON model output — far richer than wttr.in but requires lat/lon (use `geocodeAddress` first if you only have a city name).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `latitude` · `longitude` · `days` · `timezone`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Global</div>
</div>

<div class="tcg-card tcg-card--directory t-osm" data-name="geocodeaddress" data-desc="forward-geocodes a free-form address to coordinates via openstreetmap nominatim (no key). nominatim's usage policy requires a descriptive user-agent and at most 1 req/s — we set both." data-category="web" data-tags="geo" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="global/#geocodeAddress" aria-label="Open geocodeAddress in Global">geocodeAddress</a>
<div class="tcg-name"><span class="tcg-name__text">geocodeAddress</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-openstreetmap:</div>
<div class="tcg-type">web · geo <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Forward-geocodes a free-form address to coordinates via OpenStreetMap Nominatim (no key). Nominatim's usage policy requires a descriptive User-Agent and at most 1 req/s — we set both.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `address` · `limit`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Global</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="getsunrisesunset" data-desc="returns sunrise / sunset / twilight times for a given lat-lon and date via sunrise-sunset.org (no auth)." data-category="web" data-tags="geo" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="global/#getSunriseSunset" aria-label="Open getSunriseSunset in Global">getSunriseSunset</a>
<div class="tcg-name"><span class="tcg-name__text">getSunriseSunset</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-weather-sunset:</div>
<div class="tcg-type">web · geo <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Returns sunrise / sunset / twilight times for a given lat-lon and date via sunrise-sunset.org (no auth).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `latitude` · `longitude` · `date` · `timezone`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Global</div>
</div>

<div class="tcg-card tcg-card--directory t-usgs" data-name="getrecentearthquakes" data-desc="fetches recent earthquakes from the usgs public catalog (no auth)." data-category="web" data-tags="geo" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="global/#getRecentEarthquakes" aria-label="Open getRecentEarthquakes in Global">getRecentEarthquakes</a>
<div class="tcg-name"><span class="tcg-name__text">getRecentEarthquakes</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-vibrate:</div>
<div class="tcg-type">web · geo <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Fetches recent earthquakes from the USGS public catalog (no auth).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `minMagnitude` · `lookbackHours` · `limit`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Global</div>
</div>

<div class="tcg-card tcg-card--directory t-crypto" data-name="getupbitticker" data-desc="fetches the current krw ticker(s) from upbit, a major korean crypto exchange (no auth). `markets` is comma-separated, e.g. 'krw-btc,krw-eth'. returns an array of { market, tradeprice, openingprice, hi" data-category="web" data-tags="korea,finance" data-preset="korea-toolkit" data-env="" markdown>
<a class="tcg-stretched-link" href="korea/#getUpbitTicker" aria-label="Open getUpbitTicker in Korea">getUpbitTicker</a>
<div class="tcg-name"><span class="tcg-name__text">getUpbitTicker</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-currency-btc:</div>
<div class="tcg-type">web · korea · finance <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Fetches the current KRW ticker(s) from Upbit, a major Korean crypto exchange (no auth). `markets` is comma-separated, e.g. 'KRW-BTC,KRW-ETH'. Returns an array of { market, tradePrice, openingPrice, highPrice, lowPrice, change, changeRate, accTradeVolume24h, timestamp }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `markets`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Korea</div>
</div>

<div class="tcg-card tcg-card--directory t-crypto" data-name="getupbitorderbook" data-desc="fetches upbit live orderbook (bids/asks) for one or more krw markets (no auth). `markets` is comma-separated like 'krw-btc,krw-eth'. optional `level` controls price aggregation upstream. returns an ar" data-category="web" data-tags="korea,finance" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="korea/#getUpbitOrderbook" aria-label="Open getUpbitOrderbook in Korea">getUpbitOrderbook</a>
<div class="tcg-name"><span class="tcg-name__text">getUpbitOrderbook</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-chart-box-outline:</div>
<div class="tcg-type">web · korea · finance <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Fetches Upbit live orderbook (bids/asks) for one or more KRW markets (no auth). `markets` is comma-separated like 'KRW-BTC,KRW-ETH'. Optional `level` controls price aggregation upstream. Returns an array of { market, timestamp, totalAskSize, totalBidSize, units:[{askPrice,bidPrice,askSize,bidSize}] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `markets` · `level`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Korea</div>
</div>

<div class="tcg-card tcg-card--directory t-crypto" data-name="getupbitcandles" data-desc="fetches upbit ohlcv candles for a market (no auth). `interval` accepts 'days' (default), 'weeks', 'months', or 'minutes/n' where n is one of 1, 3, 5, 10, 15, 30, 60, 240. `count` is capped at 200 upst" data-category="web" data-tags="korea,finance" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="korea/#getUpbitCandles" aria-label="Open getUpbitCandles in Korea">getUpbitCandles</a>
<div class="tcg-name"><span class="tcg-name__text">getUpbitCandles</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-chart-line:</div>
<div class="tcg-type">web · korea · finance <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Fetches Upbit OHLCV candles for a market (no auth). `interval` accepts 'days' (default), 'weeks', 'months', or 'minutes/N' where N is one of 1, 3, 5, 10, 15, 30, 60, 240. `count` is capped at 200 upstream. Returns an array (most recent first) of { market, candleDateTimeKst, candleDateTimeUtc, openingPrice, highPrice, lowPrice, tradePrice, candleAccTradeVolume, candleAccTradePrice }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `market` · `interval` · `count`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Korea</div>
</div>

<div class="tcg-card tcg-card--directory t-crypto" data-name="listupbitmarkets" data-desc="lists all tradable markets on upbit (no auth). pass `quote` (e.g. 'krw', 'btc', 'usdt') to filter by quote currency. returns { count, markets: [{ market, koreanname, englishname }] }." data-category="web" data-tags="korea,finance" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="korea/#listUpbitMarkets" aria-label="Open listUpbitMarkets in Korea">listUpbitMarkets</a>
<div class="tcg-name"><span class="tcg-name__text">listUpbitMarkets</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-format-list-bulleted:</div>
<div class="tcg-type">web · korea · finance <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Lists all tradable markets on Upbit (no auth). Pass `quote` (e.g. 'KRW', 'BTC', 'USDT') to filter by quote currency. Returns { count, markets: [{ market, koreanName, englishName }] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `quote`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Korea</div>
</div>

<div class="tcg-card tcg-card--directory t-crypto" data-name="getbithumbticker" data-desc="fetches the current krw ticker for a symbol from bithumb (no auth). used as an upbit alternative or for cross-exchange checks. returns: { symbol, openingprice, closingprice, minprice, maxprice, unitst" data-category="web" data-tags="korea,finance" data-preset="korea-toolkit" data-env="" markdown>
<a class="tcg-stretched-link" href="korea/#getBithumbTicker" aria-label="Open getBithumbTicker in Korea">getBithumbTicker</a>
<div class="tcg-name"><span class="tcg-name__text">getBithumbTicker</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-currency-btc:</div>
<div class="tcg-type">web · korea · finance <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Fetches the current KRW ticker for a symbol from Bithumb (no auth). Used as an Upbit alternative or for cross-exchange checks. Returns: { symbol, openingPrice, closingPrice, minPrice, maxPrice, unitsTraded24h, accTradeValue24h, change24h, changeRate24h, fluctate24h, fluctateRate24h }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `symbol`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Korea</div>
</div>

<div class="tcg-card tcg-card--directory t-crypto" data-name="getbithumborderbook" data-desc="fetches bithumb public orderbook depth for a krw pair (no auth). `count` defaults to 5, max 30. returns { symbol, paymentcurrency, ordercurrency, timestamp, bids:[{price,quantity}], asks:[{price,quant" data-category="web" data-tags="korea,finance" data-preset="" data-env="" markdown>
<a class="tcg-stretched-link" href="korea/#getBithumbOrderbook" aria-label="Open getBithumbOrderbook in Korea">getBithumbOrderbook</a>
<div class="tcg-name"><span class="tcg-name__text">getBithumbOrderbook</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-chart-box-outline:</div>
<div class="tcg-type">web · korea · finance <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Fetches Bithumb public orderbook depth for a KRW pair (no auth). `count` defaults to 5, max 30. Returns { symbol, paymentCurrency, orderCurrency, timestamp, bids:[{price,quantity}], asks:[{price,quantity}] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `symbol` · `count`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Korea</div>
</div>

<div class="tcg-card tcg-card--directory t-naver" data-name="searchnaver" data-desc="naver search api (kr; key required). searches across blog, news, webkr, encyc, book, image, shop, kin, or cafearticle. issue client-id + client-secret at https://developers.naver.com/apps/ and set nav" data-category="web" data-tags="korea,search" data-preset="" data-env="NAVER_CLIENT_ID,NAVER_CLIENT_SECRET" markdown>
<a class="tcg-stretched-link" href="korea/#searchNaver" aria-label="Open searchNaver in Korea">searchNaver</a>
<div class="tcg-name"><span class="tcg-name__text">searchNaver</span> <span class="cost">🔑 × 2</span></div>
<div class="tcg-art" markdown>:simple-naver:</div>
<div class="tcg-type">web · korea · search <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Naver Search API (KR; key required). Searches across blog, news, webkr, encyc, book, image, shop, kin, or cafearticle. Issue Client-Id + Client-Secret at https://developers.naver.com/apps/ and set NAVER_CLIENT_ID + NAVER_CLIENT_SECRET on the tool's staticVariables, or inject as env vars. Returns: { type, total, start, display, items:[{title, description, link, pubDate, bloggerName, author}] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `query` · `type` · `display` · `start`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `NAVER_CLIENT_ID` · `NAVER_CLIENT_SECRET`</div>
</div>
<div class="tcg-page">→ Korea</div>
</div>

<div class="tcg-card tcg-card--directory t-kakao" data-name="searchkakaolocal" data-desc="kakao local keyword search — finds places/pois and returns wgs84 coordinates (kr; key required). issue a rest api key at https://developers.kakao.com/ and set kakao_rest_api_key on the tool's staticva" data-category="web" data-tags="korea,geo" data-preset="" data-env="KAKAO_REST_API_KEY" markdown>
<a class="tcg-stretched-link" href="korea/#searchKakaoLocal" aria-label="Open searchKakaoLocal in Korea">searchKakaoLocal</a>
<div class="tcg-name"><span class="tcg-name__text">searchKakaoLocal</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:simple-kakaotalk:</div>
<div class="tcg-type">web · korea · geo <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Kakao Local keyword search — finds places/POIs and returns WGS84 coordinates (KR; key required). Issue a REST API key at https://developers.kakao.com/ and set KAKAO_REST_API_KEY on the tool's staticVariables, or inject as env var. Optionally pass (longitude, latitude, radius) to search around a point. Returns: { totalCount, pageableCount, isEnd, places:[{ name, category, categoryGroup, phone, address, roadAddress, latitude, longitude, placeUrl, distance }] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `query` · `size` · `page` · `longitude` · `latitude` · `radius`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `KAKAO_REST_API_KEY`</div>
</div>
<div class="tcg-page">→ Korea</div>
</div>

<div class="tcg-card tcg-card--directory t-datagokr" data-name="getairkoreapm" data-desc="airkorea (data.go.kr) real-time air quality readings by korean province (kr; data.go.kr key required). issue the air-quality servicekey at https://www.data.go.kr/data/15073861/openapi.do and set data_" data-category="web" data-tags="korea,weather" data-preset="" data-env="DATA_GO_KR_AIR_KEY" markdown>
<a class="tcg-stretched-link" href="korea/#getAirKoreaPm" aria-label="Open getAirKoreaPm in Korea">getAirKoreaPm</a>
<div class="tcg-name"><span class="tcg-name__text">getAirKoreaPm</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:material-air-filter:</div>
<div class="tcg-type">web · korea · weather <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
AirKorea (data.go.kr) real-time air quality readings by Korean province (KR; data.go.kr key required). Issue the air-quality serviceKey at https://www.data.go.kr/data/15073861/openapi.do and set DATA_GO_KR_AIR_KEY on the tool's staticVariables, or inject as env var. Returns: { sidoName, totalCount, stations:[{ stationName, sidoName, dataTime, pm10, pm25, o3, no2, co, so2, khaiValue, khaiGrade }] }. On auth error: { success:false, status, message }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `sidoName` · `numOfRows`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `DATA_GO_KR_AIR_KEY`</div>
</div>
<div class="tcg-page">→ Korea</div>
</div>

<div class="tcg-card tcg-card--directory t-apple" data-name="searchkpoponitunes" data-desc="itunes search api — korean music catalog including k-pop (no auth). default country=kr biases results to the korean itunes storefront. suitable for song / musicartist / album / musicvideo lookups. eac" data-category="web" data-tags="korea" data-preset="korea-toolkit" data-env="" markdown>
<a class="tcg-stretched-link" href="korea/#searchKpopOnItunes" aria-label="Open searchKpopOnItunes in Korea">searchKpopOnItunes</a>
<div class="tcg-name"><span class="tcg-name__text">searchKpopOnItunes</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-applemusic:</div>
<div class="tcg-type">web · korea <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
iTunes Search API — Korean music catalog including K-pop (no auth). Default country=kr biases results to the Korean iTunes storefront. Suitable for song / musicArtist / album / musicVideo lookups. Each result includes a 30s preview URL and album artwork URL. Catalog metadata is in the storefront language (Korean for kr). `entity`: musicArtist | song | album | musicVideo | mix. `country`: ISO-2 storefront code (kr/us/jp/...). Default kr. Returns: { country, entity, resultCount, results:[{ kind, artistName, trackName, collection, releaseDate, primaryGenre, previewUrl, trackViewUrl, artworkUrl, ... }] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `term` · `entity` · `country` · `limit`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Korea</div>
</div>

<div class="tcg-card tcg-card--directory t-beauty" data-name="searchkbeautyproducts" data-desc="k-beauty cosmetics product search via open beauty facts (no auth). default country=south-korea biases the lookup to korean brand catalogs (innisfree / laneige / cosrx / ...). returns ingredients, alle" data-category="web" data-tags="korea,search" data-preset="korea-toolkit" data-env="" markdown>
<a class="tcg-stretched-link" href="korea/#searchKBeautyProducts" aria-label="Open searchKBeautyProducts in Korea">searchKBeautyProducts</a>
<div class="tcg-name"><span class="tcg-name__text">searchKBeautyProducts</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-flower-tulip-outline:</div>
<div class="tcg-type">web · korea · search <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
K-beauty cosmetics product search via Open Beauty Facts (no auth). Default country=south-korea biases the lookup to Korean brand catalogs (Innisfree / Laneige / COSRX / ...). Returns ingredients, allergens, packaging, and product image URLs. For a global search pass country='', or other slugs like country='japan'. Note: localized product names may appear in Korean. Pass a barcode (e.g. '8809610706106') as `query` for single-product lookup — useful for ingredient checks. Returns: { country, count, page, pageSize, products:[{ code, productName, brands, countries, categories, allergens, ingredients, packaging, imageUrl, openBeautyFactsUrl }] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `query` · `country` · `pageSize`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-page">→ Korea</div>
</div>

<div class="tcg-card tcg-card--directory t-tour" data-name="searchkoreatour" data-desc="korea tourism organization tourapi 4.0 keyword search — tourist spots, cultural facilities, festivals, lodging, restaurants by korean keyword (kr; data.go.kr key required, separate from the air-qualit" data-category="web" data-tags="korea,search" data-preset="" data-env="DATA_GO_KR_TOUR_KEY" markdown>
<a class="tcg-stretched-link" href="korea/#searchKoreaTour" aria-label="Open searchKoreaTour in Korea">searchKoreaTour</a>
<div class="tcg-name"><span class="tcg-name__text">searchKoreaTour</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:material-bag-suitcase-outline:</div>
<div class="tcg-type">web · korea · search <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Korea Tourism Organization TourAPI 4.0 keyword search — tourist spots, cultural facilities, festivals, lodging, restaurants by Korean keyword (KR; data.go.kr key required, separate from the air-quality key). Issue the Korean tourism serviceKey at https://www.data.go.kr/data/15101578/openapi.do and set DATA_GO_KR_TOUR_KEY on the tool's staticVariables, or inject as env var. Filters: `areaCode` (province) + `sigunguCode` (city/county) + `contentTypeId` (content type). Examples: Jeonju = areaCode 37 + sigunguCode 12, Gyeongju = 35+2, Jeju City = 39+4, Seogwipo = 39+5. Metropolitan cities (Busan=6, Daegu=4, ...) do not require sigunguCode. Returns: { keyword, totalCount, pageNo, numOfRows, items:[{ contentId, contentTypeId, title, addr1, addr2, areaCode, sigunguCode, firstImage, mapX, mapY, tel, ... }] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `keyword` · `areaCode` · `sigunguCode` · `contentTypeId` · `pageNo` · `numOfRows`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `DATA_GO_KR_TOUR_KEY`</div>
</div>
<div class="tcg-page">→ Korea</div>
</div>

<div class="tcg-card tcg-card--directory t-seoul" data-name="searchseoulculturalevents" data-desc="seoul open data plaza (data.seoul.go.kr) cultural events search (kr; separate key required). this api is operated directly by the seoul city government and is separate from the data.go.kr keychain. is" data-category="web" data-tags="korea,search" data-preset="" data-env="SEOUL_OPEN_API_KEY" markdown>
<a class="tcg-stretched-link" href="korea/#searchSeoulCulturalEvents" aria-label="Open searchSeoulCulturalEvents in Korea">searchSeoulCulturalEvents</a>
<div class="tcg-name"><span class="tcg-name__text">searchSeoulCulturalEvents</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:material-theater:</div>
<div class="tcg-type">web · korea · search <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Seoul Open Data Plaza (data.seoul.go.kr) cultural events search (KR; separate key required). This API is operated directly by the Seoul city government and is separate from the data.go.kr keychain. Issue a free key at https://data.seoul.go.kr/together/apikey.do (1,000 req/day) and set SEOUL_OPEN_API_KEY on the tool's staticVariables, or inject as env var. Optional filters: `codename` (category: musical / exhibition / Korean classical music / concert / ...), `titleSearch` (partial title match), `eventDate` ('YYYY-MM-DD'; events active on that date only). Returns: { totalCount, events:[{ category, gu, title, period, startDate, endDate, place, organizer, audience, fee, isFree, program, imageUrl, latitude, longitude, ... }] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `startIndex` · `endIndex` · `codename` · `titleSearch` · `eventDate`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `SEOUL_OPEN_API_KEY`</div>
</div>
<div class="tcg-page">→ Korea</div>
</div>

<div class="tcg-card tcg-card--directory t-kamis" data-name="getkamisagriprice" data-desc="kamis agricultural product wholesale/retail prices — daily price data operated by at (korea agro-fisheries & food trade corp). kr; cert_id + cert_key required, free. issue credentials at https://www.k" data-category="web" data-tags="korea,finance" data-preset="" data-env="KAMIS_CERT_ID,KAMIS_CERT_KEY" markdown>
<a class="tcg-stretched-link" href="korea/#getKamisAgriPrice" aria-label="Open getKamisAgriPrice in Korea">getKamisAgriPrice</a>
<div class="tcg-name"><span class="tcg-name__text">getKamisAgriPrice</span> <span class="cost">🔑 × 2</span></div>
<div class="tcg-art" markdown>:material-leaf:</div>
<div class="tcg-type">web · korea · finance <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
KAMIS agricultural product wholesale/retail prices — daily price data operated by aT (Korea Agro-Fisheries & Food Trade Corp). KR; cert_id + cert_key required, free. Issue credentials at https://www.kamis.or.kr/customer/reference/openapi_list.do and set KAMIS_CERT_ID + KAMIS_CERT_KEY on the tool's staticVariables, or inject as env vars. `productClsCode`: 01=retail, 02=wholesale (default). `itemCode` is the KAMIS product code (rice=111, apple=411, napa cabbage=211, pork belly=515, ...). Returns: { productClass, itemCode, startDay, endDay, count, rows:[{ itemName, kindName, county, market, year, date, price, unit }] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `itemCode` · `startDay` · `endDay` · `productClsCode` · `itemCategoryCode` · `kindCode`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `KAMIS_CERT_ID` · `KAMIS_CERT_KEY`</div>
</div>
<div class="tcg-page">→ Korea</div>
</div>

<div class="tcg-card tcg-card--directory t-kofic" data-name="getkoficboxoffice" data-desc="kofic (korean film council) daily box-office ranking (kr; single api key required, free instant issuance). issue the key at https://www.kobis.or.kr/kobisopenapi/ and set kofic_api_key on the tool's st" data-category="web" data-tags="korea" data-preset="" data-env="KOFIC_API_KEY" markdown>
<a class="tcg-stretched-link" href="korea/#getKoficBoxOffice" aria-label="Open getKoficBoxOffice in Korea">getKoficBoxOffice</a>
<div class="tcg-name"><span class="tcg-name__text">getKoficBoxOffice</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:material-movie-outline:</div>
<div class="tcg-type">web · korea <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
KOFIC (Korean Film Council) daily box-office ranking (KR; single API key required, free instant issuance). Issue the key at https://www.kobis.or.kr/kobisopenapi/ and set KOFIC_API_KEY on the tool's staticVariables, or inject as env var. `targetDate` is typically yesterday's date (same-day totals are tallied after market close). Both YYYYMMDD and YYYY-MM-DD are accepted. Optional filters: `multiMovieYn` (Y=diversity films only / N=commercial films only), `repNationCd` (K=Korean / F=foreign), `wideAreaCd` (screening region). Returns: { type, showRange, count, movies:[{ rank, rankChange, isNew, movieCode, title, openDate, salesAmount, salesShare, salesAccumulated, audience, audienceAccumulated, screens, shows }] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `targetDate` · `multiMovieYn` · `repNationCd` · `wideAreaCd`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `KOFIC_API_KEY`</div>
</div>
<div class="tcg-page">→ Korea</div>
</div>

<div class="tcg-card tcg-card--directory t-krx" data-name="getkrxstockprice" data-desc="krx korea exchange daily stock quotes (data.go.kr) — kospi/kosdaq/konex daily open/close/change/volume/market cap. kr; data.go.kr servicekey required, separate service application from other dgk keys." data-category="web" data-tags="korea,finance" data-preset="" data-env="DATA_GO_KR_STOCK_KEY" markdown>
<a class="tcg-stretched-link" href="korea/#getKrxStockPrice" aria-label="Open getKrxStockPrice in Korea">getKrxStockPrice</a>
<div class="tcg-name"><span class="tcg-name__text">getKrxStockPrice</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:material-chart-line:</div>
<div class="tcg-type">web · korea · finance <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
KRX Korea Exchange daily stock quotes (data.go.kr) — KOSPI/KOSDAQ/KONEX daily open/close/change/volume/market cap. KR; data.go.kr serviceKey required, separate service application from other dgk keys. Register the `Financial Services Commission stock quote info` service at data.go.kr (https://www.data.go.kr/data/15094808/openapi.do), receive a serviceKey, and set DATA_GO_KR_STOCK_KEY on the tool's staticVariables, or inject as env var. Note: the KIS API (Korea Investment & Securities) is a two-step token → Bearer flow that is inefficient for stateless tools (token consumed per call). This tool uses the KRX-official channel that exposes the same data behind a single key. Filters: `basDt` (business day YYYYMMDD), `itmsNm` (exact stock name), `likeItmsNm` (partial name match), `srtnCd` (short code e.g. 005930), `mrktCls` (KOSPI/KOSDAQ/KONEX). Returns: { totalCount, pageNo, numOfRows, items:[{ baseDate, shortCode, isinCode, name, market, close, diff, changePct, open, high, low, volume, tradeValue, listedShares, marketCap }] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `basDt` · `itmsNm` · `likeItmsNm` · `srtnCd` · `mrktCls` · `numOfRows` · `pageNo`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `DATA_GO_KR_STOCK_KEY`</div>
</div>
<div class="tcg-page">→ Korea</div>
</div>

<div class="tcg-card tcg-card--directory t-datagokr" data-name="calldatagokropenapi" data-desc="data.go.kr generic dispatcher — calls arbitrary data.go.kr services not covered by dedicated tools in this catalog. high-frequency services (air quality / tourism / stocks / ...) have their own tools;" data-category="web" data-tags="korea" data-preset="" data-env="DATA_GO_KR_KEY" markdown>
<a class="tcg-stretched-link" href="korea/#callDataGoKrOpenApi" aria-label="Open callDataGoKrOpenApi in Korea">callDataGoKrOpenApi</a>
<div class="tcg-name"><span class="tcg-name__text">callDataGoKrOpenApi</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:material-database:</div>
<div class="tcg-type">web · korea <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
data.go.kr generic dispatcher — calls arbitrary data.go.kr services not covered by dedicated tools in this catalog. High-frequency services (air quality / tourism / stocks / ...) have their own tools; use this dispatcher for the 7,000+ other services (real-estate transactions / postal codes / road-name addresses / drug-safety agency / national statistics / ...). Most responses are in Korean. Set the data.go.kr serviceKey on the tool's staticVariables as DATA_GO_KR_KEY, or inject as env var. NOTE: each data.go.kr dataset requires its own service registration (the key value can be the same, but each OpenAPI service is approved separately). Inputs: { servicePath: 'B551011/KorService2/...' (the path after apis.data.go.kr/), query: { pageNo:1, numOfRows:10, ... } (extra query parameters) }. On success: { ok:true, totalCount, pageNo, numOfRows, items, raw }. On failure: { ok:false, status, message } (HTTP error or OpenAPI_ServiceResponse.cmmMsgHeader error).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `servicePath` · `query`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `DATA_GO_KR_KEY`</div>
</div>
<div class="tcg-page">→ Korea</div>
</div>

<div class="tcg-card tcg-card--directory t-kma" data-name="getkmashorttermforecast" data-desc="kma short-term weather forecast — hourly forecast for the next ~72 hours by lat/lon or kma grid coordinates (nx,ny). kr; data.go.kr servicekey required, separate kma service registration. register the" data-category="web" data-tags="korea,weather" data-preset="" data-env="DATA_GO_KR_KMA_KEY" markdown>
<a class="tcg-stretched-link" href="korea/#getKmaShortTermForecast" aria-label="Open getKmaShortTermForecast in Korea">getKmaShortTermForecast</a>
<div class="tcg-name"><span class="tcg-name__text">getKmaShortTermForecast</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:material-weather-cloudy:</div>
<div class="tcg-type">web · korea · weather <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
KMA short-term weather forecast — hourly forecast for the next ~72 hours by lat/lon or KMA grid coordinates (nx,ny). KR; data.go.kr serviceKey required, separate KMA service registration. Register the `KMA short-term forecast service` at data.go.kr, receive a serviceKey, and set DATA_GO_KR_KMA_KEY on the tool's staticVariables, or inject as env var. Coordinates: pass either (latitude, longitude) or (nx, ny). Lat/lon are converted internally to KMA Lambert grid. baseDate/baseTime default to today's 0500 release (KMA releases at 02/05/08/11/14/17/20/23). Response is pivoted to 1-hour slots: { fcstDate, fcstTime, temp(℃), humidity(%), precipProbability(%), precipType, precipAmount, skyCondition, windSpeed(m/s), windDirection(deg) }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `latitude` · `longitude` · `nx` · `ny` · `baseDate` · `baseTime`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `DATA_GO_KR_KMA_KEY`</div>
</div>
<div class="tcg-page">→ Korea</div>
</div>

<div class="tcg-card tcg-card--directory t-molit" data-name="getapartmenttradeprice" data-desc="molit (ministry of land, infrastructure & transport) apartment sale transactions (kr; data.go.kr servicekey required). register the molit apartment-trade data service at data.go.kr, receive a servicek" data-category="web" data-tags="korea,finance" data-preset="" data-env="DATA_GO_KR_APT_KEY" markdown>
<a class="tcg-stretched-link" href="korea/#getApartmentTradePrice" aria-label="Open getApartmentTradePrice in Korea">getApartmentTradePrice</a>
<div class="tcg-name"><span class="tcg-name__text">getApartmentTradePrice</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:material-home-city-outline:</div>
<div class="tcg-type">web · korea · finance <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
MOLIT (Ministry of Land, Infrastructure & Transport) apartment sale transactions (KR; data.go.kr serviceKey required). Register the MOLIT apartment-trade data service at data.go.kr, receive a serviceKey, and set DATA_GO_KR_APT_KEY on the tool's staticVariables, or inject as env var. `lawdCode` is the 5-digit legal-dong city/county code (not the road-name address). Examples: Gangnam=11680, Seocho=11650, Songpa=11710, Haeundae=26350, Bundang(Seongnam)=41135, Jeju City=50110. `dealYmd` is the transaction month as YYYYMM. Returns: { lawdCode, dealYmd, totalCount, pageNo, numOfRows, items:[{ aptName, dealYear, dealMonth, dealDay, dealAmount(10K KRW), excluUseAr(sqm), floor, buildYear, umdNm(legal dong), jibun(lot), roadName, dealingType }] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `lawdCode` · `dealYmd` · `numOfRows` · `pageNo`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `DATA_GO_KR_APT_KEY`</div>
</div>
<div class="tcg-page">→ Korea</div>
</div>

<div class="tcg-card tcg-card--directory t-mfds" data-name="searchkoreadruginfo" data-desc="mfds (ministry of food & drug safety) drug product approval search (kr; data.go.kr servicekey required). register the `mfds drug product approval info` service at data.go.kr, receive a servicekey, and" data-category="web" data-tags="korea,search" data-preset="" data-env="DATA_GO_KR_DRUG_KEY" markdown>
<a class="tcg-stretched-link" href="korea/#searchKoreaDrugInfo" aria-label="Open searchKoreaDrugInfo in Korea">searchKoreaDrugInfo</a>
<div class="tcg-name"><span class="tcg-name__text">searchKoreaDrugInfo</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:material-pill:</div>
<div class="tcg-type">web · korea · search <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
MFDS (Ministry of Food & Drug Safety) drug product approval search (KR; data.go.kr serviceKey required). Register the `MFDS drug product approval info` service at data.go.kr, receive a serviceKey, and set DATA_GO_KR_DRUG_KEY on the tool's staticVariables, or inject as env var. At least one of `itemName` (partial product name), `entpName` (company name), or `itemSeq` (product sequence) is required. Returns: { totalCount, pageNo, numOfRows, items:[{ itemSeq, itemName, entpName, itemPermitDate, className, storageMethod, packUnit, validTerm, cancelDate, cancelName, chart }] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `itemName` · `entpName` · `itemSeq` · `numOfRows` · `pageNo`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `DATA_GO_KR_DRUG_KEY`</div>
</div>
<div class="tcg-page">→ Korea</div>
</div>

<div class="tcg-card tcg-card--directory t-mois" data-name="getkoreaemergencyalerts" data-desc="mois (ministry of the interior & safety) emergency disaster-alert sms history (kr; data.go.kr servicekey required). register the `mois emergency disaster alerts` service at data.go.kr, receive a servi" data-category="web" data-tags="korea" data-preset="" data-env="DATA_GO_KR_DISASTER_KEY" markdown>
<a class="tcg-stretched-link" href="korea/#getKoreaEmergencyAlerts" aria-label="Open getKoreaEmergencyAlerts in Korea">getKoreaEmergencyAlerts</a>
<div class="tcg-name"><span class="tcg-name__text">getKoreaEmergencyAlerts</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:material-alert-octagon-outline:</div>
<div class="tcg-type">web · korea <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
MOIS (Ministry of the Interior & Safety) emergency disaster-alert SMS history (KR; data.go.kr serviceKey required). Register the `MOIS emergency disaster alerts` service at data.go.kr, receive a serviceKey, and set DATA_GO_KR_DISASTER_KEY on the tool's staticVariables, or inject as env var. `area` matches the dispatch region name partially (Korean string). `fromDate`/`toDate` accept YYYYMMDD or YYYY-MM-DD. Returns: { totalCount, pageNo, numOfRows, items:[{ serialNo, createDate, message, emergencyStep, disasterType, location }] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `area` · `fromDate` · `toDate` · `numOfRows` · `pageNo`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `DATA_GO_KR_DISASTER_KEY`</div>
</div>
<div class="tcg-page">→ Korea</div>
</div>

</div>

</div>
</div>

