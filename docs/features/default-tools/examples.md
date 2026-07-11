title: Default Tool Examples
description: Default Tools - Examples reference. 10 starter tools covering web fetch, datetime, productivity, search, AI, and messaging.

# Default Tools - Examples

The ten tools in `default-tool-specs.json` are the **starter examples**. They span the surfaces a beginner is likely to want first - fetch a web page, get the current time, draft an email, add a calendar event, show a location on a map, upload a file, look up weather, search the web, call an LLM, send a Slack message - and double as ready-to-copy templates for the helper API you will use in your own tools.

Three of the ten need an API key or webhook URL to be useful (`googlePseSearch`, `openaiResponseGenerator`, `sendSlackMessage`). The rest work out of the box on a fresh install - `getCurrentTime` and `evalExpression` are members of every shipped preset because they have no dependency at all.

All 10 inherit Tool Studio's default sandbox: deny-first class allowlist, no filesystem, network in `strict` or host-`allowlist` mode with [the SSRF four-layer guard](../tool-studio/index.md#ssrf-four-layer-guard) for the tools that fetch.

## The 10 examples { #the-examples }

<div class="tcg-grid" markdown>

<div class="tcg-card tcg-card--clickable" id="extractPageContent" data-tool-id="extractPageContent" data-tool-title="extractPageContent" markdown>
<div class="tcg-name"><span class="tcg-name__text">extractPageContent</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-language-html5:</div>
<div class="tcg-type">web · example <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Fetches a web page and extracts its main readable content + outbound links. Uses the host-injected fetch (SSRF-defended in `strict` mode) and safety.parser.html for parsing.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `pageUrl`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Returns JSON: { title, content (with [n] link markers), links: [{index, text, url, linkTitle}] }.

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `pageUrl` | `STRING` | ✓ | Page URL to fetch and clean |

**Sandbox** - **L3** (Scoped widening) - `strict` egress - `fetch` to any host (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
const resp = await fetch(pageUrl, {
  headers: { 'User-Agent': 'Mozilla/5.0 (compatible; spring-ai-playground)' },
  maxLength: 5_000_000,
});
if (!resp.ok) throw new Error('fetch failed: ' + resp.status + ' ' + resp.statusText);
const doc = safety.parser.html(resp.text());

const junkSelectors = [
  'script', 'style', 'noscript', 'iframe', 'svg', 'link', 'meta',
  'header', 'footer', 'nav', 'aside',
  '.ad', '.ads', '.advertisement', '.banner',
  "[id*='ad-']", "[class*='ad-']", "[class*='banner']",
  '.popup', '.modal', '.sidebar', '#sidebar', '.widget',
  '.cookie-consent', '.newsletter-signup',
  '.social-share', '.share-buttons', '.comments', '#comments',
  '.meta', '.author-info', '.related-posts',
];
junkSelectors.forEach(s => doc.select(s).remove());

let title = doc.title();
if (!title || title.trim() === '') {
  const h1 = doc.select('h1').first();
  if (h1) title = h1.text();
}

let main = doc.select('article').first() || doc.select('main').first();
if (!main) {
  const tryThese = ['#content', '.content', '#main-content', '.main-content',
                    '.post-body', '.entry-content', '#article-body', '.article-body'];
  for (const sel of tryThese) { const el = doc.select(sel).first(); if (el) { main = el; break; } }
}
if (!main) main = doc.body();

const links = [];
const linkEls = main.select('a[href]');
let idx = 1;
for (let i = 0; i < linkEls.size(); i++) {
  const el = linkEls.get(i);
  const url = el.attr('abs:href');
  const text = el.text().trim();
  const linkTitle = el.attr('title').trim();
  if (url && (text.length > 0 || el.select('img').size() > 0 || linkTitle.length > 0)) {
    links.push({ index: idx, text: text || '[Image Link]', url, linkTitle });
    el.after(' [' + idx + ']');
    idx++;
  }
}

['p','div','br','h1','h2','h3','h4','h5','h6','li'].forEach(tag => {
  const els = main.select(tag);
  for (let i = 0; i < els.size(); i++) els.get(i).append('\n');
});

return JSON.stringify({ title, content: main.text().trim(), links });

```

</details>

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="getCurrentTime" data-tool-id="getCurrentTime" data-tool-title="getCurrentTime" markdown>
<div class="tcg-name"><span class="tcg-name__text">getCurrentTime</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-clock-outline:</div>
<div class="tcg-type">datetime · example · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Returns the current time in ISO 8601 format. If the user specifies a city, country, or location, the agent should first map it to an IANA time zone and supply it via the timeZone parameter. If no time zone is provided, UTC is used.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `timeZone`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `timeZone` | `STRING` |  | IANA time zone identifier (e.g., Asia/Seoul) |

**Sandbox** - Runs at the sandbox **L0** baseline (Safest) - pure compute: no network, no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Returns the current time formatted as ISO-8601 with timezone offset.
 *
 * - If a timezone is provided, the offset format (+HH:MM / -HH:MM) is used.
 * - If no timezone is provided, UTC time with 'Z' is returned.
 *
 * JavaScript standard APIs only.
 */

const now = new Date();

// Format date/time parts in target timezone
const parts = new Intl.DateTimeFormat('en-CA', {
  timeZone,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
}).formatToParts(now);

const m = {};
for (const p of parts) {
  m[p.type] = p.value;
}

// Milliseconds
const ms = String(now.getMilliseconds()).padStart(3, '0');

// Local time interpreted as UTC millis
const localAsUtc = Date.UTC(
  m.year,
  Number(m.month) - 1,
  m.day,
  m.hour,
  m.minute,
  m.second
);

// Actual UTC millis
const actualUtc = now.getTime();

const offsetMinutes = Math.round((localAsUtc - actualUtc) / 60000);

const sign = offsetMinutes >= 0 ? '+' : '-';
const abs = Math.abs(offsetMinutes);
const hh = String(Math.floor(abs / 60)).padStart(2, '0');
const mm = String(abs % 60).padStart(2, '0');

return (
  `${m.year}-${m.month}-${m.day}` +
  `T${m.hour}:${m.minute}:${m.second}.${ms}` +
  `${sign}${hh}:${mm}`
);

```

</details>

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="sendEmail" data-tool-id="sendEmail" data-tool-title="sendEmail" markdown>
<div class="tcg-name"><span class="tcg-name__text">sendEmail</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-email-outline:</div>
<div class="tcg-type">productivity · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Drafts an email and renders a **Send email** action card in chat - the user clicks to send it from their own mail app (review-then-send).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `to` · `cc` · `subject` · `body`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Calling this tool does **not** send anything. It returns a fenced `saip-action` block (`type: email`) that the chat renders as an **Email draft** card with a **📧 Send email** button; the button opens a prefilled `mailto:` link in the user's own mail app. This is **review-then-send** - the model only drafts, and the user reviews the fields and sends. The model must never claim the email was sent, and the From address is the user's own default mail account.

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `to` | `STRING` |  | Recipient email address(es), comma-separated. Optional. |
| `cc` | `STRING` |  | Cc email address(es), comma-separated. Optional. |
| `subject` | `STRING` | ✓ | Email subject line. |
| `body` | `STRING` | ✓ | Email body text. Plain text; line breaks are kept. |

**Sandbox** - Runs at the sandbox **L0** baseline (Safest) - pure compute: no network, no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

````javascript
if (subject == null || String(subject).trim() === '') throw new Error('subject required');
if (body == null || String(body).trim() === '') throw new Error('body required');
const action = { type: 'email', subject: String(subject), body: String(body) };
const toValue = to == null ? '' : String(to).trim();
const ccValue = cc == null ? '' : String(cc).trim();
if (toValue !== '') action.to = toValue;
if (ccValue !== '') action.cc = ccValue;
return 'Email ready below — review it and click the Send email button to send it from your own mail app.\n\n```saip-action-return-direct\n' + JSON.stringify(action) + '\n```';
````

</details>

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="addToCalendar" data-tool-id="addToCalendar" data-tool-title="addToCalendar" markdown>
<div class="tcg-name"><span class="tcg-name__text">addToCalendar</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-calendar-plus:</div>
<div class="tcg-type">productivity · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Builds a calendar event and renders an **Add to calendar** action card in chat - a dropdown to add the event to Google Calendar, Outlook, or Yahoo Calendar, or to get a standard `.ics` file (the `.ics` opens directly in your calendar app on the desktop, or downloads in a browser).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `title` · `start` · `end` · `location` · `description`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Calling this tool does **not** add anything to a calendar. It returns a fenced `saip-action` block (`type: calendar`) that the chat renders as a **Calendar event** card with an **📅 Add to calendar** dropdown offering Google Calendar, Outlook, Yahoo Calendar, and an `.ics` option; on the desktop app the `.ics` opens directly in your calendar app, in a browser it downloads a standard `.ics` file client-side that the user imports. The agent must convert rough date/time input into ISO-8601 and ensure `end` is after `start`.

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `title` | `STRING` | ✓ | Event title. |
| `start` | `STRING` | ✓ | Start time as ISO-8601 (convert rough input first, e.g. 2026-01-15T10:00:00+09:00). |
| `end` | `STRING` | ✓ | End time as ISO-8601, after start. |
| `location` | `STRING` |  | Optional location text. |
| `description` | `STRING` |  | Optional event description. |

**Sandbox** - Runs at the sandbox **L0** baseline (Safest) - pure compute: no network, no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

````javascript
if (title == null || String(title).trim() === '') throw new Error('title required');
if (start == null || String(start).trim() === '') throw new Error('start required');
if (end == null || String(end).trim() === '') throw new Error('end required');
const s = new Date(String(start));
const e = new Date(String(end));
if (isNaN(s.getTime())) throw new Error('invalid start: ' + start);
if (isNaN(e.getTime())) throw new Error('invalid end: ' + end);
if (e <= s) throw new Error('end must be after start');
const action = { type: 'calendar', title: String(title), start: s.toISOString(), end: e.toISOString() };
const loc = location == null ? '' : String(location).trim();
const desc = description == null ? '' : String(description).trim();
if (loc !== '') action.location = loc;
if (desc !== '') action.description = desc;
return 'Event ready below — review it and click the Add to calendar button to add it to your own calendar.\n\n```saip-action-return-direct\n' + JSON.stringify(action) + '\n```';
````

</details>

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="showLocation" data-tool-id="showLocation" data-tool-title="showLocation" markdown>
<div class="tcg-name"><span class="tcg-name__text">showLocation</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-map-marker:</div>
<div class="tcg-type">productivity · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Renders an interactive Google Map of a place directly in the chat - no API key needed.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `query` · `label`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Calling this tool returns a fenced `saip-action` block (`type: map`) that the chat renders as a **Location** card with a keyless embedded Google Map centered on the place, plus an **Open in Google Maps** link. It is display-only - the map loads client-side from the `query` (a place name, address, or `lat,lng`); no account, key, or server call is involved. Useful right after a geocoding or weather tool so the user can see where the result is.

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `query` | `STRING` | ✓ | Place name, address, or 'lat,lng' to show on the map. |
| `label` | `STRING` |  | Optional short caption shown above the map. |

**Sandbox** - Runs at the sandbox **L0** baseline (Safest) - pure compute: no network, no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

````javascript
if (query == null || String(query).trim() === '') throw new Error('query required');
const action = { type: 'map', query: String(query).trim() };
const lbl = label == null ? '' : String(label).trim();
if (lbl !== '') action.label = lbl;
return 'Location shown on the map below.\n\n```saip-action\n' + JSON.stringify(action) + '\n```';
````

</details>

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="requestFileUpload" data-tool-id="requestFileUpload" data-tool-title="requestFileUpload" markdown>
<div class="tcg-name"><span class="tcg-name__text">requestFileUpload</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-file-upload-outline:</div>
<div class="tcg-type">productivity · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Asks the user to upload a file (CSV, Excel, image, or any document) and returns its workspace path - the chat opens an upload dialog.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `prompt` · `accept`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

In Agentic Chat this tool is interactive: when the model calls it, the chat opens an **upload dialog** (the same human-in-the-loop seam the tool-approval prompt uses) and pauses. The user picks a file; the browser converts spreadsheets (`.xlsx` / `.xls`) to CSV with SheetJS, the file is saved under the workspace `uploads/` directory, and the tool returns its path (for example `uploads/sales.csv`). The model then reads it with `readTextFile` and, for tabular data, parses it with `parseCsv`. Use it whenever you need data or a document the user has not pasted into the chat. Outside the chat (Tool Studio, an external MCP client) there is no upload UI, so it returns a notice instead of a file.

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `prompt` | `STRING` |  | Short message shown to the user explaining which file to upload. |
| `accept` | `STRING` |  | Optional accepted file types as a comma-separated list of extensions or MIME types (for example ".csv,.xlsx" or "image/*"). Leave empty to accept any file. |

**Sandbox** - Runs at the sandbox **L0** baseline (Safest) - pure compute: no network, no filesystem. The file is persisted by the host's upload handler, not by the tool's JS.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

````javascript
// Interactive upload. In the chat UI the host intercepts this call, opens an
// upload dialog, saves the chosen file into the conversation workspace, and
// returns its path. Outside the chat there is no upload UI, so nothing is received.
return 'File upload is interactive and only runs inside the Spring AI Playground chat. When called there it asks the user to upload a file, saves it to the conversation workspace (uploads/<name>), and returns that path. No upload interface is available in this context, so no file was received.';
````

</details>

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="describeImage" data-tool-id="describeImage" data-tool-title="describeImage" markdown>
<div class="tcg-name"><span class="tcg-name__text">describeImage</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-image-search-outline:</div>
<div class="tcg-type">productivity · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Analyzes an image the user shared in this conversation - the chat resolves the reference and attaches the image with its metadata so a vision-capable model can see it.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `ref` · `question`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

In Agentic Chat this tool is interactive: when the model calls it, the chat looks up the conversation's stored images (every attached image is saved content-addressed under the conversation's workspace `images/` directory). If `ref` names a stored image it is loaded directly; with no `ref` and exactly one stored image that image is used; with several the chat opens a chooser dialog; with none it opens an upload dialog. The resolved image and its metadata are then attached to the next model call as native multimodal content, so a vision-capable model actually sees the pixels - including images from turns that have already fallen out of the context window ("what was in that photo I sent earlier?"). Outside the chat (Tool Studio, an external MCP client) there is no image UI, so it returns a notice instead.

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `ref` | `STRING` |  | Optional identifier of the image to analyze (its hash or file name shown earlier). Leave empty to use the only image or to let the user choose. |
| `question` | `STRING` |  | Optional specific question about the image. |

**Sandbox** - Runs at the sandbox **L0** baseline (Safest) - pure compute: no network, no filesystem. The image is loaded and attached by the host's chat interceptor, not by the tool's JS.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

````javascript
// Interactive image analysis. In the chat UI the host intercepts this call,
// resolves the referenced image (asking the user when needed), and attaches it
// with its metadata so a vision model can see it. Outside the chat there is no image UI.
return 'Image analysis is interactive and only runs inside the Spring AI Playground chat. When called there it attaches the referenced image and its metadata for a vision-capable model. No image interface is available in this context.';
````

</details>

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="getWeather" data-tool-id="getWeather" data-tool-title="getWeather" markdown>
<div class="tcg-name"><span class="tcg-name__text">getWeather</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-weather-partly-cloudy:</div>
<div class="tcg-type">web · example · weather <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Free public weather lookup via wttr.in (no API key). Returns a small JSON summary: { location, tempC, humidity, windSpeed, windDirection }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `location`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `location` | `STRING` |  | City / region name (e.g. 'Seoul', 'New York'). Empty = caller's IP-detected location. |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `wttr.in` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
const path = encodeURIComponent((location || '').trim().replace(/ +/g, '+'));
const resp = await fetch('https://wttr.in/' + path + '?format=j1', { maxLength: 1_000_000 });
if (!resp.ok) throw new Error('wttr.in returned ' + resp.status);
const data = resp.json();

const area = data.nearest_area && data.nearest_area[0];
const areaName = area && area.areaName && area.areaName[0] && area.areaName[0].value;
const current = data.current_condition && data.current_condition[0];

return {
  location: areaName || location || null,
  tempC: current && current.temp_C ? Number(current.temp_C) : null,
  humidity: current && current.humidity ? Number(current.humidity) : null,
  windSpeed: current && current.windspeedKmph ? current.windspeedKmph + ' km/h' : null,
  windDirection: current && current.winddir16Point ? current.winddir16Point : null,
};

```

</details>

</div>
</div>

<div class="tcg-card t-google tcg-card--clickable" id="googlePseSearch" data-tool-id="googlePseSearch" data-tool-title="googlePseSearch" markdown>
<div class="tcg-name"><span class="tcg-name__text">googlePseSearch</span> <span class="cost">🔑 × 2</span></div>
<div class="tcg-art" markdown>:simple-google:</div>
<div class="tcg-type">web · example · search <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Google Programmable Search Engine query (Custom Search API). Requires `GOOGLE_API_KEY` and `GOOGLE_PSE_ID` env vars.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `query` · `resultNum` · `startPage`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `GOOGLE_API_KEY` · `GOOGLE_PSE_ID`</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Setup:
 1. Create / open a project at console.cloud.google.com, enable 'Custom Search API', issue an API key.
 2. Create a Programmable Search Engine at programmablesearchengine.google.com and copy its ID.
 3. Export the values:  GOOGLE_API_KEY=AIza...   GOOGLE_PSE_ID=01234...

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `query` | `STRING` | ✓ | Search query string |
| `resultNum` | `INTEGER` |  | Number of results to return (1-10, default 3) |
| `startPage` | `INTEGER` |  | 1-based offset into the result list |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `www.googleapis.com` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
const url = 'https://www.googleapis.com/customsearch/v1'
  + '?key=' + encodeURIComponent(googleApiKey)
  + '&cx='  + encodeURIComponent(pseId)
  + '&q='   + encodeURIComponent(query)
  + '&start=' + (startPage || 1)
  + '&num='   + (resultNum || 3);
const resp = await fetch(url, { maxLength: 5_000_000 });
if (!resp.ok) {
  return { success: false, status: resp.status, message: resp.text() };
}
return resp.json();

```

</details>

</div>
</div>

<div class="tcg-card t-openai tcg-card--clickable" id="openaiResponseGenerator" data-tool-id="openaiResponseGenerator" data-tool-title="openaiResponseGenerator" markdown>
<div class="tcg-name"><span class="tcg-name__text">openaiResponseGenerator</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:material-creation:</div>
<div class="tcg-type">ai · example <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Calls OpenAI's Responses API. Requires `OPENAI_API_KEY` env var.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `prompt` · `model`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `OPENAI_API_KEY`</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Setup:
 1. Sign in at platform.openai.com and issue an API key.
 2. Export it:  OPENAI_API_KEY=sk-...

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `prompt` | `STRING` | ✓ | User prompt / question |
| `model` | `STRING` |  | Model id (default 'gpt-5.4-mini') |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `api.openai.com` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
const resp = await fetch('https://api.openai.com/v1/responses', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + openaiApiKey,
  },
  body: JSON.stringify({
    model: model || 'gpt-5.4-mini',
    input: prompt,
  }),
  maxLength: 5_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

const data = resp.json();
let content = '';
let reasoning = '';
for (const item of (data.output || [])) {
  if (item.type === 'message' && item.content) {
    for (const c of item.content) {
      if (c && c.type === 'output_text' && c.text != null) content += c.text;
    }
  }
  if (item.type === 'reasoning' && item.summary) {
    for (const s of item.summary) if (s && s.text != null) reasoning += s.text;
  }
}
return { content, reasoning };

```

</details>

</div>
</div>

<div class="tcg-card t-slack tcg-card--clickable" id="sendSlackMessage" data-tool-id="sendSlackMessage" data-tool-title="sendSlackMessage" markdown>
<div class="tcg-name"><span class="tcg-name__text">sendSlackMessage</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:material-pound-box-outline:</div>
<div class="tcg-type">messaging · example <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Posts a text message to a Slack channel via Incoming Webhook. Requires `SLACK_WEBHOOK_URL` env var (the full https://hooks.slack.com/services/... URL).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `SLACK_WEBHOOK_URL`</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Setup:
 1. Create a Slack app at api.slack.com/apps, enable 'Incoming Webhooks'.
 2. Add a webhook to the target channel and copy the URL.
 3. Export it:  SLACK_WEBHOOK_URL=https://hooks.slack.com/services/...

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `text` | `STRING` | ✓ | Message text to post |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `hooks.slack.com` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
const resp = await fetch(slackWebhookUrl, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ text }),
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };
return { status: 'ok' };

```

</details>

</div>
</div>

</div>

## Composition patterns (starter chains)

These nine tools are picked so any pair plugs together - a perfect first agentic-workflow surface. Two patterns you can reproduce after Local Pass:

- **Search → summarise** - `googlePseSearch(query)` returns ranked snippets; pass them as a prompt fragment into `openaiResponseGenerator` so the model cites recent sources rather than parametric memory.
- **Fetch → notify** - `getWeather(location)` (or `getOpenMeteoForecast` from [Global](global.md)) → `sendSlackMessage(text)` to post a daily threshold alert to a channel.
- **Time + Calendar** - `getCurrentTime(timeZone)` produces an ISO timestamp the model can offset, then `addToCalendar(title, start, end, ...)` renders an **Add to calendar** action card with a dropdown to add the event to Google/Outlook/Yahoo Calendar or get a standard `.ics` file.

Deeper walk-throughs in [Tutorial 8: Default Tool Recipes](../../tutorials/8-default-tool-recipes.md).

## Keys & secrets

Three of the nine need a credential. The launcher's **Environment Variables** card is the recommended place to set them; the static-variable substring is masked from `console.log` and from the chat tool-call trace whenever it appears as a full string in the output.

| Tool | Env var | Where to issue |
|---|---|---|
| `openaiResponseGenerator` | `OPENAI_API_KEY` | [platform.openai.com/api-keys](https://platform.openai.com/api-keys) |
| `googlePseSearch` | `GOOGLE_API_KEY` + `GOOGLE_PSE_ID` | [Google Cloud Console](https://console.cloud.google.com/) for the API key, [Programmable Search Engine](https://programmablesearchengine.google.com/) for the PSE ID |
| `sendSlackMessage` | `SLACK_WEBHOOK_URL` | [api.slack.com/apps](https://api.slack.com/apps) → Incoming Webhooks → Add to Workspace |

→ [Tool Studio: Static Variables](../tool-studio/index.md#key-tool-studio-capabilities) - how `${ENV_VAR}` placeholders resolve at runtime.
