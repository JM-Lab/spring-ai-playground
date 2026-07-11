title: OWASP MCP Top 10 Coverage
description: How Spring AI Playground's enforced controls map to the OWASP MCP Top 10 (MCP01-MCP10) - what is enforced, what is partial, and the honest gaps.

# OWASP MCP Top 10 Coverage

The [OWASP MCP Top 10](https://owasp.org/www-project-mcp-top-10/) (categories `MCP01:2025` through `MCP10:2025`) is the first OWASP catalog of the risks most likely to compromise a Model Context Protocol deployment. This page maps each category to the **controls Spring AI Playground actually ships**, and is deliberately honest about where coverage is partial or absent. It is a defensive coverage map, not a claim of completeness.

This complements the architecture documents that describe each control in depth:

- [AI Agent Tool Safety](safety-architecture.md) - the sandbox that contains locally-authored JS tools, and the isolation-tier model
- [MCP Server Safety](mcp-server-safety.md) - the client-side risk model for external servers and re-exposed tools
- [Safe Tool Specification](safe-tool-specification.md) - the normative spec behind the L0-L5 posture
- [Human-in-the-Loop Approval](hitl-architecture.md) - the per-call approval gate
- [AI Agent Observability](observability-architecture.md) - the audit and telemetry layer

## How to read this map { #how-to-read }

Each category is rated on what the runtime does, not on intent:

| Rating | Meaning |
|---|---|
| **Enforced** | A runtime control blocks or caps the risk by default (deny by default, hard ceiling, or tamper-reject). |
| **Partial** | A control exists but is advisory, covers a subset, or depends on operator configuration. |
| **Opt-in** | The full control is available and one module away, shipped off by default for the local-first single-user model. |
| **Gap (mitigated)** | No dedicated control; blast-radius limits reduce impact instead. |

!!! note "Scope and threat model"
    Spring AI Playground is a **local-first, single-user** reference implementation. Its controls run **in-process** (one JVM), which is the right boundary for author accidents and untrusted *external* tools, but is **not an adversarial kernel boundary** on its own. For a harder boundary the whole process nests inside the OS and container isolation it runs under. See [Isolation tiers](safety-architecture.md#isolation-tiers). Ratings below are read against that model.

## Coverage at a glance { #at-a-glance }

The table is the summary; each row links to its section below. Detailed mechanics live in the architecture docs linked above, not here.

| # | Risk | SAIP primary control | Coverage |
|---|---|---|---|
| **MCP01** | Token Mismanagement & Secret Exposure | Encrypted token store, env-var secret masking | Partial |
| **MCP02** | Privilege Escalation via Scope Creep | Monotonic L0-L5 posture + composition max-risk cap | **Enforced** |
| **MCP03** | Tool Poisoning | Poisoning scan (advisory) + fingerprint ledger tamper-gate | **Enforced** (tamper) |
| **MCP04** | Supply Chain & Dependency Tampering | Pinned default-tool integrity manifest (hard gate) | Partial |
| **MCP05** | Command Injection & Execution | GraalJS sandbox + SafeFs + SafeHttpFetch | **Enforced** (in-process) |
| **MCP06** | Intent Flow Subversion | HITL gates resulting actions; capability ceiling bounds blast radius | Gap (mitigated) |
| **MCP07** | Insufficient Authentication & Authorization | Outbound OAuth 2.1 client (shipped); inbound `/mcp` auth via opt-in `mcp-security` module | Opt-in |
| **MCP08** | Lack of Audit and Telemetry | Risk-signal sink + metrics + MDC + structured logs | **Enforced** |
| **MCP09** | Shadow MCP Servers | No auto-discovery + TOFU change-detection + L5 floor | Partial |
| **MCP10** | Context Injection & Over-Sharing | `sendsUserData` scoring + exposure controls + HITL | Partial |

## MCP01 - Token Mismanagement & Secret Exposure { #mcp01 }

**Risk:** hard-coded credentials, long-lived tokens, or secrets leaked into logs and model memory.

**Coverage: Partial.** API keys are read from environment variables, never embedded in config. OAuth tokens for external MCP servers are stored **encrypted at rest** in [`EncryptedFileOAuth2AuthorizedClientRepository`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/service/oauth/EncryptedFileOAuth2AuthorizedClientRepository.java) (symmetric encryption keyed off a host-derived passphrase plus a random salt). The [safe-wrapping contract](mcp-server-safety.md#wrapping-contract) **masks upstream connection secrets** out of error messages before they reach logs or chat, via `SecretMasking`.

**Limitations:** the encryption key is host-local (not portable, and only as strong as host access). The encrypted token files do not yet carry restrictive POSIX permissions (the key salt does), so encryption-at-rest, not file mode, is the protecting layer. Secret masking covers env-var-referenced values, not secrets a tool emits in its own output.

## MCP02 - Privilege Escalation via Scope Creep { #mcp02 }

**Risk:** loosely-defined permissions that expand over time, granting an agent more capability than intended.

**Coverage: Enforced.** This is the playground's core mechanism. [`SandboxPostureCalculator`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/service/tool/policy/SandboxPostureCalculator.java) resolves each tool's network, filesystem (including a `destructive` flag for irreversible file operations, floored at L5), and class permissions to an **ordered L0-L5 level via a monotonic max-merge** - a capability is never silently lowered, only raised. The single downward adjustment that exists is explicit, not silent: a tool whose spec requires [human-in-the-loop approval](hitl-architecture.md) displays a one-band-lower *effective* level next to its inherent one (`L5 → L4`), and only while its description passes the [poisoning scan](#mcp03). External tools are scored independently by [`McpToolPublishRiskCalculator`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/service/mcp/risk/McpToolPublishRiskCalculator.java), where a `sideEffectScope` of `REMOTE_WRITE_SCOPE`/`REMOTE_ADMIN_SCOPE` raises the score. Exposure is then **hard-capped**: a composition refuses to enable any member whose resolved level exceeds its `maxRiskLevel` (deny, no approval path), enforced by ordinal comparison in the composition service.

**Limitations:** the `maxRiskLevel` ceiling can be raised by an operator re-applying exposure; there is no separate re-review gate on raising the cap. `sideEffectScope` contributes to scoring but is not itself an access ceiling.

## MCP03 - Tool Poisoning { #mcp03 }

**Risk:** malicious instructions hidden in tool descriptions, parameter schemas, or return values that manipulate the model.

**Coverage: Enforced (tamper) + advisory (detection).** Two distinct controls, both described in [MCP Server Safety](mcp-server-safety.md#poisoning-scan). [`McpToolPoisoningScanner`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/service/mcp/risk/McpToolPoisoningScanner.java) scans every tool description for **eight injection signatures** (hidden-instruction, system-prompt override, role hijack, zero-width / RTL-override / homoglyph unicode, ANSI escapes, exfiltration directive) and raises a warning chip; the ninth defined signature - a cross-server imperative - is enforced on the exposure path by the [composition shadowing rules](mcp-server-safety.md#shadowing-rules) instead. Separately, [`McpToolHashLedger`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/service/mcp/risk/McpToolHashLedger.java) keeps a SHA-256 fingerprint of each tool's canonical content (name + description + input schema); a silently redefined ("rug-pull") tool flips to `AWAITING_REREVIEW` and is **withheld from re-exposure** until a human re-approves - that gate is enforced.

**Limitations:** the poisoning scan is **advisory for publish** (it warns, it does not hard-block publish) - though a hit is not free: a flagged description forfeits the [HITL risk-mitigation credit](#mcp02), so the tool is scored at its full inherent level. The scan and the fingerprint cover the description and schema, **not** upstream annotations on the live composition path (an annotation-only redefinition is a known blind spot), and not tool *return values* at runtime (see [MCP06](#mcp06)).

## MCP04 - Software Supply Chain Attacks & Dependency Tampering { #mcp04 }

**Risk:** tampered tool definitions, dependencies, or build artifacts.

**Coverage: Partial.** The **built-in default tools** are protected by a pinned integrity baseline, enforced in two places. At runtime, [`CanonicalHasher`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/service/mcp/risk/CanonicalHasher.java) fingerprints each default tool (name, description, code and code type, params, static variables, sandbox overrides including the `destructive` flag, category, tags) and [`DefaultIntegrityVerifier`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/service/mcp/risk/DefaultIntegrityVerifier.java) checks every tool that claims a reserved default name against the shipped default specs re-hashed at boot: a tampered default is marked `REVOKED_TAMPERED` and **blocked from exposure**, and an impersonation guard stops an external tool from claiming a reserved built-in name. In the repository, the shipped specs are themselves pinned to a golden manifest (`resources/integrity/default-integrity.json`): a [guard test](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/test/java/org/springaicommunity/playground/service/mcp/risk/DefaultIntegrityManifestTest.java) fails when the shipped hashes drift from the manifest, and CI runs that environment-independent guard on every push and pull request.

**Limitations:** this covers built-in tool **definitions** only. It does not attest external MCP server code or transport, does not scan the playground's own runtime dependencies (Spring AI, Jackson, Vaadin, etc.), and the baseline is build-time static (a legitimate tool edit must be re-pinned).

## MCP05 - Command Injection & Execution { #mcp05 }

**Risk:** an agent constructs and executes commands, code, or requests from untrusted input.

**Coverage: Enforced (in-process).** Locally-authored tools run in the [GraalVM JavaScript sandbox](safety-architecture.md), which is **deny-all by default**: Java interop is opt-in, and `java.lang.System` / `Runtime` / `Process` / `ProcessBuilder` and reflection are blocked, with a statement limit against runaway code. Filesystem access goes through [`SafeFs`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/service/tool/runtime/SafeFs.java), which resolves symbolic links with `toRealPath` **before** the containment check and refuses writes through a symlink. Outbound HTTP goes through [`SafeHttpFetch`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/service/tool/runtime/SafeHttpFetch.java), whose strict mode blocks SSRF to loopback, link-local, and private addresses after DNS resolution.

**Limitations:** this is an **in-process** boundary, not a kernel one - the design assumption is a trusted single-user host, with OS/container isolation as the outer tier ([Isolation tiers](safety-architecture.md#isolation-tiers)). External MCP servers execute in their own process, outside this sandbox; the playground governs the **call**, not the upstream server's internals.

## MCP06 - Intent Flow Subversion { #mcp06 }

**Risk:** malicious instructions embedded in *retrieved context* (resource documents, tool outputs) that override the user's intent and steer the agent toward an attacker's goal - goal hijacking and unauthorized autonomous actions, often while the agent still appears to be fulfilling the original request.

!!! warning "Gap (mitigated), stated plainly"
    The playground has **no runtime detector for injected instructions in retrieved content or tool output**. The [poisoning scanner](#mcp03) inspects tool *definitions* at registration time, not the *content* an agent reads at call time.

**Coverage: Gap, mitigated.** SAIP does not detect or prevent the subversion itself; it constrains the *consequence*. Per-call [HITL approval](hitl-architecture.md) puts a human in front of each gated tool action - and every built-in tool that writes, moves, or deletes files ships with approval `REQUIRED` - so a hijacked plan cannot fire a destructive call unattended; the L0-L5 capability ceiling ([MCP02](#mcp02)) bounds what a subverted agent can reach at all; oversized tool results are truncated; and [composition shadowing rules](mcp-server-safety.md#shadowing-rules) reject one specific vector (a tool whose description imperatively names another tool). What is missing is detection of the injected instructions in the retrieved content - that stays the operator's and model's responsibility.

## MCP07 - Insufficient Authentication & Authorization { #mcp07 }

**Risk:** servers, tools, or agents that fail to verify identity or enforce access control.

**Coverage: outbound shipped, inbound opt-in.** For connecting **to** external MCP servers the playground ships a full **OAuth 2.1 client** (authorization-code and refresh-token flows, encrypted token store). For its **own** built-in server, `/mcp` is `permitAll` by default - a deliberate choice for the local-first, single-user model, where the agent reaches the built-in server over loopback and there is no second party to authenticate. This is not a missing capability: gating the endpoint is a one-module opt-in.

!!! info "Enabling inbound auth on the built-in server"
    The [`spring-ai-community/mcp-security`](https://github.com/spring-ai-community/mcp-security) module (a sibling project in the same org) turns the built-in server into an OAuth2 **resource server** that requires a Bearer token on `/mcp`. The recommended path is the Boot auto-configuration: add `org.springaicommunity:mcp-server-security-spring-boot` (alongside `spring-boot-starter-oauth2-resource-server`) and set `spring.security.oauth2.resourceserver.jwt.issuer-uri` - the auto-config then secures `/mcp` with no extra code. (For manual control instead, the `mcp-server-security` module exposes `McpServerOAuth2Configurer.mcpServerOAuth2(...)` to wire into the [`SecurityConfig`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/config/SecurityConfig.java) filter chain, replacing the current `permitAll` on `/mcp`.) The playground already ships the outbound half (`spring-boot-starter-oauth2-client`); the inbound half is left out of the default build on purpose. For a deployed or multi-user instance, also set `server.address=127.0.0.1` - the default binds to all interfaces because `server.address` is unset.

## MCP08 - Lack of Audit and Telemetry { #mcp08 }

**Risk:** no record of what tools did, so abuse is invisible.

**Coverage: Enforced.** Every risk computation emits a structured event - `ServerRiskComputed`, `ToolPublishRiskComputed`, `FloorOverrideTriggered`, `PoisoningHit`, `HashLedgerMismatch`, `CompositionLifecycle` - through a wired [`McpRiskSignalSink`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/service/mcp/risk/McpRiskSignalSink.java) (the logger implementation replaces the NOOP fallback). Every wrapped tool call emits `mcp.tool.start` / `done` / `crash` with duration and a `saip.tool.risk` counter tagged by level, and pushes per-call MDC context (`saip.cid`, `saip.tool.origin`, `saip.upstream.*`, `saip.risk.*`). Identity MDC (user, session, conversation, trace) flows into structured logs and the [Safety dashboard](features/observability/ai-stack/safety.md).

**Limitations:** telemetry is local (no built-in tamper-evident remote audit sink); retention follows the local log rotation policy.

## MCP09 - Shadow MCP Servers { #mcp09 }

**Risk:** unauthorized or unvetted MCP servers connected to the agent.

**Coverage: Partial.** There is **no silent auto-discovery** - every server is registered by explicit operator action (catalog pick or hand-typed connection). An unvetted, no-auth, unknown-host server trips a **floor override to L5** in [`McpServerRiskCalculator`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/service/mcp/risk/McpServerRiskCalculator.java) (trigger `non_loopback_no_auth_trust_unknown`, shown on the chip as `no-auth-unknown`), and the [fingerprint ledger](#mcp03) catches a server that **changes its tool definitions after connection** (trust-on-first-use pinning), withholding the changed tool pending re-review.

**Limitations:** TOFU pins on **change**, so a server's *first* presentation is trusted by definition (the L5 chip flags it, but does not block the operator from exposing it). There is no cryptographic attestation of remote server identity.

## MCP10 - Context Injection & Over-Sharing { #mcp10 }

**Risk:** excessive or sensitive data exposed to tools, or context leaked across tools.

**Coverage: Partial.** A `sendsUserData` signal raises a tool's risk score, surfacing data-exfiltrating tools. What the model can see is constrained by **exposure controls**: a per-server exposure mode (built-in only / composed only / both), a Local Pass gate on which tools publish, and per-chat tool selection so a conversation only sees the tools the operator picked. [Composition shadowing rules](mcp-server-safety.md#shadowing-rules) block cross-server references that would leak one tool's scope into another. HITL adds a human checkpoint before a data-handling tool runs.

**Limitations:** these are visibility and risk-scoring controls plus operator configuration, not an automated data-loss-prevention gate; `sendsUserData` adjusts the score but does not by itself block exposure.

## Known gaps and non-goals { #gaps }

Stated together so the coverage above is not read as completeness:

- **Intent flow subversion ([MCP06](#mcp06))** - no detector for instructions injected through retrieved content or tool results; the playground gates the resulting actions (HITL) and bounds blast radius rather than detecting the injection.
- **Built-in server authentication ([MCP07](#mcp07))** - inbound auth on `/mcp` is shipped **off by default** for the local-first model, not absent: the [`mcp-security`](https://github.com/spring-ai-community/mcp-security) module gates it as an OAuth2 resource server when added. Harden a deployed instance with `server.address=127.0.0.1`.
- **Supply chain depth ([MCP04](#mcp04))** - integrity pinning covers built-in tool definitions, not external server code or the runtime's own dependencies.
- **In-process boundary ([MCP05](#mcp05))** - the sandbox is the innermost tier, not an adversarial kernel boundary; OS and container isolation are the outer tiers.

## Further reading

- [MCP Server Safety](mcp-server-safety.md) - the full risk engine behind MCP02, MCP03, MCP09, MCP10
- [AI Agent Tool Safety](safety-architecture.md) - the sandbox and isolation tiers behind MCP05, and the Layer 3 transport notes behind MCP07
- [Human-in-the-Loop Approval](hitl-architecture.md) - the per-call gate referenced across MCP02, MCP06, MCP10
- [AI Agent Observability](observability-architecture.md) - the audit layer behind MCP08
- [OWASP MCP Top 10](https://owasp.org/www-project-mcp-top-10/) - the upstream catalog
