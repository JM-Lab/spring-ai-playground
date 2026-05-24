title: AI Agent Tool Safety Architecture
description: Defense-in-depth sandbox for AI agent tools — three-layer model, policy resolution, threat-to-layer mapping, and Risk Level (L0–L5) reference.

# AI Agent Tool Safety Architecture

Spring AI Playground is a Spring Boot application that executes user-authored JavaScript inside its own JVM. Tool Studio's value proposition — author, test, and publish a tool without restart on the same machine that runs the model — puts tool code on the critical path: any tool you author becomes reachable to MCP clients (and ultimately to an agent) as soon as it earns a **Local Pass**.

This page is the system-level reference for how the sandbox is shaped. For the user-facing surface (the override fields, the Sandbox & Capabilities pane, the Risk Level badge), see [Tool Studio → Safety](features/tool-studio/index.md#safety) and [Tool Studio → Sandbox & Capabilities](features/tool-studio/index.md#sandbox-capabilities).

This is one of four architecture documents that complement each other:

- [Application Architecture](architecture.md) — runtime layers, feature modules, data flows, extension points
- [Safe Tool Specification 1.0](safe-tool-specification.md) — normative JSON spec for tool authoring (the document the sandbox enforces)
- **AI Agent Tool Safety Architecture** (this page) — defense-in-depth sandbox model, policy resolution, threat-to-layer mapping, known limitations
- [AI Agent Observability Architecture](observability-architecture.md) — the visibility layer that makes the sandbox's prevention auditable

## Scope and naming

The Playground codebase uses **safety** for the sandbox surface (`safety.fs`, `safety.parser.*`) and reserves **security** for the adversarial-threat layer (Spring Security on the MCP transport). This document follows the same convention:

- **Safety** — keeping a small JavaScript action from doing things its author did not intend: runaway resource use, accidental egress to private networks, reading the wrong file, leaking secrets to logs. Sandbox boundaries are deterministic and bypass-resistant from inside JS, but they are not adversarial-grade for code that escapes into the host JVM through unforeseen paths.
- **Security** — who can talk to the MCP endpoint at all, which authentication and transport guarantees apply. Handled by Spring Security on top of the sandbox, independent of how individual tools are authored.

The two are layered but separate, and they fail to different threats. The diagrams below split them accordingly.

## Threat surface

A tool author writes a small JavaScript action with structured input parameters and optional static variables. Tool Studio compiles that into an `McpToolDefinition`, runs it once locally against the declared test values to earn a Local Pass, then registers the callback with the built-in `McpSyncServer`. From that moment the tool is reachable through Streamable HTTP at `/mcp` and callable from Agentic Chat.

The threats this design has to defend against fall into three categories, listed in roughly increasing trust granted to the actor:

1. **Runaway code** — accidental infinite loop, recursive blow-up, unbounded buffer growth, deadlock. The author did not intend harm; the code does the wrong thing anyway.
2. **Misuse by author** — the author writes a tool that calls a private network endpoint, reads a path outside the workspace, leaks an env-backed secret into a log line, or pulls in a Java class the default policy denies. The author may not realise these are escalations.
3. **External callers** — anything calling `/mcp` from outside the local machine. This is the adversarial-security layer, distinct from sandbox safety.

The three layers below catch categories (1) and (2) at the JS-execution boundary, and category (3) at the MCP transport. The split matters because most engineering choices — `safety.fs`, deny-first allowlist, virtual-thread timeout, env-var masking — exist to protect *the local author's environment from accidents*, not to protect a deployed cluster from external attackers. The latter is a Spring Security configuration choice, not a sandbox capability.

## The three layers

The high-level model is three independent layers. Layer 1 cannot be disabled from JS. Layer 2 widens specific dimensions per tool, with the resulting elevation visible as a badge before publish. Layer 3 sits in front of the MCP transport.

```mermaid
flowchart TB
    subgraph L1["Layer 1 — Java sandbox (always on)"]
        A1["Class allow / deny<br/>(deny-first)"]
        A2["Resource limits<br/>(statements · timeout)"]
        A3["Helpers gateway<br/>(fetch · safety.fs · safety.parser)"]
        A4["Output masking<br/>(env-backed secrets)"]
    end
    subgraph L2["Layer 2 — Per-tool overrides"]
        B1["SandboxOverrides"] --> B2["Posture calculator"] --> B3["Risk badge<br/>L0 · L3 · L4 · L5"]
    end
    subgraph L3["Layer 3 — MCP endpoint (Spring Security)"]
        C1["Spring Security<br/>(off by default)"] --> C2["Streamable HTTP /mcp"]
    end
    L1 == widens (cannot weaken) ==> L2
    L2 == publishes through ==> L3
```

What each layer controls, in detail:

| Layer | Component | What it enforces |
|---|---|---|
| **1** | Class allow / deny | Deny-first lookup gate (`JsToolExecutor.isClassAllowed`). Default deny-list covers `System` / `Runtime` / `Process` / `ProcessBuilder` / `Class` / reflect / invoke / `Thread` / `ThreadGroup` / `ClassLoader` / `ServiceLoader` / `java.util.spi.*`. Default allow-list covers only `java.lang/math/time/util/text.*` — pure compute. |
| **1** | Resource limits | `max-statements: 500000` via GraalVM `ResourceLimits` + wall-clock timeout via `Future.cancel(true)` on a virtual-thread executor. |
| **1** | Helpers gateway | `fetch` (SSRF four-layer guard in `strict` by default), `safety.fs` (rooted at base path with `normalize()` escape check), `safety.parser.{html,xml,csv,yaml}`. These are the only network and filesystem paths from JS. |
| **1** | Output masking | `console.log` substring-masks env-backed static-variable values before they reach the debug pane or chat tool-call trace. The mask applies to **all** env-vars surfaced by the secret store below — values exported from the OS-encrypted secret store are still treated as secrets at the log boundary. |
| **1** | Secret store at rest | The desktop launcher persists tool-side secrets through Electron `safeStorage` — encrypted by **macOS Keychain** / **Windows DPAPI** / **libsecret** on Linux; the cipherkey never leaves the OS keychain. Secrets are exported as environment variables only to the launched JVM process, never written to YAML or chat history, and the JS-side `console.log` mask above redacts their resolved values from any tool output. See [Desktop App → Use Environment Variables for Keys and Secrets](getting-started/desktop.md#7-use-environment-variables-for-keys-and-secrets). |
| **2** | `SandboxOverrides` | Per-tool widening: `networkMode`, `hostsAllow`, `fileRead`/`fileWrite`, `addAllow/DenyClasses`, `fsBasePath`. |
| **2** | Posture calculator | `SandboxPostureCalculator.compute()` — pure function from overrides to `RiskLevel`. |
| **2** | Risk badge | L0 baseline · L3 narrow widening · L4 broad widening · L5 critical class re-enabled. |
| **3** | Spring Security | `SecurityFilterChain` in front of MCP transport. Disabled by default for local single-user; enabled via Spring AI MCP Security for deployed scenarios. |
| **3** | MCP transport | Streamable HTTP at `/mcp`. Binds to localhost only unless `server.address` is changed. |

Layer 1 is fixed code in [`JsToolExecutor`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/service/tool/runtime/JsToolExecutor.java), [`JsRuntimeGlobals`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/service/tool/runtime/JsRuntimeGlobals.java), [`SafeHttpFetch`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/service/tool/runtime/SafeHttpFetch.java), and [`SafeFs`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/service/tool/runtime/SafeFs.java). Layer 2 lives in `SandboxOverrides` per `ToolSpec` and `SandboxPostureCalculator` for the badge. Layer 3 is conventional Spring Security configuration on top of the MCP transport — independent of the sandbox.

## Human-in-the-loop checkpoints

Sandbox layers fail to threats that JS itself can produce — runaway resource use, accidental egress, path-escape attempts. They cannot judge *intent*. Spring AI Playground complements them with explicit human checkpoints spread across **four phases** — authoring, exposure curation, MCP Inspector testing, and Agentic Chat runtime. Each phase has its own decisions and feedback loops, so risk-shaping stays with a person rather than the model.

| Phase | Checkpoint | When it fires | What the human decides |
|---|---|---|---|
| **1. Authoring** | **Local Pass** | Author clicks **Test & Publish** in Tool Studio | "Did my tool produce the right result against my declared inputs?" — the publish gate *is* the act of testing. Fail → return to edit |
| **1. Authoring** | **Risk badge review** | Author widens a dimension in the **Sandbox & Capabilities** pane | "Is the resulting L3 / L4 / L5 badge justified for what this tool actually needs?" — the elevation is visible *before* publish |
| **2. Exposure curation** | **Tool MCP Server Setting** | Operator opens the drawer in Tool Studio (or the Default MCP Tools card in the launcher) | "Which subset of the bundled catalog do I want the built-in MCP server to expose?" — preset + include/exclude rules |
| **3. Inspector test** | **Direct Run from MCP Inspector** | Tester clicks the **Run** play button on a tool card in [MCP Server → Inspector](features/mcp-server/index.md) | "Does the actual MCP-routed call (not just the local sandbox path) return what I expect?" — the result and full schema are visible before any chat exposure |
| **4. Runtime** | **Tool / RAG selection in Chat** | User picks MCP servers + documents for the conversation in [Agentic Chat](features/agentic-chat.md) | "Which tools and which retrieval surface should this conversation reach?" — per-conversation scope |
| **4. Runtime** | **Result + trace review** | Agentic Chat surfaces each tool call, arguments, result, and reasoning trace inline | "Did the model pick the right tool with the right arguments? Was the answer grounded?" — every tool call is inspectable in the chat output |
| **4. Runtime** | **Per-call confirmation** *(shipping next)* | Server-side wrapper for tools declaring `humanInTheLoop`; chat-side override for `AUTO_APPROVE` tools above the Risk Level threshold | "Approve, reject, or modify this call before the model proceeds." Implements `ToolManifest.HumanInTheLoop` via the MCP `elicitation/create` protocol — see the **MCP elicitation HITL** sub-section below |

```mermaid
flowchart LR
    subgraph P1["1. Authoring (Tool Studio)"]
        direction TB
        A1[Write tool] --> A2{Local Pass?<br/>Test & Publish}
        A2 -- fails --> A1
        A2 -- passes --> A3[Risk badge<br/>L0–L5]
        A3 --> A4{Accept<br/>badge?}
        A4 -- no --> A1
        A4 -- yes --> A5[Published<br/>to built-in MCP]
    end
    subgraph P2["2. Exposure curation"]
        direction TB
        B1[Operator picks<br/>preset + rules]
    end
    subgraph P3["3. Inspector test (MCP Server)"]
        direction TB
        C1[Run tool from<br/>MCP Inspector] --> C2{Result<br/>as expected?}
        C2 -- no --> A1
    end
    subgraph P4["4. Runtime (Agentic Chat)"]
        direction TB
        D1[User selects<br/>tools / docs] --> D2[Model calls tool]
        D2 --> D3[Inline trace:<br/>args · result · reasoning]
        D3 --> D4{Accept<br/>answer?}
        D4 -. planned .-> D5[Per-call approval<br/>for L4 / L5]
    end
    A5 --> B1 --> C1
    C2 -- yes --> D1
```

The flow is deliberately not a single straight line. Failed Local Passes, unacceptable risk badges, and wrong Inspector results all loop back to authoring — so the badge alone never publishes a tool a human did not endorse. The existing gates are sufficient for the single-user local case but warrant tighter coupling once Spring AI Playground hosts multi-user or operator-vs-author workflows.

### MCP elicitation HITL (shipping next)

The Phase 4 **Per-call confirmation** checkpoint is implemented through an MCP **elicitation** layer that ships in the next feature pass. A tool spec carries a `humanInTheLoop` block; at call time a server-side wrapper issues an `elicitation/create` JSON-RPC to the calling client before delegating to the real callback. This sits *between* the Layer 1 sandbox (which judges *what* a tool can do) and the agent (which judges *when* to call it).

The tool spec carries one block:

```json
{
  "humanInTheLoop": {
    "mode": "REQUIRED",
    "promptTemplate": "Confirm writing to {args.path}?"
  }
}
```

`promptTemplate` is optional; `{toolName}` and `{args}` are simple text substitutions (no template engine).

Three modes on `ToolManifest.HumanInTheLoop.Mode`:

| Mode | Server behavior | Client experience |
|---|---|---|
| `REQUIRED` | Calls `exchange.createElicitation(prompt)` and waits for `ACCEPT`. `DECLINE` / `CANCEL` / no-elicit-support / SSE disconnect / SDK timeout all → `ToolExecutionException` (**fail-safe**) | Confirm dialog rendered by the calling client — the playground's MCP Inspector tab already handles this; chat-side handler ships alongside the wrapper |
| `AUTO_APPROVE` | Logs and proceeds — no elicit. Delegates to client-side policy | Client-defined (Agentic Chat applies a Risk Level threshold — see below) |
| `DISABLED` (default when block is absent or `null`) | Proceeds with zero overhead — no wrapping | n/a |

The wrapper only applies on MCP-routed calls. **The local Test Run path in the builder is not wrapped** — an author's own validation does not need to elicit from themselves.

Builder UI applies **Risk-Level-aware defaults** when a new tool is authored. The radio tracks Risk Level changes until the user touches it; afterward the explicit choice sticks.

| Risk Level at author time | Default `mode` |
|---|---|
| `L0` | `DISABLED` |
| `L1` – `L5` | `REQUIRED` |

Downgrades (`REQUIRED → AUTO_APPROVE`, `REQUIRED → DISABLED`, `AUTO_APPROVE → DISABLED`) trigger a **confirm modal in the builder** that explains the consequence:

- *"AUTO_APPROVE delegates to the calling client's own auto-approval policy. In Agentic Chat we override at the Risk Level threshold (default L3, user-configurable later) — anything at or above is still confirmed. External clients follow their own rules."*
- *"DISABLED runs with no human gate in any client. This tool's Risk Level is L{n}; only the Layer 1 sandbox isolation remains."*

Upgrades (`* → REQUIRED`) are silent.

**Chat-side AUTO_APPROVE override:** when Agentic Chat is about to call a built-in tool whose `mode` is `AUTO_APPROVE`, it overrides with an inline confirm card if the tool's Risk Level is **≥ threshold** (default `L3`). With the default threshold:

- `L0` / `L1` / `L2` → auto-execute, inline "auto-approved (L{n}) tool run" notice
- `L3` / `L4` / `L5` → user confirm card before the call proceeds; `DECLINE` synthesises a "user declined the call" tool response back to the model

The threshold lives on a service method so a later chat-settings UI can bind it (planned choices: `L3` / `L4` / `L5`). External MCP tools have no Risk Level metadata available locally, so the override does not apply to them; they follow their host client's policy.

**Status:** the `ToolManifest.HumanInTheLoop` record is defined in the codebase. The wrapper (`HumanInTheLoopToolCallback`), JSON parser hookup, builder radio, downgrade-confirm modal, chat-side threshold override, and the chat's elicitation UI handler land in the next feature pass. The contract above is fixed — this page is the user-facing spec, not a teaser.

## Policy resolution

Every tool execution runs against an `EffectivePolicy` computed at call time. Three inputs feed it: the baseline from `application.yaml`, an optional named profile chain, and the per-tool `SandboxOverrides`. The resolver enforces three invariants — the same class in both allow and deny throws, removing a baseline deny-entry only succeeds when the override explicitly removes it, and profile-chain depth is capped at 8.

```mermaid
flowchart LR
    YAML[/"application.yaml<br/>(baseline)"/]
    PROFILE[/"named profile<br/>(optional · depth ≤ 8)"/]
    OVERRIDES[/"per-tool<br/>SandboxOverrides"/]
    RESOLVER{{"EffectivePolicy<br/>Resolver"}}
    POLICY[/"EffectivePolicy"/]
    EXEC["JsToolExecutor"]

    YAML --> RESOLVER
    PROFILE -. extends .-> RESOLVER
    OVERRIDES --> RESOLVER
    RESOLVER --> POLICY --> EXEC
```

`EffectivePolicy` fields: `allowClasses`, `denyClasses`, `network` (mode + hosts), `fs` (read/write/basePath), `maxStatements`, `timeoutSeconds`.

The `EffectivePolicy` is what the executor uses for the lifetime of one call. It does not get cached across calls — every Test Run, every MCP invocation, every Agentic Chat tool call resolves a fresh policy from the current override state. That property is what lets the Sandbox & Capabilities pane behave as a *live* widening rather than a deploy-time configuration.

## Per-execution enforcement

Inside `JsToolExecutor.execute()`, the policy is applied at six distinct points. None of these are reachable from inside the JS context — they sit between the policy object and the GraalVM `Context` that runs user code.

```mermaid
flowchart TB
    CALL["Tool invocation<br/>(Test · MCP · Chat)"]
    EXEC["Virtual-thread<br/>executor"]
    CTX["Polyglot Context<br/>HostAccess · IOAccess.NONE"]
    GATE1["Class lookup gate<br/>(deny-first)"]
    BINDINGS["Global bindings"]
    G2["fetch install<br/>+ SSRF guard"]
    G3["safety.fs<br/>path resolve"]
    G4["console mask"]
    G5["safety.parser"]
    KILL{{"Future.cancel(true)<br/>on timeout"}}
    RESULT["JsExecutionResult"]

    CALL --> EXEC --> CTX
    CTX --> GATE1
    CTX --> BINDINGS
    BINDINGS --> G2 & G3 & G4 & G5
    EXEC -. wall-clock .-> KILL
    KILL --> RESULT
    G2 & G3 & G4 & G5 --> RESULT
```

Each gate is configured by `EffectivePolicy` and lives outside the JS context. Detail:

- **Class lookup gate** — `JsToolExecutor.isClassAllowed`. Deny list is evaluated first.
- **fetch install + SSRF guard** — `JsRuntimeGlobals.installFetch`. Skips installation entirely when egress is `blocked`; otherwise the SSRF four-layer guard runs in `strict`.
- **safety.fs path resolve** — `SafeFs.resolveAndValidate`. Every helper call resolves and `normalize()`-escape-checks against the base path.
- **console mask** — `installConsoleLog` + `maskKnownSecrets`. Env-backed static variables substring-masked.
- **safety.parser** — XML is XXE-hardened; XML/CSV return plain proxy trees; YAML and HTML have caveats documented at [Tool Studio → Built-in Helpers](features/tool-studio/index.md#built-in-javascript-helpers).
- **Future.cancel(true) on timeout** — host-side kill on the virtual-thread executor. Interrupts propagate into the Polyglot Context.

Three enforcement points are worth calling out:

- **`Future.cancel(true)` on a virtual-thread executor** — the wall-clock timeout is a host-side kill, not a JS-side promise rejection. A tool that infinite-loops without yielding statements still terminates within the timeout because the thread interrupt propagates through GraalVM's context. Virtual threads matter because hung tools cannot pin platform threads.
- **`installFetch()` short-circuit at `blocked`** — when a tool's `SandboxOverrides.networkMode` is `blocked`, `JsRuntimeGlobals.installFetch` does not bind `fetch` at all. Calling `fetch(...)` from JS throws `ReferenceError`. This is stricter than `strict` mode (which installs `fetch` and enforces the SSRF guard).
- **`isClassAllowed` runs deny-first** — even when an override adds a class via `addAllowClasses`, the deny list is checked first. A tool author cannot re-enable `java.lang.Runtime` by adding it to allow; the resolver rejects conflicting allow/deny entries at policy build time.

## Secret masking { #secret-masking }

This section is the reference-runtime wiring for the masking contract declared in [safe-tool-specification → § 7.4 Secret masking pipeline](safe-tool-specification.md#74-secret-masking-pipeline). Two surfaces share the same `SecretMasking` filter: the JS-side `console.log` mask inside Layer 1, and the MCP-transport-side connection/error/per-call mask.

```mermaid
flowchart TB
    SV["staticVariables[]<br/>(template values)"]
    OS["OS env / JVM properties"]
    R["EnvVarResolver.substitute"]
    M["SecretMasking.collectFromTemplate<br/>→ Set&lt;String&gt; of resolved values"]

    subgraph EGRESS["Masked text egress points"]
        direction TB
        E1["MCP tool-call log<br/>(LoggingMcpToolCallback)"]
        E2["MCP client error / event log<br/>(McpClientService — startMcpClient + testConnection)"]
        E3["Tool Studio UI<br/>connection JSON display<br/>(McpServerConfigView)"]
        E4["Audit log entries"]
        E5["console.log from tool code<br/>(JsToolExecutor.installConsoleLog)"]
    end

    SV --> R
    OS --> R
    R --> M
    M -.mask.-> E1
    M -.mask.-> E2
    M -.mask.-> E3
    M -.mask.-> E4
    M -.mask.-> E5
```

The reference implementation lives in `org.springaicommunity.playground.service.util.SecretMasking`:

| Method | Behavior |
|---|---|
| `collectFromTemplate(String template) → Set<String>` | Walks every `${NAME}` reference, resolves each via `EnvVarResolver.lookup`, collects values whose length is ≥ `MIN_MASK_LENGTH` (= 4) into an immutable `Set<String>`. |
| `mask(String text, Set<String> secrets) → String` | Iterates `secrets` and `String.replace`s each match with `***` in `text`. Plain substring substitution — prefixes / suffixes around the secret survive; only the secret itself is redacted. |

The `MIN_MASK_LENGTH = 4` floor prevents the mask from accidentally redacting `""`, `"a"`, or other near-empty resolutions that would otherwise blanket-replace innocuous substrings.

Reference-runtime call sites (each is a MUST for conformant implementations per § 7.4):

| Surface | Reference call site |
|---|---|
| Every published MCP tool-call log line | `LoggingMcpToolCallback` |
| MCP client startup exception | `McpClientService.startMcpClient` |
| MCP **Test Connection** transient failure | `McpClientService.testConnection` |
| Tool Studio UI rendering of an MCP connection's JSON | `McpServerConfigView` |
| `console.log` from inside the tool's JavaScript `code` | `JsToolExecutor.installConsoleLog` (via `maskKnownSecrets`) |

What this layer does *not* cover:

- The MCP server payload itself. If a server's tool *response* contains a credential, that text reaches chat unchanged — at that point it's tool output, not a connection-level message. Use `console.log` masking inside the tool wrapper if you need to redact tool-response content.
- Encrypted OAuth tokens. They live on a separate surface keyed on a host-bound passphrase — see [Encrypted OAuth token storage](#encrypted-oauth-token-storage) below.

Cross-references: [MCP Server → `${ENV_VAR}` substitution](features/mcp-server/index.md#custom-http-headers-and-env_var-substitution) for the placeholder syntax; [Default MCP Catalog → Environment variables](features/default-mcp-catalog/index.md#environment-variables-short-list) for the catalog-context summary.

## Encrypted OAuth token storage

A separate surface from the env-backed static-variable secrets above: when an MCP server connection uses OAuth 2.1, the resulting tokens are persisted to disk encrypted, keyed on a host- and user-bound passphrase.

| Concern | Reference runtime |
|---|---|
| **Token path** | `~/spring-ai-playground/mcp/oauth-tokens/` (one file per authorized client), written by `EncryptedFileOAuth2AuthorizedClientRepository`. |
| **Encryption** | AES via Spring Security's `Encryptors.text(passphrase, salt)` (`OAuthTokenEncryptor`). |
| **Passphrase** | `hostname + ":" + user.home`, derived at process start. Never persisted to disk. |
| **Salt** | `~/spring-ai-playground/.security/oauth.salt`, generated by `KeyGenerators.string()` on first use and `chmod 0600` on POSIX platforms. |

The host-bound passphrase is what gives the tokens their geographic lock: copying the token directory to a different host or a different user account makes the same playground build unable to decrypt them — a backup restore requires both `mcp/oauth-tokens/` and `.security/oauth.salt`. This is intentional. Disk-copy alone is not sufficient to recover plaintext tokens.

OAuth tokens are independent of the `SecretMasking` pipeline above: tokens never appear in connection JSON in plaintext, so there is nothing to mask at egress for them — the encrypted on-disk file is the only artifact, and the in-memory plaintext is short-lived inside Spring Security's `OAuth2AuthorizedClient`.

## Component view

The components in Layer 1 form a small, single-direction graph: the resolver builds an `EffectivePolicy` once per call, the executor reads it to configure GraalVM, the global bindings consult it for per-helper limits, and the posture calculator reads the same overrides to produce the badge.

```mermaid
flowchart LR
    subgraph svc["service/tool"]
        SPEC["ToolSpec"]
        ACT["ToolActivation<br/>Calculator"]
        MCD["McpToolDefinition"]
    end
    subgraph policy["service/tool/policy"]
        RES["EffectivePolicy<br/>Resolver"]
        POSE["SandboxPosture<br/>Calculator"]
    end
    subgraph runtime["service/tool/runtime"]
        EXEC["JsToolExecutor"]
        GLOB["JsRuntimeGlobals"]
        HTTP["SafeHttpFetch"]
        FS["SafeFs"]
    end
    subgraph spring["Spring AI MCP"]
        SRV["McpSyncServer"]
        CB["FunctionToolCallback"]
    end
    BASE[/"application.yaml"/]

    BASE --> RES
    SPEC --> RES
    SPEC --> POSE
    RES --> EXEC
    POSE --> MCD
    EXEC --> GLOB
    GLOB --> HTTP & FS
    EXEC --> CB
    ACT --> CB
    MCD --> SRV
    CB --> SRV
```

Two design choices are worth noting:

- **`SandboxPostureCalculator` is pure** — it has no I/O and no shared state. Same inputs always yield the same `RiskLevel`. That property makes the badge testable and predictable; the resolver can call it during draft editing to show the badge live before any execution happens.
- **`JsRuntimeGlobals.installFetch` is the only place `SafeHttpFetch` is wired** — there is no other path that reaches `HttpClient` from JS. If the install short-circuits (`blocked`), no HTTP at all.

## Spring AI / Spring Security integration

Tool Studio sits on top of two distinct Spring projects:

- **Spring AI** — `spring-ai-starter-mcp-server` exposes the built-in MCP server over Streamable HTTP at `/mcp`. Every Local-Passed tool registers itself with the server's `McpSyncServer` via `addTool(FunctionToolCallback)`. The sandbox runs *inside* the callback, so MCP never sees a tool that hasn't been through `JsToolExecutor`.
- **Spring Security** — sits in front of the MCP endpoint. Disabled by default for the local single-user case; enabled via Spring AI's official [MCP Security](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-security.html) configuration for deployed scenarios.

```mermaid
flowchart LR
    EXT["External MCP clients<br/>(Claude · Cursor · …)"]
    SEC["Spring Security<br/>(Layer 3)"]
    TRANS["Streamable HTTP<br/>/mcp"]
    SYNC["McpSyncServer"]
    SAND["Sandbox<br/>(Layer 1 + 2)"]

    EXT --> SEC --> TRANS --> SYNC --> SAND
```

The arrows go one way: callers cannot reach the sandbox without traversing the transport and (when enabled) the security filter chain. `SecurityFilterChain` is disabled by default for local single-user; OAuth2 / API key are the typical choices when enabled. The sandbox in the bottom box is everything from the previous two diagrams — the sandbox is what gives Spring AI's MCP server a safe runtime for user-authored tools; Spring Security is what gives it an adversarial perimeter. Both fail to different threats.

## Risk Level decision matrix

Each tool's safety posture is summarised by a single badge — the **Risk Level**. The code calls it `RiskLevel` (an enum in `ToolManifest.Sandbox.RiskLevel`); it is the inverse of "how safe the tool is":

- **Lower Risk Level = safer / more sandboxed.** `L0` means the tool runs entirely on the default sandbox surface with no widening — the strongest safety guarantees.
- **Higher Risk Level = less safe / less sandboxed.** Each step up is the result of a declared `SandboxOverrides` widening, computed by `SandboxPostureCalculator.compute()`.

There is no separate "Safety Level" knob — the Risk Level *is* the safety indicator, expressed from the risk side so that "higher number = needs more attention before publish" maps directly to review effort. The user-facing meaning of the L0–L5 badge, summarised:

| Level | Posture | Typical capabilities | Publish recommendation |
|---|---|---|---|
| **L0** | Safest. Baseline defaults. | No I/O. Pure-compute helpers only. | Auto-publish on Local Pass. |
| **L3** | Safe with scoped widening. | `networkMode: allowlist` to specific hosts, OR `fileRead: true`, OR 1–2 non-critical deny removals. | Default-publish — review the host list / paths. |
| **L4** | Broader access. Review before publish. | `networkMode: allowlist` with `*`, `networkMode: open`, `fileWrite: true`, file-read class added, reflection class added, ≥3 deny removals. | **Review before publish.** Justify the breadth. |
| **L5** | Effectively unsandboxed. | `System` / `Runtime` / `Process` / `ProcessBuilder` re-enabled, OR file-write classes added directly. | **Trusted authors only.** Process spawn or raw write means the tool has the same authority as the JVM itself. |

The full bullet-by-bullet rule set (which signal pushes the badge to which level) is in [Tool Studio → Risk Level Reference](features/tool-studio/index.md#risk-level-reference).

The Local Pass gate runs against the tool's *effective* policy, so a tool that exceeds its own declared capabilities fails its test before publish. This matters because the badge is not enforcement — the policy is. The badge advertises what the policy implies.

## Threat-to-layer mapping

Concrete threats, the layer that catches each, and the mechanism. This is the reference an operator uses to reason about deployment risk.

| Threat | Layer | Mechanism |
|---|---|---|
| Tool calls `Java.type("java.lang.Runtime").getRuntime().exec(...)` | Layer 1 | `deny-classes` evaluated before allow-classes (`JsToolExecutor.isClassAllowed`) |
| Tool calls `fetch("http://169.254.169.254/...")` to reach cloud metadata | Layer 1 (strict egress) | SSRF four-layer guard — literal-IP private/reserved check rejects |
| Tool calls `fetch("attacker.example")` where DNS resolves to RFC 1918 | Layer 1 (strict egress) | DNS resolve — every returned address checked against private/reserved |
| Tool calls `fetch` with a host in CGNAT (`100.64.0.0/10`) | Layer 1 (strict egress) | Explicit CGNAT range rejection (not covered by `isSiteLocalAddress`) |
| Tool reads `safety.fs.readText("../../etc/passwd")` | Layer 1 | `SafeFs.resolveAndValidate` — `normalize()` + `startsWith(base)` |
| Tool runs `while (true) {}` or unbounded recursion | Layer 1 | `max-statements` GraalVM budget + virtual-thread `Future.cancel(true)` |
| Tool calls `console.log` with an env-backed Bearer token | Layer 1 | `maskKnownSecrets` substring-masks resolved env values |
| Tool author wants to call a private API server | Layer 2 (declared widening) | `networkMode: allowlist` + `hostsAllow` — badge becomes L3, visible before publish |
| Tool author wants raw `java.io.File` read | Layer 2 (declared widening) | `addAllowClasses: [java.io.File*]` — badge becomes L4 |
| Tool author wants raw `java.io.FileWriter` write | Layer 2 (declared widening) | `addAllowClasses: [java.io.FileWriter*]` — badge becomes **L5** |
| External attacker calls `/mcp` from another machine | Layer 3 | Spring Security configuration (auth / network ACL) on MCP transport |
| Bind-to-all-interfaces accident | Layer 3 (defaults) | `server.address` defaults to localhost; explicit operator change required |

The first seven threats are blocked at the **always-on Java sandbox** — no per-tool configuration can disable them. The next three are *opt-in widenings* that surface as risk-level badges before publish, so the gate is review rather than runtime. The last two live entirely on the **MCP transport** layer and are independent of how individual tools were authored.

## Known limitations

The sandbox is intentionally defense-in-depth rather than adversarial-grade. Each limitation below is a current-state caveat with a documented mitigation; concrete follow-up work is tracked in [GitHub Issues](https://github.com/spring-ai-community/spring-ai-playground/issues) under the `sandbox` label rather than here, so the architecture page does not drift out of sync with what's actually being worked on.

### Reflection-after-load gap

`JsToolExecutor.allowHostClassLookup` gates `Java.type(...)` calls, but once a tool holds a `Class` object obtained through another path (for example a `Class.forName` analogue or a method that returns one), reflection on that handle can route around the lookup gate. The deny-list catches the obvious cases (`java.lang.Class`, `java.lang.reflect.*`, `java.lang.invoke.*`), but a tighter `HostAccess` builder or a `Class.forName`-specific interceptor would close the residual surface.

**Mitigation today**: the deny-list already rejects `java.lang.Class`, `java.lang.ClassLoader`, `java.lang.reflect.*`, `java.lang.invoke.*`, `ServiceLoader`, and `java.util.spi.*`. The gap is theoretical for any tool that runs against the default allow-classes (`java.lang/math/time/util/text.*`), because none of those packages return arbitrary `Class` objects. The gap matters only for tools that have opted into `addAllowClasses` for something exotic — which already raises the badge to L4 or L5.

### Allow-classes pattern granularity

Today `java.lang.*` in `allow-classes` matches `java.lang.reflect.Method` because the pattern matcher uses `startsWith(prefix + ".")`. The deny-list catches reflection explicitly, so the practical effect is contained — but the pattern semantics are wider than the dotted-name suggests. A future pass could either tighten the matcher to single-package (so `java.lang.*` does not match `java.lang.reflect.*`) or require explicit nested allow entries.

**Mitigation today**: `deny-classes` lists `java.lang.reflect.*`, `java.lang.invoke.*`, `java.lang.Thread`, `java.lang.ThreadGroup`, `java.lang.ClassLoader` explicitly, and deny wins. The pattern looseness is documented but does not weaken the default posture.

### `safety.parser.yaml` constructor choice

The YAML helper uses SnakeYAML's regular `Constructor` rather than `SafeConstructor`. Global tags such as `!!class.name` cause class instantiation during `load`. The output gets coerced through `jsonToProxy` before reaching JS, so user code never sees the resulting host object directly, but the instantiation has already happened in the JVM.

**Mitigation today**: documented in [Tool Studio → Built-in Helpers](features/tool-studio/index.md#built-in-javascript-helpers). Treat YAML input as trusted-source-only.

### `safety.parser.html` returns host `Document`

The HTML helper uses jsoup and returns the raw `org.jsoup.nodes.Document` host object rather than a plain proxy tree (unlike the XML helper, which returns a plain `{tag, attrs, text, children}` proxy). The class itself is not in the default allow-classes, so JS code cannot construct new jsoup instances via `Java.type(...)`, but it can call methods on the returned object. A future pass could either wrap jsoup methods into a fixed surface or move to a plain proxy tree like the XML helper.

**Mitigation today**: documented. The deny-list still blocks every escape vector, so the worst case is the tool author calls jsoup methods that already exist on the returned object.

### Env-var masking substring-only

`maskKnownSecrets` does substring replacement on console output. If an env-backed static variable is *not* referenced as an anchored full-string `${VAR}`, the secret value is not auto-collected, and a `console.log` that constructs the same value through string concatenation will not be masked. The Test Run path collects the actual resolved value, so any later log that contains it is masked; the gap is for values constructed after collection.

**Mitigation today**: anchored env references are the documented contract; the substring mask is best-effort secondary.

## Configuration reference

Authoritative configuration lives in two places:

- **Baseline policy**: `src/main/resources/application.yaml` under `spring.ai.playground.tool-studio.js-sandbox`. See [Tool Studio → JavaScript Runtime](features/tool-studio/index.md#javascript-runtime) for the keys and defaults.
- **Per-tool overrides**: the `sandboxOverrides` block of each `ToolSpec` (in `default-tool-specs*.json` for bundled tools, or in user-authored tools saved through Tool Studio). See [Tool Studio → SandboxOverrides JSON shape](features/tool-studio/index.md#sandboxoverrides-json-shape).

Operational reference for the wider system runtime — UI surfaces, service layer, MCP transport, advisor chain — is on the [Application Architecture](architecture.md) page.

## Further reading

- [Application Architecture](architecture.md) — runtime layers, feature modules, data flows
- [Tool Studio → Safety](features/tool-studio/index.md#safety) — the user-facing three-layer summary, in context
- [Tool Studio → Sandbox & Capabilities](features/tool-studio/index.md#sandbox-capabilities) — `SandboxOverrides` JSON shape, egress modes, SSRF four-layer steps, Risk Level rules
- [GraalVM Security Guide](https://www.graalvm.org/latest/security-guide/sandboxing/) — Polyglot sandbox policies, `HostAccess`, `IOAccess`, `ResourceLimits`
- [Spring AI MCP Security](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-security.html) — Layer 3 configuration
