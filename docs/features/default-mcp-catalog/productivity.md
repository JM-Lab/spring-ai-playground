description: Default MCP Catalog — Productivity & Communication reference: 8 preset MCP connections with transport, auth, required env, and full description per card.

# Default MCP Catalog — Productivity & Communication

Email, calendar, notes, chat, team messaging — the surfaces an agent most often reaches into on behalf of a user. Every entry is a vendor-official remote MCP server and uses OAuth 2.1 Authorization Code; the Microsoft entries route through Microsoft 365 Agent365 and need `MS_TENANT_ID` set as an OS environment variable before connect.

## Entries (8)

Click any card to expand the full spec inline — transport (Streamable HTTP / STDIO), authentication shape (OAuth 2.1 / API key / Bearer / none), required environment variables, vendor URL or stdio command, and the upstream docs link.

<div class="tcg-grid" markdown>

<div class="tcg-card tcg-card--clickable t-google" id="Gmail" data-tool-id="Gmail" data-tool-title="Gmail" markdown>
<div class="tcg-name"><span class="tcg-name__text">Gmail</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Gmail](https://cdn.simpleicons.org/gmail){ width="40" .tcg-favicon }</div>
<div class="tcg-type">productivity · global · preview <span class="risk risk-l3">preview</span></div>
<div class="tcg-body" markdown>
Read, search, and send Gmail messages, manage labels and drafts. Google Workspace MCP (Preview).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Google · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Google (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://gmailmcp.googleapis.com/mcp/v1`

**Auth** — OAuth 2.1

**OAuth 2.1** — runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.
**Stability** — PREVIEW · **Tier** — Tier 1

**Required env** — —

**Tags** — global · preview

**Description**

Read, search, and send Gmail messages, manage labels and drafts. Google Workspace MCP (Preview).

Docs: https://developers.google.com/workspace/guides/configure-mcp-servers

**Docs** — [https://developers.google.com/workspace/guides/configure-mcp-servers](https://developers.google.com/workspace/guides/configure-mcp-servers)

</div>
</div>

<div class="tcg-card tcg-card--clickable t-microsoft" id="Outlook-Mail" data-tool-id="Outlook-Mail" data-tool-title="Outlook Mail" markdown>
<div class="tcg-name"><span class="tcg-name__text">Outlook Mail</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>:material-microsoft-outlook:</div>
<div class="tcg-type">productivity · global <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Read, send, and organise Outlook mail across folders. Routed through Microsoft 365 Agent365 — requires your tenant ID (MS_TENANT_ID).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Microsoft · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Microsoft (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://agent365.svc.cloud.microsoft/agents/tenants/${MS_TENANT_ID}/servers/mcp_MailTools`

**Auth** — OAuth 2.1

**OAuth 2.1** — runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.
**Stability** — GA · **Tier** — Tier 1

**Required env** — `MS_TENANT_ID`

**Tags** — global

**Description**

Read, send, and organise Outlook mail across folders. Routed through Microsoft 365 Agent365 — requires your tenant ID (MS_TENANT_ID).

Docs: https://github.com/microsoft/mcp

**Docs** — [https://github.com/microsoft/mcp](https://github.com/microsoft/mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable t-google" id="Google-Calendar" data-tool-id="Google-Calendar" data-tool-title="Google Calendar" markdown>
<div class="tcg-name"><span class="tcg-name__text">Google Calendar</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Google Calendar](https://cdn.simpleicons.org/googlecalendar){ width="40" .tcg-favicon }</div>
<div class="tcg-type">productivity · global · preview <span class="risk risk-l3">preview</span></div>
<div class="tcg-body" markdown>
Create, list, and manage Google Calendar events, attendees, reminders, and recurring schedules. Google Workspace MCP (Preview).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Google · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Google (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://calendarmcp.googleapis.com/mcp/v1`

**Auth** — OAuth 2.1

**OAuth 2.1** — runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.
**Stability** — PREVIEW · **Tier** — Tier 1

**Required env** — —

**Tags** — global · preview

**Description**

Create, list, and manage Google Calendar events, attendees, reminders, and recurring schedules. Google Workspace MCP (Preview).

Docs: https://developers.google.com/workspace/guides/configure-mcp-servers

**Docs** — [https://developers.google.com/workspace/guides/configure-mcp-servers](https://developers.google.com/workspace/guides/configure-mcp-servers)

</div>
</div>

<div class="tcg-card tcg-card--clickable t-microsoft" id="Outlook-Calendar" data-tool-id="Outlook-Calendar" data-tool-title="Outlook Calendar" markdown>
<div class="tcg-name"><span class="tcg-name__text">Outlook Calendar</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>:material-microsoft-outlook:</div>
<div class="tcg-type">productivity · global <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Browse Outlook calendars, schedule and update meetings, manage availability via Microsoft 365 Agent365. Requires MS_TENANT_ID.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Microsoft · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Microsoft (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://agent365.svc.cloud.microsoft/agents/tenants/${MS_TENANT_ID}/servers/mcp_CalendarTools`

**Auth** — OAuth 2.1

**OAuth 2.1** — runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.
**Stability** — GA · **Tier** — Tier 1

**Required env** — `MS_TENANT_ID`

**Tags** — global

**Description**

Browse Outlook calendars, schedule and update meetings, manage availability via Microsoft 365 Agent365. Requires MS_TENANT_ID.

Docs: https://github.com/microsoft/mcp

**Docs** — [https://github.com/microsoft/mcp](https://github.com/microsoft/mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Notion" data-tool-id="Notion" data-tool-title="Notion" markdown>
<div class="tcg-name"><span class="tcg-name__text">Notion</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Notion](https://cdn.simpleicons.org/notion){ width="40" .tcg-favicon }</div>
<div class="tcg-type">productivity · global <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Browse, create, and edit Notion pages, databases, and properties. Vendor-hosted remote MCP with OAuth 2.1 + PKCE.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Notion · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Notion (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://mcp.notion.com/mcp`

**Auth** — OAuth 2.1

**OAuth 2.1** — runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.
**Stability** — GA · **Tier** — Tier 1

**Required env** — —

**Tags** — global

**Description**

Browse, create, and edit Notion pages, databases, and properties. Vendor-hosted remote MCP with OAuth 2.1 + PKCE.

Docs: https://developers.notion.com/guides/mcp/overview

**Docs** — [https://developers.notion.com/guides/mcp/overview](https://developers.notion.com/guides/mcp/overview)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Kakao-PlayMCP" data-tool-id="Kakao-PlayMCP" data-tool-title="Kakao PlayMCP" markdown>
<div class="tcg-name"><span class="tcg-name__text">Kakao PlayMCP</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Kakao PlayMCP](https://cdn.simpleicons.org/kakaotalk){ width="40" .tcg-favicon }</div>
<div class="tcg-type">productivity · korea · aggregator <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Kakao aggregator hub — KakaoTalk send-to-self, Talk Calendar, KakaoMap, Gift, Melon, plus 200+ third-party MCPs. KR-focused.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Kakao · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;Bearer (OTT-derived)</div>
</div>
<div class="tcg-cta">Click for transport · auth · setup · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Kakao (vendor-official (Tier 1))

**Transport** — Streamable HTTP at `https://playmcp.kakao.com/mcp`

**Auth** — `Authorization: Bearer ${KAKAO_PLAYMCP_TOKEN}` header. PlayMCP does not support standard OAuth 2.1 Authorization Code (`redirect_uri` is not registered and the public DCR endpoint is IP-allowlisted), so the OAuth form is not used. Issue a One-Time Token (OTT), exchange it for an access token, export the access token as `KAKAO_PLAYMCP_TOKEN`, then activate. Access tokens are valid for 12 hours; refresh tokens for 90 days.

**Required env** — `KAKAO_PLAYMCP_TOKEN` &nbsp; **Stability** — GA · **Tier** — Tier 1 &nbsp; **Tags** — korea · aggregator

#### Setup (one-time, ~3 minutes)

**1. Issue an OTT.** Open [https://playmcp.kakao.com/toolbox](https://playmcp.kakao.com/toolbox), sign in with Kakao, find the **OpenClaw** integration and click **Connect**. PlayMCP issues a 64-character hex OTT, valid for ~10 minutes. Copy it.

**2. Exchange the OTT for access + refresh tokens and print them.** The playground manages the env values itself — copy the two printed lines into its **Environment Variables** card (desktop launcher or `Edit Config`) before launching, then start / restart the app.

macOS · Linux · WSL · Git Bash — only `curl` and `sed`:

```bash
export OTT="paste-the-64-hex-OTT-here"

RESP=$(curl -sS -X POST 'https://playmcp.kakao.com/api/v1/auths/otts:exchange' \
  -H 'Content-Type: application/json' \
  -d "{\"tokenValue\":\"$OTT\"}")

echo "KAKAO_PLAYMCP_TOKEN=$(printf '%s'   "$RESP" | sed -nE 's/.*"accessToken":\{[^}]*"tokenValue":"([^"]+)".*/\1/p')"
echo "KAKAO_PLAYMCP_REFRESH=$(printf '%s' "$RESP" | sed -nE 's/.*"refreshToken":\{[^}]*"tokenValue":"([^"]+)".*/\1/p')"
```

Windows PowerShell — uses the built-in `Invoke-RestMethod` + `ConvertFrom-Json`:

```powershell
$OTT = "paste-the-64-hex-OTT-here"

$resp = Invoke-RestMethod -Method Post `
  -Uri 'https://playmcp.kakao.com/api/v1/auths/otts:exchange' `
  -ContentType 'application/json' `
  -Body "{`"tokenValue`":`"$OTT`"}"

Write-Host "KAKAO_PLAYMCP_TOKEN=$($resp.accessToken.tokenValue)"
Write-Host "KAKAO_PLAYMCP_REFRESH=$($resp.refreshToken.tokenValue)"
```

**3. Paste the two `KAKAO_PLAYMCP_*` lines into the playground's Environment Variables**, then start (or restart) the playground.

**4. Activate the catalog entry.** MCP Server page → **Inactive MCP → Productivity → Kakao PlayMCP** → click. The form pre-fills with Transport `Streamable HTTP`, URL `https://playmcp.kakao.com/mcp`, and `Authorization: Bearer ${KAKAO_PLAYMCP_TOKEN}` in Headers. Click **Save & Connect** — the dot turns green and the Inspector shows `kakaotalk_send_to_self`, `talk_calendar_*`, `kakaomap_*`, `gift_*`, `melon_*`, plus 200+ third-party relays.

#### Refresh ritual (every 12 hours)

The access token expires in 12 hours. Run the refresh-token grant below, then **handle the two printed `KAKAO_PLAYMCP_*` lines exactly as in step 2** — paste into the playground's Environment Variables, restart, click **Save & Connect**.

```bash
RESP=$(curl -sS -X POST 'https://playauth.kakao.com/playmcp/oauth2/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=refresh_token' \
  -d "refresh_token=$KAKAO_PLAYMCP_REFRESH" \
  -d 'client_id=HElMUWdVoroTsrXxezeTSemg8gXzzCKWARb5MJux8gY')

echo "KAKAO_PLAYMCP_TOKEN=$(printf '%s'   "$RESP" | sed -nE 's/.*"access_token":"([^"]+)".*/\1/p')"
echo "KAKAO_PLAYMCP_REFRESH=$(printf '%s' "$RESP" | sed -nE 's/.*"refresh_token":"([^"]+)".*/\1/p')"
```

The grant endpoint returns snake_case `access_token` / `refresh_token` at the top level (different from the OTT exchange shape), so the regex differs — that's the only change from step 2.

#### Refresh ritual (every 90 days)

The refresh token expires after 90 days. When that happens, the 12-hour grant returns an error — start over from step 1 (issue a new OTT, exchange it, paste into Environment Variables).

**Description**

Kakao aggregator hub — KakaoTalk send-to-self, Talk Calendar, KakaoMap, Gift, Melon, plus 200+ third-party MCPs. KR-focused.

**Docs**

- PlayMCP product — [https://playmcp.kakao.com/](https://playmcp.kakao.com/)

</div>
</div>

<div class="tcg-card tcg-card--clickable t-slack" id="Slack" data-tool-id="Slack" data-tool-title="Slack" markdown>
<div class="tcg-name"><span class="tcg-name__text">Slack</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>:material-pound-box-outline:</div>
<div class="tcg-type">communication · global <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Read and post Slack messages across channels and DMs, search the workspace, manage user/channel metadata. Slack's official remote MCP.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Slack · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Slack (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://mcp.slack.com/mcp`

**Auth** — OAuth 2.1

**OAuth 2.1** — runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.
**Stability** — GA · **Tier** — Tier 1

**Required env** — —

**Tags** — global

**Description**

Read and post Slack messages across channels and DMs, search the workspace, manage user/channel metadata. Slack's official remote MCP.

Docs: https://docs.slack.dev/ai/slack-mcp-server/

**Docs** — [https://docs.slack.dev/ai/slack-mcp-server/](https://docs.slack.dev/ai/slack-mcp-server/)

</div>
</div>

<div class="tcg-card tcg-card--clickable t-microsoft" id="Microsoft-Teams" data-tool-id="Microsoft-Teams" data-tool-title="Microsoft Teams" markdown>
<div class="tcg-name"><span class="tcg-name__text">Microsoft Teams</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>:material-microsoft-teams:</div>
<div class="tcg-type">communication · global <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Send messages to Teams chats and channels, search conversations, manage meetings via Microsoft 365 Agent365. Requires MS_TENANT_ID.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Microsoft · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Microsoft (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://agent365.svc.cloud.microsoft/agents/tenants/${MS_TENANT_ID}/servers/mcp_TeamsServer`

**Auth** — OAuth 2.1

**OAuth 2.1** — runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.
**Stability** — GA · **Tier** — Tier 1

**Required env** — `MS_TENANT_ID`

**Tags** — global

**Description**

Send messages to Teams chats and channels, search conversations, manage meetings via Microsoft 365 Agent365. Requires MS_TENANT_ID.

Docs: https://github.com/microsoft/mcp

**Docs** — [https://github.com/microsoft/mcp](https://github.com/microsoft/mcp)

</div>
</div>

</div>

## Workflow combinations { #combinations }

The eight entries on this page are usually most useful as **pairs or triplets** behind a single agent turn. A few combinations that pay for themselves:

- **Inbox triage → calendar** — `Gmail` (or `Outlook Mail`) + `Google Calendar` (or `Outlook Calendar`). An agent reads new messages, extracts proposed times, and writes accepted ones to the calendar in one turn.
- **Standup digest → channel** — `Notion` (or `Atlassian Rovo` from the Dev page) + `Slack` (or `Microsoft Teams`). Pulls overnight changes from the doc, posts a digest before the morning meeting.
- **Cross-suite triage** — `Outlook Mail` + `Microsoft Teams` (both Microsoft 365 Agent365). Single `MS_TENANT_ID` env unlocks both; the agent can answer a Teams DM with a mail attachment in the same turn.
- **Mixed-tenant** — `Gmail` + `Outlook Mail`. Two OAuth flows but one Inspector — switch sidebar selection per connection; chat sees both as a flat tool set.

## Auth & secrets { #auth-secrets }

Every entry on this page uses OAuth 2.1 Authorization Code. Two also need an OS environment variable on top of OAuth, supplied as a `${VAR}` placeholder in the URL or `requiredEnv`:

| Connection | OAuth issuer | Extra env |
|---|---|---|
| Gmail | `https://accounts.google.com` (Google Workspace scopes — preview) | — |
| Google Calendar | `https://accounts.google.com` (calendar scope — preview) | — |
| Outlook Mail | `https://login.microsoftonline.com/${MS_TENANT_ID}/v2.0` | `MS_TENANT_ID` |
| Outlook Calendar | `https://login.microsoftonline.com/${MS_TENANT_ID}/v2.0` | `MS_TENANT_ID` |
| Notion | Notion-hosted OAuth 2.1 + PKCE | — |
| Kakao PlayMCP | Bearer header (OTT-derived access token), 12 h refresh ritual — [setup](#Kakao-PlayMCP) | `KAKAO_PLAYMCP_TOKEN` |
| Slack | Slack-hosted OAuth | — |
| Microsoft Teams | `https://login.microsoftonline.com/${MS_TENANT_ID}/v2.0` | `MS_TENANT_ID` |

Set `MS_TENANT_ID` in your shell or in the desktop launcher's Environment Variables card *before* you click **Save & Connect** — the connection fails fast with a clear error if it's missing.

## Picking guide { #picking-guide }

| If you need… | Reach for |
|---|---|
| The lightest-weight personal-inbox demo | `Gmail` — preview but no tenant config |
| Anything inside a Microsoft 365 tenant | `Outlook Mail` / `Outlook Calendar` / `Microsoft Teams` — share one `MS_TENANT_ID` env |
| A doc store that doubles as a knowledge base for RAG | `Notion` — pages and databases come back as MCP resources |
| Channel posting from agentic flows | `Slack` for fast-moving teams · `Microsoft Teams` for 365 shops |
| Korean-domain coverage (KakaoTalk send-to-self, Talk Calendar, Kakao Map, 200+ relays) | `Kakao PlayMCP` |

