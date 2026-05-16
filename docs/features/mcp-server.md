description: MCP Server surface — connect external servers (Streamable HTTP / STDIO / SSE / OAuth 2.1) and inspect tools, resources, prompts, and notifications.

# MCP Server

The MCP Server area is where you manage and inspect tool connections.

![MCP Server overview — list of connected MCP servers (built-in + external) with tool, resource, prompt counts and per-server inspector tabs](../assets/images/mcp.png)

It serves two roles:

- it helps you validate tools published by Spring AI Playground itself
- it acts as a client-side inspection surface for external MCP servers

That distinction matters for safety expectations: built-in tools follow the Tool Studio authoring and test-run workflow, while external MCP servers are connections that you choose to add and trust.

## Connection Management

The MCP runtime supports multiple transport styles, including:

- Streamable HTTP
- STDIO
- legacy HTTP plus SSE-style setups where needed

This makes the Playground useful both for local tool exposure and for external MCP integration testing.

Streamable HTTP is the modern single-endpoint transport used by the built-in MCP server, while STDIO and legacy HTTP plus SSE remain useful for compatibility and external integrations.

The modern Streamable HTTP transport formalized in the MCP v2025-03-26 specification uses a single MCP endpoint. Clients POST JSON-RPC requests to `/mcp`, responses can stream when supported, and session-oriented behavior can be layered on top by MCP clients and servers.

That modern transport replaces the older split HTTP-plus-SSE mental model with a simpler single endpoint, while still preserving compatibility value for STDIO and older integrations.

### Live status in the sidebar

Each connection in the left rail carries a colored dot that reflects its last health check: **green** for OK, **gray** for offline (not connected), and **red** for an error returned during initialization or a recent ping. The dot updates as the playground reconnects or re-pings, so a connection that drops mid-session is visible without opening the inspector.

![Sidebar with a connected server showing the OK status dot](../assets/images/tutorials/tutorial-2-sidebar-status.png){ width="320" }

*① the green dot next to a server name means the most recent ping succeeded — it goes red if the server returned an error and gray when nothing has connected yet.*

### Test Connection without disturbing live clients

The config form has a **Test Connection** button next to **Save & Connect**. It spins up a transient sync MCP client, runs `initialize` and a one-shot `listTools`, then disposes the client — without touching the running connection map. Use it to validate a config change against a remote server before saving (which would otherwise replace the live client and might briefly drop tool availability in chat).

### Custom HTTP headers and `${ENV_VAR}` substitution

HTTP and SSE connections both expose a single **Headers** section in the config form, edited as key/value rows. The same row layout drives the auth-preset dropdown — picking **Authorization (Bearer Token)**, **Authorization (Basic Auth)**, or **API Key Header** inserts a templated row whose value you fill in.

Header values, STDIO `env` values, and any name listed in `requiredEnv` accept `${VAR}` placeholders that resolve from the OS environment at connect time (with a JVM system-property fallback). That keeps secrets out of the saved JSON: the persisted file stores `${OPENAI_API_KEY}` literally, and the actual key only enters memory when the connection is brought up. A missing reference throws at connect time so the inspector can surface it instead of silently sending an empty header.

![Auth header preset dropdown showing Bearer Token, Basic Auth, API Key, and OAuth 2.1 options](../assets/images/tutorials/tutorial-2-auth-preset.png)

*① the **+** button adds a blank header row. ②③④ pick a templated header (Bearer / Basic / API Key) and only the value is left for you to fill in. ⑤ switches the form to the dedicated [OAuth 2.1](#oauth-21-authorization-code) sub-form instead of an inline header.*

## OAuth 2.1 Authorization Code

Some MCP servers expect an OAuth 2.1 Authorization Code flow rather than a static bearer token. Picking **OAuth 2.1 Authorization Code (configure)** from the auth preset opens a dedicated sub-form on the connection.

![OAuth 2.1 sub-form with Client ID, Issuer URI, Scopes, Advanced details, Redirect URI, and Authorize button](../assets/images/tutorials/tutorial-2-oauth-subform.png)

*① the OAuth section, opened from the auth preset dropdown. ② **Client ID** (required) and **Issuer URI** — the issuer alone is enough for OIDC discovery to auto-resolve the authorization and token endpoints. ③ **Scopes** are comma-separated; leave blank to inherit the issuer's defaults. ④ **Advanced** discloses manual `authorization_uri` / `token_uri` / client-secret / client auth method overrides for non-OIDC providers. ⑤ the **Redirect URI** the playground listens on — register this URI as an allowed redirect on the issuer side. ⑥ **Authorize** opens your system browser to grant access — click it after **Save & Connect** records the registration.*

The flow has three observable states:

- **Configured** — Save & Connect persists the OAuth registration but does not connect yet (no token).
- **AWAITING_AUTHORIZATION** — clicking **Authorize** opens the system browser to the issuer's consent screen and the connection sits in this state until the redirect lands. The Home dashboard surfaces a counter so half-finished authorizations don't get lost.
- **Connected** — once the redirect completes, the playground exchanges the code for tokens and the connection comes up like any other.

Tokens are kept in an encrypted file store under `${user.home}/spring-ai-playground/mcp/oauth-tokens/`. The encryption key is derived from a per-install salt plus the host's `user.home`, so copying the directory to another machine doesn't disclose tokens to that host. Refresh tokens are used transparently — once you authorize, the playground keeps the connection live across restarts as long as the issuer accepts the refresh.

!!! tip "Use `${ENV_VAR}` for client secrets"
    The OAuth sub-form's **Client secret** field accepts placeholders the same way header values do. Storing `${SOME_OAUTH_CLIENT_SECRET}` in the form keeps the secret out of the persisted JSON; the actual value is read from the OS environment at connect time.

## MCP Inspector

The Inspector is the practical center of the MCP screen — once a connection is up, it lets you exercise every primitive the server (or your client) exposes, isolated from chat.

![Inspector tab strip — Tools, Resources, Prompts, Ping, Notifications, Roots, Sampling, Elicitation](../assets/images/tutorials/tutorial-2-inspector-tabs.png)

The eight tabs split cleanly into **server primitives** (Tools, Resources, Prompts, Ping, Notifications) and **client primitives** (Roots, Sampling, Elicitation — these are inverted: the *server* asks the playground to act as the client). The split matters because client-side primitives are how a server drives interactive behavior back into the playground, not how you call the server.

### Tools

Each tool is a full-width card. The card shows the tool name, the description shown to the model, any tool annotations the server published as badges (read-only / destructive / idempotent / open-world hint), an inputs panel typed by JSON Schema (boolean / number / array / object / enum render with the matching control), and a **Run** button that calls the tool through the live transport.

![Tools tab with the new card layout — search, run buttons, JSON-Schema-typed inputs, parameter rows](../assets/images/tutorials/tutorial-2-inspector-tools.png)

*① the selected tab — Tools is the default. ② all eight tabs run side by side. ③ a search input filters the cards by name or description, with the count alongside. ④ the **Run** play button on each card calls the tool through the actual transport (not just a sandbox). ⑤ the tool name and ⑥ its parameter schema, rendered as the appropriate input control.*

The result of a run lands inline in the same card: a status header (OK / ERROR badge, tool name, elapsed ms, timestamp), a **REQUEST** section, a **RESPONSE** section, a **Raw** toggle that swaps the cleaned response for the JSON-RPC envelope, a **Copy** action with toast feedback, and a dismiss button. That keeps the entire request → response → diagnose loop in one place per tool.

### Resources, Prompts, Ping, Notifications

The other server-primitive tabs follow the same per-card pattern with the type-appropriate fields:

- **Resources** lists static resources and resource templates the server exposes; selecting one fetches its content into an inline panel. Templates accept parameters via the same JSON-Schema-typed inputs the Tools tab uses.
- **Prompts** lists named prompts; you can fill in arguments and preview the rendered messages the server would return to the model.
- **Ping** sends a one-shot `ping` request and reports round-trip latency — a quick liveness check independent of any tool.
- **Notifications** is a feed of inbound server-initiated notifications (`tools/list_changed`, `resources/updated`, `prompts/list_changed`, log messages) with timestamps. It's the easiest way to see whether a server actually pushes change notifications, since chat doesn't surface them.

![Resources tab with the empty-state message — same layout reused for prompts, ping, and notifications](../assets/images/tutorials/tutorial-2-inspector-resources-empty.png)

*① clicking another tab swaps the inspector content but keeps the connection. ② tabs that have nothing to show say so explicitly — useful when probing a new server's capability surface.*

### Roots, Sampling, Elicitation (client primitives)

These three are flipped: the server initiates and the playground (acting as the MCP client) responds. The Inspector exposes them so you can see and answer those server-side requests instead of having them disappear into the void:

- **Roots** is the list of file/URI roots the playground advertises to the server when it asks `roots/list`. The default is empty; add roots here when a server needs to know which directories or URIs you've opted to expose.
- **Sampling** is what fires when a server calls `sampling/createMessage` — it's the server asking your client (the playground) to run a model turn on its behalf. The tab logs the request with its messages and model preferences and lets you approve or reject it; approved requests run through the playground's configured chat model.
- **Elicitation** is what fires when a server calls `elicitation/create` to ask the user a question (a typed input form) mid-conversation. The tab renders the form per the server's schema, captures your answer, and ships it back as the response.

Together with Notifications, these tabs make the playground a useful place to *develop* MCP servers, not just consume them — you can see exactly when your server emits a notification or asks the client to do something.

## Getting Started With MCP

1. configure an MCP connection (or use the built-in one)
2. inspect the available tools, resources, and prompts in the Inspector
3. review the argument schemas with the JSON-Schema-typed inputs
4. execute tools / fetch resources / preview prompts directly
5. for OAuth-protected servers, complete the **Authorize** browser handoff once
6. use the validated connection later in Agentic Chat

## Relationship to Tool Studio

Tool Studio and MCP Server are designed to work together:

- Tool Studio creates or updates a tool
- the built-in MCP server exposes it
- MCP Inspector verifies the contract and runtime behavior
- Agentic Chat consumes the validated connection

This is one of the cleanest parts of the overall product flow.

→ Next: [Vector Database](vector-database.md) — prepare grounded knowledge for retrieval.
