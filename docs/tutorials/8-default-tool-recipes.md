description: Tutorial 8 - five default-tool recipes composed inside a single JS action, published as new MCP-exposed custom tools.

# Tutorial 8 - Default Tool Recipes

**Time** 20 min (5 recipes ~ 4 min each) · **Difficulty** ★★★ · **Surfaces** Tool Studio + Agentic Chat

!!! abstract "Goal"
    Compose default-tool helpers inside one JS action - five new custom tools you author and publish in Tool Studio, each one going live on the built-in MCP server the moment Local Pass succeeds. Same flow each time: copy a default tool, swap the action, run Local Pass, hit **Test & Publish**.

This tutorial differs from [Tutorial 7](7-weather-to-slack.md) in *where* the chaining happens. Tutorial 7 chains two pre-existing tools at the **agent loop** layer - the model decides "call A, then call B". Tutorial 8 chains the same kind of helpers *inside one JS action*, producing a single new tool the agent then calls once. Both shapes are useful: pick the agent-loop pattern when each step's output should be a separate user-visible turn, and pick the in-action pattern when the steps are an implementation detail.

The pattern (every recipe follows it):

1. Open the closest default tool in Tool Studio.
2. **Copy And New Tool** - gives you the same parameter shape with a fresh `toolId` and a `Draft` badge.
3. Rewrite the JS action to chain the helpers you need.
4. Adjust the test value so Local Pass exercises the new logic.
5. **Test & Publish**. The new tool joins the built-in MCP server the same moment Local Pass succeeds - Agentic Chat sees it on the next turn.

The cross-platform property carries through - all five recipes use only JVM-backed helpers (`fetch`, `safety.fs.*`), so the same JS runs identically on macOS, Windows, and Linux. See [Tool Studio: Cross-platform by design](../features/tool-studio/index.md#cross-platform-by-design).

---

## Recipe 1 - Hacker News digest (`hnDailyDigest`)

**Default tools used:** [`searchHackerNews`](../features/default-tools/global.md) ・ [`openaiResponseGenerator`](../features/default-tools/examples.md)

**What we're building** - a tool that takes a `query` ("AI", "TypeScript", "Rust", ...), pulls the top Hacker News stories from the last 24 h, and asks the OpenAI Responses API to summarise the chatter in five bullet points. The most universally useful "morning brief" recipe.

**Sandbox** - `networkMode: strict`.

**Static variable required** - `openaiApiKey = ${OPENAI_API_KEY}`.

**Parameters** - `query` (string, required), `hits` (integer, optional, default 10), `model` (string, optional, default `gpt-4o-mini`).

**JS action:**

```javascript
const query = params.query;
const hits = params.hits || 10;
const model = params.model || 'gpt-4o-mini';

// 1. fetch the top stories from Algolia HN Search (anonymous)
const url = `https://hn.algolia.com/api/v1/search_by_date?` +
            `query=${encodeURIComponent(query)}&tags=story&hitsPerPage=${hits}`;
const hn = await (await fetch(url)).json();
const stories = (hn.hits || []).map(h => ({
  title: h.title,
  url: h.url || `https://news.ycombinator.com/item?id=${h.objectID}`,
  points: h.points,
  comments: h.num_comments,
}));

// 2. ask the model for a 5-bullet digest
const prompt = `Summarise these Hacker News stories in 5 short bullets ` +
               `for a developer audience. Lead with what changed; mention ` +
               `concrete numbers and breaking news.\n\n` +
               stories.map((s, i) => `${i+1}. ${s.title} (${s.points} pts, ${s.comments} comments) - ${s.url}`).join('\n');
const aiResp = await fetch('https://api.openai.com/v1/responses', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${openaiApiKey}`,
    'Content-Type': 'application/json',
  },
  body: JSON.stringify({ model, input: prompt }),
});
const ai = await aiResp.json();
const summary = ai.output_text || ai.output?.[0]?.content?.[0]?.text || '';

return { query, count: stories.length, summary, stories };
```

**Test value** - `query`: `Anthropic`.

**Chat prompt** - *"Give me today's HN digest on AI agents."*

---

## Recipe 2 - GitHub release radar → Slack (`releaseRadar`)

**Default tools used:** [`getGithubLatestRelease`](../features/default-tools/global.md) ・ [`openaiResponseGenerator`](../features/default-tools/examples.md) ・ [`sendSlackMessage`](../features/default-tools/examples.md)

**What we're building** - takes `owner` and `repo`, fetches the latest non-draft release, asks the LLM for a three-sentence "what users will feel" digest, and posts it to a Slack channel. The single most common "ship-it" agent pattern in dev teams.

**Sandbox** - `networkMode: strict`.

**Static variables required** - `openaiApiKey = ${OPENAI_API_KEY}` ・ `slackWebhookUrl = ${SLACK_WEBHOOK_URL}`.

**Parameters** - `owner` (string, required), `repo` (string, required), `model` (string, optional, default `gpt-4o-mini`).

**JS action:**

```javascript
const owner = params.owner;
const repo = params.repo;
const model = params.model || 'gpt-4o-mini';

// 1. latest release
const ghResp = await fetch(
  `https://api.github.com/repos/${owner}/${repo}/releases/latest`,
  { headers: { 'Accept': 'application/vnd.github+json' } }
);
if (!ghResp.ok) return { ok: false, status: ghResp.status, message: ghResp.statusText };
const release = await ghResp.json();

// 2. AI digest
const prompt = `Summarise these release notes in three sentences for a ` +
               `Slack channel. Lead with what users will feel; mention ` +
               `breaking changes if any.\n\n${release.body || '(empty body)'}`;
const aiResp = await fetch('https://api.openai.com/v1/responses', {
  method: 'POST',
  headers: { 'Authorization': `Bearer ${openaiApiKey}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ model, input: prompt }),
});
const ai = await aiResp.json();
const summary = ai.output_text || ai.output?.[0]?.content?.[0]?.text || '';

// 3. Slack post
const slackBody = `*${owner}/${repo} ${release.tag_name}* released\n` +
                  `${summary}\n<${release.html_url}|Full notes ↗>`;
await fetch(slackWebhookUrl, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ text: slackBody }),
});

return { tag: release.tag_name, publishedAt: release.published_at, url: release.html_url, summary };
```

**Test value** - `owner`: `spring-projects`, `repo`: `spring-ai`.

**Chat prompt** - *"Post the latest spring-projects/spring-ai release to #releases."*

---

## Recipe 3 - City weather → Calendar event (`cityForecastEvent`)

**Default tools used:** [`geocodeAddress`](../features/default-tools/global.md) ・ [`getOpenMeteoForecast`](../features/default-tools/global.md) ・ [`buildGoogleCalendarCreateLink`](../features/default-tools/examples.md)

**What we're building** - given a city name, forward-geocode it through Nominatim, fetch tomorrow's hourly forecast from Open-Meteo, and if it is going to be sunny in the afternoon, return an "Add to Google Calendar" URL pre-filled with a "Beach trip" event. The agent then offers the link to the user. No keys needed for the geo + weather half; only the calendar URL builder is free.

**Sandbox** - `networkMode: strict`.

**Parameters** - `city` (string, required), `eventTitle` (string, optional, default `Outdoor plan`), `timeZone` (string, optional, default `UTC`).

**JS action:**

```javascript
const city = params.city;
const eventTitle = params.eventTitle || 'Outdoor plan';
const timeZone = params.timeZone || 'UTC';

// 1. geocode
const geoResp = await fetch(
  `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(city)}&format=json&limit=1`,
  { headers: { 'User-Agent': 'spring-ai-playground/cityForecastEvent' } }
);
const geo = await geoResp.json();
if (!geo.length) return { ok: false, reason: 'no geocode for ' + city };
const { lat, lon, display_name } = geo[0];

// 2. forecast (3 days, hourly precipitation probability)
const fcResp = await fetch(
  `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}` +
  `&hourly=precipitation_probability,temperature_2m&forecast_days=3&timezone=${encodeURIComponent(timeZone)}`
);
const fc = await fcResp.json();

// 3. find first 'sunny afternoon': pp < 20 and 13:00 <= hour < 18:00 within the next 3 days
const times = fc.hourly?.time || [];
const pp = fc.hourly?.precipitation_probability || [];
let pick = null;
for (let i = 0; i < times.length; i++) {
  const hr = new Date(times[i]).getHours();
  if (hr >= 13 && hr < 18 && pp[i] <= 20) { pick = { iso: times[i], pp: pp[i] }; break; }
}
if (!pick) return { city: display_name, ok: false, reason: 'no sunny afternoon in next 3 days' };

// 4. build a Google Calendar Add-Event URL for that slot
const start = pick.iso.replace(/[-:]/g, '').replace('T', 'T') + '00';
const endDate = new Date(new Date(pick.iso).getTime() + 2 * 60 * 60 * 1000)
                  .toISOString().slice(0, 16).replace(/[-:]/g, '');
const calUrl =
  'https://calendar.google.com/calendar/render?action=TEMPLATE' +
  `&text=${encodeURIComponent(eventTitle + ' - ' + display_name.split(',')[0])}` +
  `&dates=${start}/${endDate}` +
  `&details=${encodeURIComponent('Forecast says ' + pick.pp + '% chance of rain at ' + pick.iso)}` +
  `&location=${encodeURIComponent(display_name)}` +
  `&ctz=${encodeURIComponent(timeZone)}`;

return { city: display_name, slot: pick.iso, rainProbability: pick.pp, addToCalendarUrl: calUrl };
```

**Test value** - `city`: `Seoul`, `timeZone`: `Asia/Seoul`.

**Chat prompt** - *"Find me a sunny afternoon in Jeju this week and add a beach trip to my calendar."*

---

## Recipe 4 - Korea crypto kimchi-premium (`kimchiPremium`)

**Default tools used:** [`getUpbitTicker` · `getBithumbTicker`](../features/default-tools/korea.md) ・ [`evalExpression`](../features/default-tools/utilities.md)

**What we're building** - fetches the live BTC price from Upbit and Bithumb (both anonymous), computes the cross-exchange spread (the "kimchi premium" between Korean exchanges and the global market is a long-running phenomenon), and returns a JSON snapshot. Pure-Korea recipe - no English-speaking equivalent. No keys needed.

**Sandbox** - `networkMode: strict`.

**Parameters** - `symbol` (string, optional, default `BTC`).

**JS action:**

```javascript
const symbol = params.symbol || 'BTC';

// 1. Upbit ticker (KRW-XXX)
const upResp = await fetch(`https://api.upbit.com/v1/ticker?markets=KRW-${symbol}`);
const upArr = await upResp.json();
if (!upArr.length) return { ok: false, reason: 'upbit market not found' };
const upPrice = upArr[0].trade_price;

// 2. Bithumb ticker (symbol_KRW)
const bhResp = await fetch(`https://api.bithumb.com/public/ticker/${symbol}_KRW`);
const bh = await bhResp.json();
if (bh.status !== '0000') return { ok: false, reason: 'bithumb error ' + bh.status };
const bhPrice = Number(bh.data.closing_price);

// 3. spread (%)
const spreadPct = ((upPrice - bhPrice) / bhPrice) * 100;

return {
  symbol,
  upbitKrw: upPrice,
  bithumbKrw: bhPrice,
  spreadKrw: upPrice - bhPrice,
  spreadPct: Math.round(spreadPct * 100) / 100,
  timestamp: new Date().toISOString(),
};
```

**Test value** - `symbol`: `BTC`.

**Chat prompt** - *"비트코인 김치프리미엄 지금 얼마야?"*

---

## Recipe 5 - Filesystem pipeline (`summariseRecentLogs`)

**Default tools used:** [`listDir`, `grepFile`, `sliceFile`, `writeTextFile`](../features/default-tools/filesystem.md)

**What we're building** - finds the most recent `*.log` file under a directory, greps a regex (default `ERROR`), takes the last 50 matching lines, and writes them to a summary file. Useful for chunked log directories where the agent should produce a "recent errors" digest without inhaling the whole tree.

**Sandbox** - `fileRead = true` (L3) and `fileWrite = true` (L4) - open the **Sandbox & Capabilities** pane and switch **Filesystem mode** to `read+write` before publishing.

**Parameters** - `dir` (string, required), `pattern` (string, optional, default `ERROR`), `outPath` (string, optional, default `${dir}/errors-summary.txt`).

**JS action:**

```javascript
const dir = params.dir;
const pattern = params.pattern || 'ERROR';
const outPath = params.outPath || dir + '/errors-summary.txt';

// 1. find the latest *.log under dir
const entries = safety.fs.list(dir);
const logs = entries.filter(n => n.endsWith('.log'));
if (logs.length === 0) return { ok: false, reason: 'no .log files under ' + dir };
const withMtime = logs.map(n => ({ n, mtime: safety.fs.stat(dir + '/' + n).mtime }));
withMtime.sort((a, b) => b.mtime - a.mtime);
const latest = dir + '/' + withMtime[0].n;

// 2. grep the pattern, last 50 hits
const hits = safety.fs.grep(pattern, latest, { caseInsensitive: true, numbered: true, limit: 200 });
const tail = hits.slice(-50);

// 3. write the summary
safety.fs.writeText(outPath, tail.join('\n'));
return { ok: true, source: latest, matches: hits.length, written: outPath };
```

**Test value** - `dir`: `/tmp/logs` (set `TOOL_STUDIO_FS_BASE=/tmp` first), `pattern`: `ERROR`.

**Chat prompt** - *"Summarise the recent errors in /tmp/logs."*

---

## What to validate

For each recipe:

- The `Drafts` badge disappears as soon as Local Pass succeeds - the tool moves to `LOCAL PASS` in the sidebar and the MCP server's Tools tab updates without restart.
- The Agentic Chat inventory shows the new tool name when MCP is reconnected.
- The action invocation appears as a single MCP tool call in the chat trace, not several - that's the whole point of in-action composition.
- Risk badge matches your sandbox edits: Recipe 5 = L4, Recipes 1-4 = L0 (no overrides) or L3 if you tightened anything.

## Where to go from here

- **Two-layer composition** - call a tool you just made from another new tool. Example: a `multiRepoDigest` that loops over `[{ owner, repo }, ...]`, calls `releaseRadar` per row, and rolls the summaries into a single Slack post.
- **Move from JS chain to model chain** - re-implement Recipe 3 as a Tutorial-7-style agent loop. Use `geocodeAddress` and `getOpenMeteoForecast` directly from the preset and let the model decide. Compare the trace: one MCP tool call vs two.
- **Stress the cross-platform property** - all five recipes use only JVM-backed helpers (`fetch`, `safety.fs.*`). The same JS works on macOS, Windows, and Linux. See [Tool Studio: Cross-platform by design](../features/tool-studio/index.md#cross-platform-by-design).

