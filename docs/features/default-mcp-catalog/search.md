description: Default MCP Catalog — Search reference: 6 preset MCP connections with transport, auth defaults, required env, and full description per card.

# Default MCP Catalog — Search

Web search and content retrieval. Remote services (Tavily, Exa, Firecrawl, Jina AI, SerpAPI) expect a vendor API key via `${VAR}` placeholders in the headers; the community stdio entry (`Brave Search`) needs `BRAVE_API_KEY` in the `env` section. All five remote services have a free tier sufficient for tutorial-scale use.

## Entries (6)

Click any card to expand the full spec inline — transport (Streamable HTTP / STDIO), authentication shape (OAuth 2.1 / API key / Bearer / none), required environment variables, vendor URL or stdio command, and the upstream docs link.

<div class="tcg-grid" markdown>

<div class="tcg-card tcg-card--clickable" id="Tavily" data-tool-id="Tavily" data-tool-title="Tavily" markdown>
<div class="tcg-name"><span class="tcg-name__text">Tavily</span> <span class="cost">🔑</span></div>
<div class="tcg-art" markdown>:material-magnify-scan:</div>
<div class="tcg-type">search · global <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
AI-optimised web search and answer engine for grounding LLMs with up-to-date facts and sources. Requires TAVILY_API_KEY.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Tavily · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;API key</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Tavily (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://mcp.tavily.com/mcp`

**Auth** — API key

**Stability** — GA · **Tier** — Tier 1

**Required env** — `TAVILY_API_KEY`

**Tags** — global

**Description**

AI-optimised web search and answer engine for grounding LLMs with up-to-date facts and sources. Requires TAVILY_API_KEY.

Docs: https://docs.tavily.com/documentation/mcp

**Docs** — [https://docs.tavily.com/documentation/mcp](https://docs.tavily.com/documentation/mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Exa" data-tool-id="Exa" data-tool-title="Exa" markdown>
<div class="tcg-name"><span class="tcg-name__text">Exa</span> <span class="cost">🔑</span></div>
<div class="tcg-art" markdown>:material-magnify-expand:</div>
<div class="tcg-type">search · global <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Neural web search by Exa — semantic ranking, source-aware retrieval, and high-quality result snippets. Requires EXA_API_KEY (x-api-key header).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Exa · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;API key</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Exa (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://mcp.exa.ai/mcp`

**Auth** — API key

**Stability** — GA · **Tier** — Tier 1

**Required env** — `EXA_API_KEY`

**Tags** — global

**Description**

Neural web search by Exa — semantic ranking, source-aware retrieval, and high-quality result snippets. Requires EXA_API_KEY (x-api-key header).

Docs: https://exa.ai/docs/reference/exa-mcp

**Docs** — [https://exa.ai/docs/reference/exa-mcp](https://exa.ai/docs/reference/exa-mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Firecrawl" data-tool-id="Firecrawl" data-tool-title="Firecrawl" markdown>
<div class="tcg-name"><span class="tcg-name__text">Firecrawl</span> <span class="cost">🔑</span></div>
<div class="tcg-art" markdown>:material-spider-web:</div>
<div class="tcg-type">search · global <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Web scraping, crawling, and structured data extraction with anti-bot handling. Bearer-authenticated with FIRECRAWL_API_KEY.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Firecrawl · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;Bearer</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Firecrawl (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://mcp.firecrawl.dev/v2/mcp`

**Auth** — Bearer

**Stability** — GA · **Tier** — Tier 1

**Required env** — `FIRECRAWL_API_KEY`

**Tags** — global

**Description**

Web scraping, crawling, and structured data extraction with anti-bot handling. Bearer-authenticated with FIRECRAWL_API_KEY.

Docs: https://docs.firecrawl.dev/mcp-server

**Docs** — [https://docs.firecrawl.dev/mcp-server](https://docs.firecrawl.dev/mcp-server)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Jina-AI" data-tool-id="Jina-AI" data-tool-title="Jina AI" markdown>
<div class="tcg-name"><span class="tcg-name__text">Jina AI</span> <span class="cost">🔑</span></div>
<div class="tcg-art" markdown>:material-vector-link:</div>
<div class="tcg-type">search · global <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Jina AI Reader (URL-to-clean-markdown) and search APIs for LLM-grounded retrieval. Bearer-authenticated with JINA_API_KEY.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Jina AI · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;Bearer</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Jina AI (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://mcp.jina.ai/v1`

**Auth** — Bearer

**Stability** — GA · **Tier** — Tier 2

**Required env** — `JINA_API_KEY`

**Tags** — global

**Description**

Jina AI Reader (URL-to-clean-markdown) and search APIs for LLM-grounded retrieval. Bearer-authenticated with JINA_API_KEY.

Docs: https://github.com/jina-ai/MCP

**Docs** — [https://github.com/jina-ai/MCP](https://github.com/jina-ai/MCP)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="SerpAPI" data-tool-id="SerpAPI" data-tool-title="SerpAPI" markdown>
<div class="tcg-name"><span class="tcg-name__text">SerpAPI</span> <span class="cost">🔑</span></div>
<div class="tcg-art" markdown>:material-google:</div>
<div class="tcg-type">search · global <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Real-time Google, Bing, Baidu, DuckDuckGo, Naver and other SERP scraping with structured JSON. API key embedded in URL path (SERPAPI_API_KEY).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; SerpAPI · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;API key</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — SerpAPI (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://mcp.serpapi.com/${SERPAPI_API_KEY}/mcp`

**Auth** — API key

**Stability** — GA · **Tier** — Tier 2

**Required env** — `SERPAPI_API_KEY`

**Tags** — global

**Description**

Real-time Google, Bing, Baidu, DuckDuckGo, Naver and other SERP scraping with structured JSON. API key embedded in URL path (SERPAPI_API_KEY).

Docs: https://github.com/serpapi/serpapi-mcp

**Docs** — [https://github.com/serpapi/serpapi-mcp](https://github.com/serpapi/serpapi-mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Brave-Search" data-tool-id="Brave-Search" data-tool-title="Brave Search" markdown>
<div class="tcg-name"><span class="tcg-name__text">Brave Search</span> <span class="cost">🛠</span></div>
<div class="tcg-art" markdown>![Brave Search](https://cdn.simpleicons.org/brave){ width="40" .tcg-favicon }</div>
<div class="tcg-type">search · global <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
[macOS] Brave Search API — web, local, news, image, and video search. Outbound calls only to api.search.brave.com (no SSRF surface). The activated form is pre-filled to run: npx -y…
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Brave · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;STDIO</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Brave (vendor-official)

**Transport** — STDIO

**Command** — `npx`

**Args** — `-y @brave/brave-search-mcp-server`

**OS variants** — mac · linux · win (catalog picks the entry matching the host OS automatically; macOS / Linux use `npx` or `uvx`; Windows uses `npx.cmd`).

**Auth** — STDIO

**Stability** — GA · **Tier** — Tier 2

**Required env** — —

**Tags** — global

**Description**

[macOS] Brave Search API — web, local, news, image, and video search. Outbound calls only to api.search.brave.com (no SSRF surface).

Prereq: Node.js 18+ on macOS (Homebrew, nvm, or installer). Free API key (2k req/month) at https://api.search.brave.com/app/keys.

The activated form is pre-filled to run:
  npx -y @brave/brave-search-mcp-server

Required — set BRAVE_API_KEY in the env section below (treat as a secret).

Docs: https://github.com/brave/brave-search-mcp-server

**Docs** — [https://github.com/brave/brave-search-mcp-server](https://github.com/brave/brave-search-mcp-server)

</div>
</div>

</div>

## Workflow combinations { #combinations }

Five remote search services and one community stdio entry. They're substitutable but each has a different latency / quality / quota profile, so the meaningful "combination" is **choosing one as primary and one as fallback**:

- **Primary + fallback** — `Tavily` (LLM-tuned answers) primary, `Exa` (semantic search) fallback. Tavily fails fast on quota; Exa picks up.
- **Crawl + index** — `Firecrawl` (crawls + structured extract) feeding a downstream Vector Database run. Good when the agent needs the *content* of pages, not just the URLs.
- **Search-engine results** — `SerpAPI` when you specifically want Google / Bing SERP JSON without scraping.
- **Long-form context retrieval** — `Jina AI` for embedding + reader API; the model gets cleaned-up article bodies, not raw HTML.
- **Privacy-first local-stdio** — `Brave Search` (stdio) when you don't want a remote OAuth dance and you have a Brave API key.

## Auth & secrets { #auth-secrets }

All six entries are API-key gated. Set the key as a `${VAR}` placeholder in the form (header for remote, env for stdio):

| Connection | Auth | Env / header |
|---|---|---|
| Tavily | API key | `Authorization: Bearer ${TAVILY_API_KEY}` |
| Exa | API key | `x-api-key: ${EXA_API_KEY}` |
| Firecrawl | Bearer | `Authorization: Bearer ${FIRECRAWL_API_KEY}` |
| Jina AI | Bearer | `Authorization: Bearer ${JINA_API_KEY}` |
| SerpAPI | API key (query param style; configurable as `${SERPAPI_KEY}`) | per-vendor docs |
| Brave Search (stdio) | API key | `env.BRAVE_API_KEY = ${BRAVE_API_KEY}` |

Every remote vendor on this page ships a free tier large enough for tutorial-scale agent use — the free quotas are intended for evaluation, not production, but a single agent session won't exhaust them.

## Picking guide { #picking-guide }

| If you need… | Reach for |
|---|---|
| LLM-shaped answers with citations | `Tavily` |
| Neural / semantic search | `Exa` |
| Crawl + structured extract | `Firecrawl` |
| Embedding-friendly reader API | `Jina AI` |
| Raw SERP JSON | `SerpAPI` |
| Privacy-first, local-process search | `Brave Search` (stdio) |

