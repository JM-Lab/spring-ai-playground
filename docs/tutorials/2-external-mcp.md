Description: Tutorial 2 — connect an external MCP server (Streamable HTTP / STDIO / SSE / OAuth 2.1) and validate the schema in the Inspector before relying on it from chat.

# Tutorial 2 — Connect an External MCP Server

**Time** 8 min · **Difficulty** ★★☆ · **Surfaces** MCP Server

!!! abstract "Goal"
    Add an external MCP server connection (Streamable HTTP, STDIO, or SSE), wire up authentication if the server requires it, validate the schema in the Inspector, and run a tool through it directly — *before* relying on it from chat.

## Steps

1. Open **MCP Server** and click the `+` icon next to **MCP Server Connections** to start a new connection.
2. Pick the transport type. **Streamable HTTP** is the modern default; STDIO is for proxy-style local processes (Claude Desktop's `mcp-remote`); SSE is the legacy HTTP+SSE shape.
3. Fill in the connection name and the JSON config for your transport.

![New MCP connection form with status sidebar, transport, JSON config, headers, and connect buttons](../assets/images/tutorials/tutorial-2-connection-form.png)
*① the sidebar shows a colored status dot per connection (green OK · gray offline · red error). ② transport — Streamable HTTP is the modern default; STDIO and SSE are also supported. ③ JSON connection config (URL + endpoint, or stdio command + args). ④ **Headers** section, with a `${ENV_VAR}` substitution hint — values like `${MY_API_KEY}` resolve from the OS environment at connect time. ⑤ **Save & Connect** registers the connection; **Test Connection** spins up a transient client to validate the config without touching the live one.*

### Add an Authorization header

Many remote MCP servers require an API key or bearer token. Use the **Insert auth header preset…** dropdown above the buttons to drop in a templated row instead of hand-typing the header name.

![Auth header preset dropdown showing the four built-in templates](../assets/images/tutorials/tutorial-2-auth-preset.png)
*① the **+** button adds a blank header row. ② **Authorization (Bearer Token)** templates `Authorization: Bearer <value>` — fill in the token. ③ **Authorization (Basic Auth)** templates a base64 user:pass row. ④ **API Key Header** templates a custom header (e.g. `X-API-Key`). ⑤ **OAuth 2.1 Authorization Code (configure)** swaps in the dedicated OAuth sub-form covered below.*

!!! tip "Don't paste secrets into the form"
    The Headers section accepts `${ENV_VAR}` placeholders — set the secret in your shell or the desktop launcher's Environment Variables, then put `${MY_API_KEY}` in the form. The persisted JSON only stores the placeholder; the actual key is resolved at connect time. The same syntax works for STDIO `env` values and `requiredEnv` lists.

### OAuth 2.1 servers (Authorization Code flow)

For servers that expect an OAuth dance instead of a static token (Atlassian's MCP server is a common example), pick **OAuth 2.1 Authorization Code (configure)** from the preset dropdown. The Headers section is replaced with a dedicated OAuth sub-form.

![OAuth 2.1 sub-form with Client ID, Issuer URI, Scopes, Advanced, Redirect URI, and Authorize button](../assets/images/tutorials/tutorial-2-oauth-subform.png)
*① the OAuth sub-form, opened from the auth preset dropdown. ② **Client ID** (required) and **Issuer URI** — the issuer alone is enough for OIDC discovery (`.well-known`) to auto-resolve the authorization and token endpoints. ③ **Scopes** are comma-separated; leave blank to inherit the issuer's defaults. ④ **Advanced** discloses manual `authorization_uri` / `token_uri` / client-secret / client auth method overrides for non-OIDC providers. ⑤ the **Redirect URI** the playground listens on — register this URI as an allowed redirect on the issuer side. ⑥ **Authorize** opens your system browser to the consent screen — click it after **Save & Connect**.*

The flow has three observable states:

1. **Save & Connect** records the OAuth registration but doesn't connect yet (no token).
2. Click **Authorize** — the connection moves to **AWAITING_AUTHORIZATION** and your system browser opens to the issuer. The Home dashboard adds an awaiting-auth counter so you don't lose track of half-finished flows.
3. After you grant access, the redirect lands at the playground's callback URL, the code is exchanged for tokens, and the connection comes up like any other.

Tokens are encrypted on disk under `~/spring-ai-playground/mcp/oauth-tokens/`. Refresh happens transparently — once you authorize once, the connection survives playground restarts as long as the issuer accepts the refresh.

### Validate in the Inspector

4. Once connected (the sidebar dot turns green), scroll to **MCP Inspector**. The tab strip exposes everything the server speaks — *and* a few client-side primitives the server can call back into.

![Inspector tab strip — Tools, Resources, Prompts, Ping, Notifications, Roots, Sampling, Elicitation](../assets/images/tutorials/tutorial-2-inspector-tabs.png)

The eight tabs split into **server primitives** the server exposes (Tools, Resources, Prompts, Ping, Notifications) and **client primitives** the server can ask *your* playground to handle (Roots, Sampling, Elicitation). For most "use this server's tools in chat" workflows you'll spend your time on Tools and Resources; the others are mostly useful when developing or debugging an MCP server.

5. Click **Tools**. Each tool is a full-width card with its description, schema-typed inputs, and a **Run** button that calls the tool through the live transport.

![Tools tab with the new card layout — search, run, schema-typed inputs](../assets/images/tutorials/tutorial-2-inspector-tools.png)
*① the selected tab — Tools is the default. ② all eight tabs are visible side by side. ③ search filters cards by name or description, with the live count. ④ the **Run** button on each card calls the tool through the actual transport (not just a sandbox). ⑤ the tool name. ⑥ parameter rows rendered per the JSON Schema (string / number / boolean / enum each get the matching control).*

6. Fill in any required parameters and click **Run**. The result lands inline in the same card — a status header (OK / ERROR, elapsed ms, timestamp), a **REQUEST** section, a **RESPONSE** section, and a **Raw** toggle that swaps in the JSON-RPC envelope. Use **Copy** to grab the response, or the dismiss button to clear the panel.

!!! tip "Validate here, not in chat"
    Tools that fail in MCP Inspector will fail in Agentic Chat too — but the chat error message is wrapped in the agent's reasoning trace and harder to debug. Save yourself a turn: run every new tool through the inspector once before letting a model invoke it.

!!! example "Useful external MCP servers"
    - Claude Desktop / Claude Code via Streamable HTTP
    - Cursor's MCP server entry
    - Awesome MCP Servers list — a directory of community servers

→ Next: [Tutorial 3 — Index a Document for RAG](3-index-document.md)
