import json
import re
from pathlib import Path

from mkdocs.exceptions import PluginError

ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "src/main/resources"
DOCS = ROOT / "docs"

NUMBER_WORDS = {
    10: "ten", 11: "eleven", 12: "twelve", 13: "thirteen", 14: "fourteen",
    15: "fifteen", 16: "sixteen", 17: "seventeen", 18: "eighteen", 19: "nineteen", 20: "twenty",
}

MCP_GROUP_PAGES = {
    "Productivity & Communication": "productivity.md",
    "Dev & Project Management": "dev.md",
    "Data & Cloud": "data-cloud.md",
    "Business": "business.md",
    "Search": "search.md",
    "Examples": "examples.md",
}


def _read(path):
    return path.read_text(encoding="utf-8")


def _tool_total():
    total = 0
    for spec in sorted((RESOURCES / "tool").glob("default-tool-specs*.json")):
        total += len(json.loads(_read(spec)))
    return total


def _integrity_total():
    manifest = json.loads(_read(RESOURCES / "integrity/default-integrity.json"))
    entries = manifest.get("tools", manifest.get("entries", manifest))
    return len(entries)


def _mcp_totals():
    remote = len(json.loads(_read(RESOURCES / "mcp/default-mcp-specs.json")))
    stdio = len(json.loads(_read(RESOURCES / "mcp/default-mcp-specs-stdio-mac.json")))
    return remote, stdio


def _dashboard_total():
    view = ROOT / "src/main/java/org/springaicommunity/playground/webui/observability/ObservabilityView.java"
    return len(re.findall(r'\bregister\("', _read(view)))


def _tutorial_total():
    return len([p for p in (DOCS / "tutorials").glob("*.md") if re.match(r"^\d+-", p.name)])


def _expect_numbers(errors, label, text, pattern, expected, min_matches=1):
    matches = re.findall(pattern, text)
    if len(matches) < min_matches:
        errors.append(f"{label}: pattern /{pattern}/ not found - update count_guard.py if the wording changed")
        return
    for m in matches:
        values = m if isinstance(m, tuple) else (m,)
        for v in values:
            if int(v) != expected:
                errors.append(f"{label}: says {v}, catalog says {expected} (pattern /{pattern}/)")


def _expect_word(errors, label, text, pattern, expected):
    word = NUMBER_WORDS.get(expected)
    if word is None:
        errors.append(f"{label}: no number word for {expected} - extend NUMBER_WORDS")
        return
    for m in re.findall(pattern, text):
        if m != word:
            errors.append(f"{label}: says '{m}', source says {expected} ('{word}')")


def on_config(config):
    errors = []

    tools = _tool_total()
    integrity = _integrity_total()
    if integrity != tools:
        errors.append(f"integrity manifest has {integrity} entries, tool catalog has {tools}")

    tools_page = _read(DOCS / "features/default-tools/index.md")
    _expect_numbers(errors, "default-tools/index.md description", tools_page,
                    r"Default Tools - (\d+) ready-to-call", tools)
    _expect_numbers(errors, "default-tools/index.md heading", tools_page,
                    r"Browse all (\d+) tools", tools)
    _expect_numbers(errors, "default-tools/index.md counter", tools_page,
                    r"Showing (\d+) of (\d+) tools", tools)
    cards = len(re.findall(r"tcg-card--directory", tools_page))
    if cards != tools:
        errors.append(f"default-tools/index.md directory has {cards} cards, tool catalog has {tools}")

    remote, stdio = _mcp_totals()
    mcp_total = remote + stdio
    home = _read(DOCS / "index.md")
    _expect_numbers(errors, "docs/index.md preset MCP count", home,
                    r"(\d+) preset MCP", mcp_total)

    page_cards = {}
    for label, page_name in MCP_GROUP_PAGES.items():
        page = _read(DOCS / "features/default-mcp-catalog" / page_name)
        page_cards[label] = len(re.findall(r"tcg-card--clickable", page))
    if sum(page_cards.values()) != mcp_total:
        errors.append(f"default-mcp-catalog pages have {sum(page_cards.values())} cards, catalog has {mcp_total} (remote {remote} + stdio {stdio})")
    split_pattern = r"[^\n]*?".join(re.escape(label) + r" \((\d+)\)" for label in MCP_GROUP_PAGES)
    split = re.search(split_pattern, home)
    if split is None:
        errors.append("docs/index.md: MCP category split line not found - update count_guard.py if the wording changed")
    else:
        for claimed, (label, page_name) in zip(split.groups(), MCP_GROUP_PAGES.items()):
            if int(claimed) != page_cards[label]:
                errors.append(f"docs/index.md: '{label}' says {claimed}, {page_name} has {page_cards[label]} cards")

    dashboards = _dashboard_total()
    _expect_word(errors, "docs/index.md dashboards", home,
                 r"\b(ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen)\b(?=[^.\n]{0,60}(?:dashboards|panels))", dashboards)
    obs_arch = _read(DOCS / "observability-architecture.md")
    _expect_word(errors, "observability-architecture.md dashboards", obs_arch,
                 r"\b(ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen)\b(?=[^.\n]{0,60}dashboards)", dashboards)

    tutorials = _tutorial_total()
    tutorials_index = _read(DOCS / "tutorials/index.md")
    _expect_word(errors, "tutorials/index.md", tutorials_index,
                 r"These (\w+) tutorials", tutorials)

    if errors:
        raise PluginError("count_guard: documentation counts drifted from the catalogs:\n  - " + "\n  - ".join(errors))
    return config
