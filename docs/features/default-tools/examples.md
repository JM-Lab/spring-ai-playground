Description: Default Tools — Examples reference. 7 starter tools covering web fetch, datetime, productivity, search, AI, and messaging.

# Default Tools — Examples

The seven tools in `default-tool-specs.json` are the **starter examples**. They span the surfaces a beginner is likely to want first — fetch a web page, get the current time, build a calendar link, look up weather, search the web, call an LLM, send a Slack message — and double as ready-to-copy templates for the helper API you will use in your own tools.

Three of the seven need an API key or webhook URL to be useful (`googlePseSearch`, `openaiResponseGenerator`, `sendSlackMessage`). The rest work out of the box on a fresh install — `getCurrentTime` and `evalExpression` are members of every shipped preset because they have no dependency at all.

All 7 inherit Tool Studio's default sandbox: deny-first class allowlist, no filesystem, network in `strict` mode with [the SSRF four-layer guard](../tool-studio.md#ssrf-four-layer-guard) for the tools that fetch.

## The 7 examples { #the-examples }

<div class="tcg-grid" markdown>

<div class="tcg-card tcg-card--clickable" id="extractPageContent" data-tool-id="extractPageContent" data-tool-title="extractPageContent" markdown>
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
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Returns JSON: { title, content (with [n] link markers), links: [{index, text, url, linkTitle}] }.

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `pageUrl` | `STRING` | ✓ | Page URL to fetch and clean |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

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
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `timeZone` | `STRING` |  | IANA time zone identifier (e.g., Asia/Seoul) |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

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

</div>
</div>

<div class="tcg-card t-google tcg-card--clickable" id="buildGoogleCalendarCreateLink" data-tool-id="buildGoogleCalendarCreateLink" data-tool-title="buildGoogleCalendarCreateLink" markdown>
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
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

The tool only generates a URL; the user must open it and click "Save" in Google Calendar.

If the user provides a rough input for date, time, or location 
(e.g., "tomorrow 10am", "Seoul", "New York"), the agent is expected
to parse and convert these inputs into proper ISO-8601 date strings 
for start/end and a valid IANA time zone identifier for timeZone 
before passing them to this tool.

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `title` | `STRING` | ✓ | Event title shown in Google Calendar. |
| `start` | `STRING` | ✓ | Event start time. The agent should convert any rough user input (like 'tomorrow 10am') into a valid ISO-8601 string. (e.g., 2025-12-16T10:00:00+09:… |
| `end` | `STRING` | ✓ | Event end time. Must be after start. The agent should ensure proper ISO-8601 format(e.g., 2025-12-16T10:00:00+09:00). |
| `details` | `STRING` |  | Optional event description or agenda. |
| `location` | `STRING` |  | Optional event location text. The agent can resolve rough location names to standard city names if needed. |
| `timeZone` | `STRING` |  | IANA time zone identifier (e.g., Asia/Seoul). If the user provides a city or location name, the agent should convert it to a valid IANA time zone b… |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Build a Google Calendar "Add event" URL (action=TEMPLATE).
 *
 * This tool ONLY generates a URL.
 * The user must open the link and click "Save" in Google Calendar.
 *
 * INPUT NOTES:
 * - start / end: Date object or ISO-8601 string parseable by Date()
 * - dates are encoded in UTC (Google Calendar requirement)
 * - timeZone (ctz) controls UI display, not the UTC timestamps
 *
 */

function toDate(v) {
  const d = v instanceof Date ? v : new Date(String(v));
  if (isNaN(d.getTime())) {
    throw new Error('Invalid date value: ' + v);
  }
  return d;
}

// Google Calendar expects UTC timestamps like: 20251216T010000Z
function formatAsUtcCompact(d) {
  return d
    .toISOString()
    .replace(/[-:]/g, '')
    .replace(/\.\d{3}Z$/, 'Z');
}

const s = toDate(start);
const e = toDate(end);

if (e <= s) {
  throw new Error('end must be after start');
}

const base = 'https://www.google.com/calendar/render?action=TEMPLATE';
const params = [];

params.push('text=' + encodeURIComponent(String(title || '')));
params.push(
  'dates=' +
    encodeURIComponent(formatAsUtcCompact(s) + '/' + formatAsUtcCompact(e))
);

if (details) params.push('details=' + encodeURIComponent(String(details)));
if (location) params.push('location=' + encodeURIComponent(String(location)));
if (timeZone) params.push('ctz=' + encodeURIComponent(String(timeZone)));

return base + '&' + params.join('&');

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="getWeather" data-tool-id="getWeather" data-tool-title="getWeather" markdown>
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
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `location` | `STRING` |  | City / region name (e.g. 'Seoul', 'New York'). Empty = caller's IP-detected location. |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

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

</div>
</div>

<div class="tcg-card t-google tcg-card--clickable" id="googlePseSearch" data-tool-id="googlePseSearch" data-tool-title="googlePseSearch" markdown>
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

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

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

</div>
</div>

<div class="tcg-card t-openai tcg-card--clickable" id="openaiResponseGenerator" data-tool-id="openaiResponseGenerator" data-tool-title="openaiResponseGenerator" markdown>
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

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

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

</div>
</div>

<div class="tcg-card t-slack tcg-card--clickable" id="sendSlackMessage" data-tool-id="sendSlackMessage" data-tool-title="sendSlackMessage" markdown>
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

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
const resp = await fetch(slackWebhookUrl, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ text }),
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };
return { status: 'ok' };

```

</div>
</div>

</div>

## Composition patterns (starter chains)

These seven tools are picked so any pair plugs together — a perfect first agentic-workflow surface. Two patterns you can reproduce after Local Pass:

- **Search → summarise** — `googlePseSearch(query)` returns ranked snippets; pass them as a prompt fragment into `openaiResponseGenerator` so the model cites recent sources rather than parametric memory.
- **Fetch → notify** — `getWeather(location)` (or `getOpenMeteoForecast` from [Global](global.md)) → `sendSlackMessage(text)` to post a daily threshold alert to a channel.
- **Time + Calendar** — `getCurrentTime(timeZone)` produces an ISO timestamp the model can offset, then `buildGoogleCalendarCreateLink(title, start, end, ...)` returns a one-click "Add to Calendar" URL.

Deeper walk-throughs in [Tutorial 8: Default Tool Recipes](../../tutorials/8-default-tool-recipes.md).

## Keys & secrets

Three of the seven need a credential. The launcher's **Environment Variables** card is the recommended place to set them; the static-variable substring is masked from `console.log` and from the chat tool-call trace whenever it appears as a full string in the output.

| Tool | Env var | Where to issue |
|---|---|---|
| `openaiResponseGenerator` | `OPENAI_API_KEY` | [platform.openai.com/api-keys](https://platform.openai.com/api-keys) |
| `googlePseSearch` | `GOOGLE_API_KEY` + `GOOGLE_PSE_ID` | [Google Cloud Console](https://console.cloud.google.com/) for the API key, [Programmable Search Engine](https://programmablesearchengine.google.com/) for the PSE ID |
| `sendSlackMessage` | `SLACK_WEBHOOK_URL` | [api.slack.com/apps](https://api.slack.com/apps) → Incoming Webhooks → Add to Workspace |

→ [Tool Studio: Static Variables](../tool-studio.md#key-tool-studio-capabilities) — how `${ENV_VAR}` placeholders resolve at runtime.
→ [Index](index.md) — overview of all 86 default tools and the five reference pages.
