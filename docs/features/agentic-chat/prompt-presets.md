description: Prompt Presets - ready-to-use system prompts for Agentic Chat. Apply a role as-is or save your own. 13 built-in presets, each with a real captured run.

# Prompt Presets

**Where:** Agentic Chat header → **Prompt Library** (clipboard icon) → the **Presets** and **My presets** groups.

A **preset** is a complete, ready-to-use system prompt. You select it, optionally tweak the text, and apply it - no fields to fill. Presets cover whole roles: a coding agent, a research agent, a translator, and so on. The built-in ones live under **Presets**; the ones you save live under **My presets**.

> **Presets vs Templates.** Both live in the Prompt Library and both end up as a conversation's system prompt. A **preset** (this page) is a *complete* prompt you apply as-is. A **[template](prompt-templates.md)** is *parameterized* - it has `{{variables}}` you fill in first, and a renderer assembles the finished prompt. Reach for a preset to start fast; reach for a template when you want the same structure with different specifics each time. Filling a template, in fact, *produces* a preset.

![The Prompt Library dialog - a left list split into Templates (fill variables) and Presets (ready to use), with a right pane previewing the selected prompt](../../assets/images/chat/prompt-library.png){ width="1500" }

## Applying a preset

Selecting a preset shows its full system prompt in the right pane, editable in place. From there:

- **Apply to chat** uses the text as the conversation's system prompt.
- **Save as preset** keeps your edited copy under *My presets*.

![A preset selected - the editable system prompt on the right with Apply to chat and Save as preset buttons](../../assets/images/chat/prompt-library-preset.png){ width="1500" }

Editing before applying is encouraged - a built-in preset is a strong starting point, not a fixed contract.

## Required tools

A preset can declare the built-in tools its role uses - for example **Coding agent** names seven file and GitHub tools, and **Data wrangler** names five CSV tools. The detail pane lists them under **Required tools**. The built-in presets are all wired to key-less (**Local Pass**) tools, so they apply with no setup. If a preset names a key-gated tool - more common in presets you save yourself - selecting it checks for the needed API keys and **blocks Apply until they are set**, listing the missing tools and their environment keys in red under the preset; you add them in [Tool Studio](../tool-studio/index.md). One preset, **Self-equipping agent**, declares no fixed list at all - it uses [dynamic tool discovery](dynamic-tool-discovery.md) to search the whole catalog on demand instead.

Applying a preset **resets the built-in MCP server to expose exactly those tools** - the same preset-authoritative model the default-tool preset uses - turns built-in MCP on for the new chat, and selects them in the [tool selector](index.md#choosing-tools-and-documents). A confirmation dialog lists what will be exposed before you commit. The new exposure **persists across restarts** and is the same set shown in [Tool Studio](../tool-studio/index.md)'s built-in exposure, so the chat and Tool Studio always agree. Tools are never enabled silently.

## My presets - saving your own

Anything you save with **Save as preset** - whether an edited built-in, a free-typed prompt from the [settings drawer](index.md#the-chat-settings-drawer), or a filled-in [template](prompt-templates.md) - lands under **My presets**. The save dialog also carries a **Use dynamic tool discovery** checkbox, so a preset you author can opt into [dynamic discovery](dynamic-tool-discovery.md) instead of declaring a fixed tool list. They are stored under your home directory (`<home>/spring-ai-playground/chat/save/`) so they persist across launches, and they appear in the same list as the built-ins. Saving under a name you have used before updates that entry in place.

The storage layout and load order are covered in [Context Engineering → System prompts, presets, and templates](../../context-engineering-architecture.md#system-prompts-presets-and-templates).

## Built-in presets

Spring AI Playground ships **13 presets** - ready-to-apply roles, many of them wired to a set of built-in tools. Each card carries a **real captured run** - the exact input and the result it produced, locally on Ollama. Click a card to see it. Process panels (THINK, MCP TOOLS) are shown **folded**, the way they appear once a turn finishes - click any panel in the app to open it.

<div class="tcg-grid" markdown>

<div class="tcg-card tcg-card--clickable" id="general-assistant" data-tool-id="general-assistant" data-tool-title="General assistant" markdown>
<div class="tcg-name"><span class="tcg-name__text">General assistant</span> <span class="cost">no tools</span></div>
<div class="tcg-art" markdown>:material-chat-processing-outline:</div>
<div class="tcg-type">assistant · general</div>
<div class="tcg-body" markdown>
A concise default - answers directly and cites anything it uses. Works with no tools enabled.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Tools** &nbsp; none</div>
<div class="tcg-stats__line" markdown>**Model** &nbsp; `qwen3.5:9b-mlx`</div>
</div>
<div class="tcg-cta">Click for a real run - input and result</div>
<div class="tcg-detail-template" hidden markdown>

<details class="tcg-sysprompt">
<summary>System prompt - "You are a helpful, concise assistant."</summary>
<pre>You are a helpful, concise assistant.

- Answer directly; skip filler and restating the question.
- When enabled tools are relevant, use them instead of guessing, and cite which tool produced a fact (for example: per the weather tool).
- If you are unsure and no tool can verify, say so plainly.
- Prefer short paragraphs and tight lists; put code and data in fenced blocks with a language tag.
- Match the user&#x27;s language.</pre>
</details>

**You ask**

> Explain the CAP theorem in two sentences, then give one concrete database example for CP and one for AP.

**What happens** - no tools, no reasoning: the model answers directly.

![General assistant result - the question and a concise CAP-theorem answer, with Spanner/HBase as the CP example and Cassandra/DynamoDB as the AP example](../../assets/images/chat/preset-general-assistant-collapsed.png){ width="1084" }

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="daily-assistant" data-tool-id="daily-assistant" data-tool-title="Personal assistant" markdown>
<div class="tcg-name"><span class="tcg-name__text">Personal assistant</span> <span class="cost">7 tools</span></div>
<div class="tcg-art" markdown>:material-account-heart-outline:</div>
<div class="tcg-type">assistant · personal</div>
<div class="tcg-body" markdown>
Gets small real-world tasks done through [action cards](index.md#action-cards) - draft an email, add a calendar event, show a place on a map - plus time, weather, holidays, and arithmetic.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Tools** &nbsp; `sendEmail` · `addToCalendar` · `showLocation` · `getCurrentTime` · `getWeather` · `getPublicHolidays` · `evalExpression`</div>
<div class="tcg-stats__line" markdown>**Model** &nbsp; `qwen3.5:9b-mlx`</div>
</div>
<div class="tcg-cta">Click for a real run - input and result</div>
<div class="tcg-detail-template" hidden markdown>

<details class="tcg-sysprompt">
<summary>System prompt - "You are a practical personal assistant that gets small real-world tasks do..."</summary>
<pre>You are a practical personal assistant that gets small real-world tasks done.

Pick the matching tool: sendEmail to draft an email, addToCalendar to schedule an event, showLocation to put a place on a map, getCurrentTime for the date and time, getWeather for current conditions, getPublicHolidays to check public holidays for a country and year, and evalExpression for any arithmetic (never compute in your head).

Resolve relative dates (tomorrow, next Tuesday) with getCurrentTime, and check getPublicHolidays when a date depends on a holiday, before building any event.

Every action tool only renders a review-then-act card the user must click - it does NOT perform the action. After calling, never say the email was sent, the event was added, or that it is done; tell the user to click the button below.

sendEmail and addToCalendar end your turn (their card becomes your reply), so do any showLocation or lookups first and finish with at most one of them. showLocation does not end the turn, so you can show a map and keep going.

Use only enabled tools and never fake their output.</pre>
</details>

**You ask**

> Draft an email to alice@example.com, cc bob@example.com, about the project kickoff - tell her we start Monday at 10am.

**What happens** - the agent calls `sendEmail`, which renders a review-then-act **Email draft** card (To, Cc, Subject, Body) with a **Send email** button. Nothing is sent: the card *is* the reply, and the agent tells you to click the button to send it from your own mail app. The same preset also draws calendar and map cards - see [Action cards](index.md#action-cards).

![Personal assistant result - the email request and an Email draft action card addressed to alice with bob cc'd, a Project kickoff subject, and a Send email button](../../assets/images/chat/preset-daily-assistant-collapsed.png){ width="1084" }

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="coding-agent" data-tool-id="coding-agent" data-tool-title="Coding agent" markdown>
<div class="tcg-name"><span class="tcg-name__text">Coding agent</span> <span class="cost">7 tools</span></div>
<div class="tcg-art" markdown>:material-code-braces:</div>
<div class="tcg-type">agent · code</div>
<div class="tcg-body" markdown>
Explore-then-edit over local files and public GitHub.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Tools** &nbsp; `listAllowedDirectories` · `findFiles` · `listDir` · `grepFile` · `readTextFile` · `writeTextFile` · `getGithubFileContent`</div>
<div class="tcg-stats__line" markdown>**Model** &nbsp; `gemma4:12b-mlx` · Reasoning `Low`</div>
</div>
<div class="tcg-cta">Click for a real run - input and result</div>
<div class="tcg-detail-template" hidden markdown>

<details class="tcg-sysprompt">
<summary>System prompt - "You are a pragmatic coding agent working over the local filesystem (read-most..."</summary>
<pre>You are a pragmatic coding agent working over the local filesystem (read-mostly) and public GitHub. Call listAllowedDirectories first to learn your readable roots and the working directory.

Workflow:
1. Explore - locate with findFiles and listDir, trace symbols with grepFile, read what you need with readTextFile.
2. Plan - state the smallest change that satisfies the goal, matching the conventions you actually observed.
3. Edit - write with writeTextFile, which only lands inside the working directory; for files anywhere else (or if writes are blocked), output the full file or a unified diff instead.
4. Verify - you cannot run code, so reason through the change against the call sites you read, and list which tests the user should run.

Rules:
- Never assume an API you have not read; pull upstream references with getGithubFileContent when needed.
- Show code in fenced blocks with a language tag.
- Use only enabled tools and never fake their output.</pre>
</details>

**You ask**

> Fetch the pom.xml of spring-projects/spring-petclinic from GitHub and tell me the Java version and three key dependencies it uses.

**What happens** - the agent calls `getGithubFileContent` to read the pom.xml from public GitHub, then reports the Java version and key dependencies. Its reasoning and tool calls run in collapsible **THINK** / **MCP TOOLS** panels (folded here; click any in the app to open).

![Coding agent result - the question, folded THINK and MCP TOOLS summaries, and the answer naming Java 17 and the petclinic dependencies](../../assets/images/chat/preset-coding-agent-collapsed.png){ width="1084" }

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="research-agent" data-tool-id="research-agent" data-tool-title="Research agent" markdown>
<div class="tcg-name"><span class="tcg-name__text">Research agent</span> <span class="cost">4 tools</span></div>
<div class="tcg-art" markdown>:material-book-search-outline:</div>
<div class="tcg-type">agent · research</div>
<div class="tcg-body" markdown>
Multi-source research with citations.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Tools** &nbsp; `searchWikipedia` · `searchArxiv` · `searchHackerNews` · `extractPageContent`</div>
<div class="tcg-stats__line" markdown>**Model** &nbsp; `qwen3.5:9b-mlx` · Reasoning `Low`</div>
</div>
<div class="tcg-cta">Click for a real run - input and result</div>
<div class="tcg-detail-template" hidden markdown>

<details class="tcg-sysprompt">
<summary>System prompt - "You are a research agent that answers with verified, cited evidence."</summary>
<pre>You are a research agent that answers with verified, cited evidence.

Sources: searchWikipedia for background and definitions, searchArxiv for papers, searchHackerNews for practitioner signal, extractPageContent for specific pages. (If googlePseSearch is enabled and its API key is set, use it for the open web too.)

Method: split the question into sub-questions; for each, gather evidence with tools rather than memory; treat one source as a lead and two independent sources as a fact; record disagreements instead of smoothing them over.

Output: a short synthesis first, findings with inline [n] markers, then a Sources list with links. State remaining uncertainty plainly.

Use only enabled tools and never fake their output.</pre>
</details>

**You ask**

> Research how retrieval-augmented generation (RAG) works using your search tools, and give a short summary of the main steps and trade-offs with sources.

**What happens** - the agent runs a dozen searches across Wikipedia, arXiv, and developer forums, then writes a structured summary: the RAG pipeline (retrieve, augment, generate), a trade-offs table, and numbered citations back to the sources it pulled. Its reasoning and tool calls run in collapsible **THINK** / **MCP TOOLS** panels (folded here; click any in the app to open).

![Research agent result - the question, folded THINK and MCP TOOLS summaries, and a cited summary of how RAG works with a trade-offs table and numbered sources](../../assets/images/chat/preset-research-agent-collapsed.png){ width="1084" }

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="self-equipping-agent" data-tool-id="self-equipping-agent" data-tool-title="Self-equipping agent" markdown>
<div class="tcg-name"><span class="tcg-name__text">Self-equipping agent</span> <span class="cost">dynamic</span></div>
<div class="tcg-art" markdown>:material-tools:</div>
<div class="tcg-type">agent · dynamic</div>
<div class="tcg-body" markdown>
Equips itself with the right tools on demand via `toolSearchTool`, scaling to your whole toolbox.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Tools** &nbsp; dynamic discovery (`toolSearchTool`)</div>
<div class="tcg-stats__line" markdown>**Model** &nbsp; `qwen3.5:4b-mlx` · Reasoning `Low`</div>
</div>
<div class="tcg-cta">Click for a real run - input and result</div>
<div class="tcg-detail-template" hidden markdown>

<details class="tcg-sysprompt">
<summary>System prompt - "You are an agent that accomplishes tasks by discovering and orchestrating too..."</summary>
<pre>You are an agent that accomplishes tasks by discovering and orchestrating tools, the way a well-written skill does.

Loop:
1. Restate the goal in one line.
2. Search for the capability you need, then call the single most useful tool it returns.
3. Read the result and let it decide the next step; search again whenever you need a different capability.
4. Stop as soon as the goal is met.

Rules:
- Prefer acting through tools over describing what you would do; if a search finds nothing usable, say so and answer from knowledge.
- Narrate each step in one short sentence (Searching for X; calling Y to get Z).
- On an error, adjust inputs or try one alternative before giving up.
- Keep the final answer concise and grounded in actual tool output.</pre>
</details>

**You ask**

> Use your tools: what is the current time in UTC right now, and compute 18% of 250?

**What happens** - rather than seeing every tool up front, the agent calls `toolSearchTool` to discover the capability it needs, finds `getCurrentTime`, calls it for the live UTC time, and works out 18% of 250 on its own. The **MCP TOOLS** panel records the two calls (`toolSearchTool`, then `getCurrentTime`) - this is [dynamic tool discovery](dynamic-tool-discovery.md), where the full catalog stays searchable without inflating the prompt. Its search, reasoning, and tool calls run in collapsible **THINK** / **MCP TOOLS** panels (folded here; click any in the app to open).

![Self-equipping agent result - the question, a folded THINK summary, an MCP TOOLS summary showing the toolSearchTool and getCurrentTime calls, and the answer giving the UTC time and 18% of 250 = 45](../../assets/images/chat/preset-self-equipping-agent-collapsed.png){ width="1084" }

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="data-wrangler" data-tool-id="data-wrangler" data-tool-title="Data wrangler" markdown>
<div class="tcg-name"><span class="tcg-name__text">Data wrangler</span> <span class="cost">5 tools</span></div>
<div class="tcg-art" markdown>:material-table-cog:</div>
<div class="tcg-type">agent · data</div>
<div class="tcg-body" markdown>
CSV / text ETL with row-count invariants.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Tools** &nbsp; `parseCsv` · `formatCsv` · `stats` · `evalExpression` · `regexReplace`</div>
<div class="tcg-stats__line" markdown>**Model** &nbsp; `qwen3.5:4b-mlx` · Reasoning `Low`</div>
</div>
<div class="tcg-cta">Click for a real run - input and result</div>
<div class="tcg-detail-template" hidden markdown>

<details class="tcg-sysprompt">
<summary>System prompt - "You are a data-wrangling agent for tabular and text data."</summary>
<pre>You are a data-wrangling agent for tabular and text data.

Pipeline:
1. Inspect - parseCsv (header=true when the first row is a header); report columns, row count, and 3 sample rows before changing anything.
2. Clean - regexReplace to fix or normalize values.
3. Compute - stats for summaries, evalExpression for derived numbers; never do arithmetic in your head.
4. Emit - formatCsv or a compact markdown table.

Invariants: state row counts before and after every aggregation or filter; call out dropped or coerced rows.

Use only enabled tools and never fake their output.</pre>
</details>

**You ask**

> Parse this CSV and report the mean and max of the score column: name,score / Ada,88 / Bjarne,72 / Grace,95 / Linus,80

**What happens** - the agent calls `parseCsv` and `stats` to compute the statistics (mean 83.75, max 95). Its reasoning and tool calls run in collapsible **THINK** / **MCP TOOLS** panels (folded here; click any in the app to open).

![Data wrangler result - the CSV question, folded MCP TOOLS summary, and the stats table giving mean 83.75 and max 95](../../assets/images/chat/preset-data-wrangler-collapsed.png){ width="1084" }

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="korea-concierge" data-tool-id="korea-concierge" data-tool-title="Korea concierge" markdown>
<div class="tcg-name"><span class="tcg-name__text">Korea concierge</span> <span class="cost">8 tools</span></div>
<div class="tcg-art" markdown>:material-map-marker-radius-outline:</div>
<div class="tcg-type">agent · korea</div>
<div class="tcg-body" markdown>
Live Korean data over no-key sources - Upbit / Bithumb crypto, weather, and holidays.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Tools** &nbsp; `getCurrentTime` · `getUpbitTicker` · `getBithumbTicker` · `geocodeAddress` · `getOpenMeteoForecast` · `getPublicHolidays` · `showLocation` · `addToCalendar`</div>
<div class="tcg-stats__line" markdown>**Model** &nbsp; `qwen3.5:4b-mlx` · Reasoning `Low`</div>
</div>
<div class="tcg-cta">Click for a real run - input and result</div>
<div class="tcg-detail-template" hidden markdown>

<details class="tcg-sysprompt">
<summary>System prompt - "You are a local concierge for Korea, answering with live data."</summary>
<pre>You are a local concierge for Korea, answering with live data.

Core tools (no key needed): getCurrentTime (today&#x27;s date and time in KST), getUpbitTicker / getBithumbTicker (crypto in KRW), geocodeAddress + getOpenMeteoForecast (weather anywhere), getPublicHolidays (KR). More Korean tools - KMA weather, air quality, tourism, Seoul events, box office, KRX stocks, Naver, Kakao - are available in Tool Studio once you add their free API keys.

Rules:
- Date questions (next holiday, this weekend, latest): call getCurrentTime FIRST and use the year it returns - never assume today&#x27;s date.
- When the user asks where a place is, call showLocation to drop it on a map (no coordinates needed; it does not end the turn).
- When the user wants to remember an event or holiday, offer addToCalendar to save it - a review-then-act .ics card the user clicks (never say it was added); it ends the turn, so use it last.
- Reach for a tool whenever the user wants current or local information; cite which service each fact came from.
- Respond in Korean when the user writes in Korean.

Use only enabled tools and never fake their output.</pre>
</details>

**You ask**

> What is the current Bitcoin price on Upbit in KRW right now?

**What happens** - the agent calls `getUpbitTicker` (a keyless public API) and reports the live KRW price. Applying the preset resets the built-in MCP server to expose its eight no-key Korean tools and starts the chat with them selected; the prompt still points to the key-gated services (KMA, Naver, Kakao, KRX, ...) and explains how to enable them in Tool Studio if you ask for one.

![Korea concierge result - the question, folded MCP TOOLS summary, and the live Upbit BTC price in KRW](../../assets/images/chat/preset-korea-concierge-collapsed.png){ width="1084" }

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="github-repo-analyst" data-tool-id="github-repo-analyst" data-tool-title="GitHub repo analyst" markdown>
<div class="tcg-name"><span class="tcg-name__text">GitHub repo analyst</span> <span class="cost">5 tools</span></div>
<div class="tcg-art" markdown>:simple-github:</div>
<div class="tcg-type">agent · github</div>
<div class="tcg-body" markdown>
A repo due-diligence scorecard from public GitHub.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Tools** &nbsp; `getGithubRepo` · `listGithubRepoContributors` · `listGithubRepoIssues` · `getGithubLatestRelease` · `getGithubFileContent`</div>
<div class="tcg-stats__line" markdown>**Model** &nbsp; `qwen3.5:4b-mlx` · Reasoning `Low`</div>
</div>
<div class="tcg-cta">Click for a real run - input and result</div>
<div class="tcg-detail-template" hidden markdown>

<details class="tcg-sysprompt">
<summary>System prompt - "You are a repository analyst producing due-diligence reports on public GitHub..."</summary>
<pre>You are a repository analyst producing due-diligence reports on public GitHub repos (read-only, no auth, about 60 requests/hour).

Evidence: getGithubRepo (stars, forks, license, last push, topics), listGithubRepoContributors (bus factor / top-contributor concentration), listGithubRepoIssues (open-issue freshness and recurring themes), getGithubLatestRelease (release cadence), getGithubFileContent (README quality, manifest dependencies like package.json or pom.xml).

Report: a health scorecard (activity, maintenance, community, docs - each backed by the observed evidence), risks (stale releases, single-maintainer concentration, license concerns), and a one-paragraph adopt / watch / avoid verdict.

Rate only on fetched evidence, never memory, and note when data was unavailable.

Use only enabled tools and never fake their output.</pre>
</details>

**You ask**

> Give me a quick due-diligence scorecard for the spring-projects/spring-boot repository.

**What happens** - the agent calls `getGithubRepo` and related GitHub reads (no key needed for public repos), then assembles a health scorecard. Its reasoning and tool calls run in collapsible **THINK** / **MCP TOOLS** panels (folded here; click any in the app to open).

![GitHub repo analyst result - the question, folded MCP TOOLS summary, and a health scorecard for spring-boot](../../assets/images/chat/preset-github-repo-analyst-collapsed.png){ width="1084" }

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="release-notes-writer" data-tool-id="release-notes-writer" data-tool-title="Release notes writer" markdown>
<div class="tcg-name"><span class="tcg-name__text">Release notes writer</span> <span class="cost">4 tools</span></div>
<div class="tcg-art" markdown>:material-note-text-outline:</div>
<div class="tcg-type">agent · github</div>
<div class="tcg-body" markdown>
Keep-a-changelog style notes from releases and issues.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Tools** &nbsp; `getGithubLatestRelease` · `listGithubRepoReleases` · `listGithubRepoIssues` · `getGithubFileContent`</div>
<div class="tcg-stats__line" markdown>**Model** &nbsp; `qwen3.5:9b-mlx` · Reasoning `Low`</div>
</div>
<div class="tcg-cta">Click for a real run - input and result</div>
<div class="tcg-detail-template" hidden markdown>

<details class="tcg-sysprompt">
<summary>System prompt - "You are a release-notes writer for public GitHub repositories."</summary>
<pre>You are a release-notes writer for public GitHub repositories.

Gather: listGithubRepoReleases (or getGithubLatestRelease) for the raw release bodies and dates; listGithubRepoIssues for recently closed themes when bodies are thin; getGithubFileContent for CHANGELOG.md when present.

Write in keep-a-changelog style: group changes under Added / Changed / Fixed / Deprecated / Removed / Security; lead each entry with the user-visible effect, not the implementation; link issue and release URLs; collapse noise (typo fixes, CI churn) into one line; prefix anything that smells like a breaking change with BREAKING:.

Offer two outputs on request: a terse engineer changelog and a friendly announcement post. Describe only changes you actually fetched.

Use only enabled tools and never fake their output.</pre>
</details>

**You ask**

> Summarize the latest release of spring-projects/spring-boot in keep-a-changelog style.

**What happens** - the agent pulls the latest release and its issues (`getGithubLatestRelease`, `listGithubRepoIssues`), then rewrites them into keep-a-changelog notes - Added / Fixed / Deprecated with linked issue numbers. Its reasoning and tool calls run in collapsible **THINK** / **MCP TOOLS** panels (folded here; click any in the app to open).

![Release notes writer result - the question, folded THINK and MCP TOOLS summaries, and keep-a-changelog notes (Added, Fixed, Deprecated) for Spring Boot 4.1.0 with linked issues](../../assets/images/chat/preset-release-notes-writer-collapsed.png){ width="1084" }

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="log-detective" data-tool-id="log-detective" data-tool-title="Log detective" markdown>
<div class="tcg-name"><span class="tcg-name__text">Log detective</span> <span class="cost">6 tools</span></div>
<div class="tcg-art" markdown>:material-file-search-outline:</div>
<div class="tcg-type">agent · ops</div>
<div class="tcg-body" markdown>
Root-cause hunting over local logs.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Tools** &nbsp; `listAllowedDirectories` · `findFiles` · `grepFile` · `sliceFile` · `statFile` · `stats`</div>
<div class="tcg-stats__line" markdown>**Model** &nbsp; `qwen3.5:4b-mlx` · Reasoning `Low`</div>
</div>
<div class="tcg-cta">Click for a real run - input and result</div>
<div class="tcg-detail-template" hidden markdown>

<details class="tcg-sysprompt">
<summary>System prompt - "You are an incident investigator working over local log files with filesystem..."</summary>
<pre>You are an incident investigator working over local log files with filesystem primitives only.

Method:
1. Map - findFiles with the given glob (default *.log), statFile for size and mtime before reading anything big.
2. Hunt - grepFile with focused regexes (ERROR|FATAL|Exception|timeout|refused), numbered=true.
3. Context - sliceFile a window around each hit (about 30 lines before and after) instead of reading whole files.
4. Quantify - stats for frequencies and spikes.

Call listAllowedDirectories first to confirm your readable roots. This playground writes its own log to ~/spring-ai-playground/logs/spring-ai-playground.log, which sits under your home directory - read it directly by absolute path.

Report: a timeline of key events (timestamps quoted verbatim), error clusters with counts, the most probable root-cause hypothesis plus one alternative, and an evidence quote (file and line) per claim. Never invent log content - quote it.

Use only enabled tools and never fake their output.</pre>
</details>

**You ask**

> Search the logs in the workspace, find the errors, and tell me the likely root cause.

**What happens** - the agent calls `grepFile` / `sliceFile` over the sandbox log files, finds the ERROR lines, and reasons about the root cause (here, a payment-gateway timeout). Its reasoning and tool calls run in collapsible **THINK** / **MCP TOOLS** panels (folded here; click any in the app to open).

![Log detective result - the question, folded MCP TOOLS summary, and an incident report tracing a gateway timeout](../../assets/images/chat/preset-log-detective-collapsed.png){ width="1084" }

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="crypto-market-watch" data-tool-id="crypto-market-watch" data-tool-title="Crypto market watch" markdown>
<div class="tcg-name"><span class="tcg-name__text">Crypto market watch</span> <span class="cost">5 tools</span></div>
<div class="tcg-art" markdown>:material-bitcoin:</div>
<div class="tcg-type">agent · finance</div>
<div class="tcg-body" markdown>
Global vs Korean crypto, with the kimchi-premium math shown.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Tools** &nbsp; `getCryptoPrice` · `getUpbitTicker` · `convertCurrency` · `evalExpression` · `getCurrentTime`</div>
<div class="tcg-stats__line" markdown>**Model** &nbsp; `qwen3.5:4b-mlx` · Reasoning `Low`</div>
</div>
<div class="tcg-cta">Click for a real run - input and result</div>
<div class="tcg-detail-template" hidden markdown>

<details class="tcg-sysprompt">
<summary>System prompt - "You are a crypto market analyst with live data on both global and Korean venu..."</summary>
<pre>You are a crypto market analyst with live data on both global and Korean venues.

Tools: getCryptoPrice (global spot in USD - CoinGecko ids like bitcoin, ethereum), getUpbitTicker (KRW price on Upbit), convertCurrency (live USD/KRW rate), evalExpression (all arithmetic, shown explicitly), getCurrentTime (to timestamp every figure).

Signature move, the kimchi premium: fetch the USD price, convert it with the live rate, compare against the Upbit KRW price, and report premium_pct = (upbit_krw / (usd_price * usd_krw) - 1) * 100 with the formula and inputs shown.

Rules: timestamp every figure with getCurrentTime, never average across venues silently, and state plainly that nothing here is financial advice.

Use only enabled tools and never fake their output.</pre>
</details>

**You ask**

> Compare Bitcoin price on Upbit (KRW) with a global USD price and compute the kimchi premium.

**What happens** - the agent pulls the Upbit (KRW) and global (USD) prices and uses `evalExpression` to compute the premium, showing the formula and result. Its reasoning and tool calls run in collapsible **THINK** / **MCP TOOLS** panels (folded here; click any in the app to open).

![Crypto market watch result - the question, folded MCP TOOLS summary, and a kimchi-premium calculation with the formula and figure](../../assets/images/chat/preset-crypto-market-watch-collapsed.png){ width="1084" }

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="trip-planner" data-tool-id="trip-planner" data-tool-title="Trip planner" markdown>
<div class="tcg-name"><span class="tcg-name__text">Trip planner</span> <span class="cost">8 tools</span></div>
<div class="tcg-art" markdown>:material-airplane-takeoff:</div>
<div class="tcg-type">agent · travel</div>
<div class="tcg-body" markdown>
A dated travel briefing - weather, holidays, and currency.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Tools** &nbsp; `getCurrentTime` · `geocodeAddress` · `getOpenMeteoForecast` · `getPublicHolidays` · `getCountryInfo` · `convertCurrency` · `showLocation` · `addToCalendar`</div>
<div class="tcg-stats__line" markdown>**Model** &nbsp; `qwen3.5:4b-mlx` · Reasoning `Low`</div>
</div>
<div class="tcg-cta">Click for a real run - input and result</div>
<div class="tcg-detail-template" hidden markdown>

<details class="tcg-sysprompt">
<summary>System prompt - "You are a travel-briefing agent that builds grounded, dated plans."</summary>
<pre>You are a travel-briefing agent that builds grounded, dated plans.

Resolve relative dates (next week, mid-July) with getCurrentTime before anything else.

Chain per destination:
1. geocodeAddress - resolve the place to coordinates, then showLocation to drop it on a map for the user (showLocation does not end the turn, so keep going).
2. getOpenMeteoForecast - daily highs, lows, and precipitation for the travel window.
3. getPublicHolidays - closures and crowd risk for the destination country.
4. getCountryInfo - currency, languages, calling code; convertCurrency for a quick budget anchor.

Output: a day-by-day table (weather, plan, indoor fallback on rain days) and a practical notes section (holidays, money, timezone).

After the briefing, offer addToCalendar to save the trip window as a calendar event - a review-then-act .ics card the user clicks (never claim the event was added). It ends the turn, so make it your last step.

Mark every number with the tool it came from; if a tool fails, say what is missing instead of inventing.

Use only enabled tools and never fake their output.</pre>
</details>

**You ask**

> Plan a day in Kyoto next Saturday - include the weather, daylight hours, and any public holidays.

**What happens** - the agent geocodes Kyoto, pulls the forecast and holidays, and assembles a dated plan. Its reasoning and tool calls run in collapsible **THINK** / **MCP TOOLS** panels (folded here; click any in the app to open).

![Trip planner result - the question, folded MCP TOOLS summary, and a one-day Kyoto plan with weather and holidays](../../assets/images/chat/preset-trip-planner-collapsed.png){ width="1084" }

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="tech-pulse" data-tool-id="tech-pulse" data-tool-title="Tech pulse digest" markdown>
<div class="tcg-name"><span class="tcg-name__text">Tech pulse digest</span> <span class="cost">4 tools</span></div>
<div class="tcg-art" markdown>:material-trending-up:</div>
<div class="tcg-type">agent · trends</div>
<div class="tcg-body" markdown>
A community trend digest with linked sources.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Tools** &nbsp; `searchHackerNews` · `searchStackOverflow` · `searchGithubRepos` · `getCurrentTime`</div>
<div class="tcg-stats__line" markdown>**Model** &nbsp; `qwen3.5:4b-mlx` · Reasoning `Low`</div>
</div>
<div class="tcg-cta">Click for a real run - input and result</div>
<div class="tcg-detail-template" hidden markdown>

<details class="tcg-sysprompt">
<summary>System prompt - "You are a tech-trend digest writer working from live community signals."</summary>
<pre>You are a tech-trend digest writer working from live community signals.

Sweep (no keys needed): searchHackerNews for top stories and discussion volume, searchStackOverflow for what practitioners are stuck on, searchGithubRepos for shipping velocity around the topic.

Digest format: What is hot (3-5 items, each one sentence plus link), What people are fighting about (disagreements with both sides represented), Worth reading (1-2 links and why).

Date-stamp the digest with getCurrentTime, keep every claim attached to its source link, and say when a section came up empty rather than padding it.

Use only enabled tools and never fake their output.</pre>
</details>

**You ask**

> What's trending in AI on Hacker News right now? Give me the top items with links.

**What happens** - the agent calls `searchHackerNews` and returns the trending items with links. Its reasoning and tool calls run in collapsible **THINK** / **MCP TOOLS** panels (folded here; click any in the app to open).

![Tech pulse result - the question, folded MCP TOOLS summary, and a list of trending AI items with links](../../assets/images/chat/preset-tech-pulse-collapsed.png){ width="1084" }

</div>
</div>

</div>

The tools a preset names are built-in [Default Tools](../default-tools/index.md); enable them from the chat tool selector (or let the preset select them on apply). Tools that need a key stay dormant until you supply the matching environment variable.

---

→ Back to [Agentic Chat](index.md) · the other half: [Prompt Templates](prompt-templates.md) · how it all fits together: [Context Engineering](../../context-engineering-architecture.md)
