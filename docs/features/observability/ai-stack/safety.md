title: Safety
description: MCP risk-model and sandbox safety signals - L0–L5 risk distribution, tool-poisoning hits, content-hash tamper rejects, HITL approval rate, and sandbox guard blocks, all from lifetime counters.

# Safety

![Safety dashboard - six KPI cards (Risk signals, Tamper rejects, Poisoning hits, Floor overrides, HITL approval rate, Sandbox guard blocks) above lifetime bar charts for risk signals by type and risk level distribution, with a recent risk-events timeline below](../../../assets/images/observability/safety-full.png)

**Purpose** - the security view across the MCP risk model and the JS tool sandbox. It rolls up every safety signal the runtime emits - per-call risk levels, tool-description poisoning scans, content-hash tamper detection, human-in-the-loop approvals, and sandbox guard rejections - into one dashboard, so an operator can answer *"is the agent being fed anything dangerous, and did the guards catch it?"* at a glance.

## When to look here

- *"How risky is the tool surface the agent can reach?"* - Risk level distribution (L0–L5).
- *"Did an upstream tool quietly change its definition?"* - Tamper rejects KPI (content-hash ledger, TOFU).
- *"Is any tool description trying to inject instructions?"* - Poisoning hits KPI + the risk-event timeline.
- *"Are humans actually approving the gated calls?"* - HITL approval rate.
- *"Is the sandbox blocking unsafe actions?"* - Sandbox guard blocks (SSRF / filesystem policy).

## Data source

Two streams, both lifetime (not windowed):

- **Counters** via `SystemMetricsSnapshot` - `saip.risk.signal` (grouped by signal type), `saip.tool.risk` (grouped by composed level `L0`–`L5`), `mcp.hitl.decision` (grouped by outcome), and `sandbox.guard.blocked` (grouped by reason). The `saip.*` counters are emitted by `McpRiskSignalLogger` (the risk-signal sink) and `McpToolObservationFilter`.
- **Event timeline** via `McpRiskEventRingBuffer` - the most recent risk events (server/tool risk computed, floor override, hash mismatch, composition lifecycle, poisoning hit) with their type and summary.

See [MCP Server Safety](../../../mcp-server-safety.md) for the risk model these signals come from.

## Controls

Shares the [Observability global settings](../index.md#global-settings), but the KPI cards and bar charts are **lifetime counters** - the time-window preset does not scope them. Only the *Recent risk events* timeline reflects recency (most recent 50).

## KPI cards (six)

| Card | Shows | Source |
|---|---|---|
| Risk signals | Σ all `saip.risk.signal` events (lifetime) | `saip.risk.signal` counter |
| Tamper rejects | `hash-ledger-mismatch` - a default/exposed tool's content hash changed since first seen (TOFU) | `saip.risk.signal{type=hash-ledger-mismatch}` |
| Poisoning hits | `poisoning-hit` - a tool description/schema matched a prompt-injection pattern | `saip.risk.signal{type=poisoning-hit}` |
| Floor overrides | `floor-override-triggered` - a risk floor rule forced a higher level | `saip.risk.signal{type=floor-override-triggered}` |
| HITL approval rate | % approved of all human-in-the-loop decisions | `mcp.hitl.decision` counter |
| Sandbox guard blocks | Σ `sandbox.guard.blocked` (SSRF + filesystem policy rejections) | `sandbox.guard.blocked` counter |

## Charts (four)

| Chart | Type | Reading |
|---|---|---|
| Risk signals by type | Horizontal bar | `saip.risk.signal` grouped by type - server-risk-computed, tool-publish-risk-computed, floor-override-triggered, hash-ledger-mismatch, composition-lifecycle, poisoning-hit |
| Risk level distribution | Horizontal bar, L0→L5 in order | Final composed risk level of each executed MCP tool call (`saip.tool.risk`). L0 verified · L1 safe · L2 low · L3 moderate · L4 high · L5 critical |
| HITL decisions | Horizontal bar | `mcp.hitl.decision` outcomes (chat-side + MCP-server-side): approved / declined / denied / elicit-failed |
| Sandbox guard blocks | Horizontal bar | `sandbox.guard.blocked` by reason: host-not-in-allowlist, private-ip, too-many-redirects, body-too-large, … |

## Tables

**Recent risk events** - a scrollable timeline of the latest events from `McpRiskEventRingBuffer`, each row showing time, a type badge (warn-tinted for failures), and a one-line summary. Populated as MCP servers and tools are registered, exposed, composed, or fail an integrity/poisoning check.

## Cross-references

- [MCP Server Safety](../../../mcp-server-safety.md) - the L0–L5 risk model, content-hash ledger, and poisoning scanner this dashboard surfaces
- [Safety Architecture](../../../safety-architecture.md) - the JS sandbox layers behind the `Sandbox guard blocks` counter
- [Human-in-the-Loop](../../../hitl-architecture.md) - the approval gate behind the `mcp.hitl.decision` counter
- [Tool Studio](tool-studio.md) - sibling AI Stack tab for in-process tool execution
- [Observability Architecture](../../../observability-architecture.md) - the counter / metrics pipeline
