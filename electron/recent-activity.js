'use strict';

const fs = require('fs');
const path = require('path');

const STORE_LIMIT = 20;
const LABEL_MAX_LENGTH = 48;
const SKIP_PATH_PREFIXES = ['/oauth-complete'];
const ROUTE_LABELS = [
  ['/agentic-chat', 'Agentic Chat'],
  ['/mcp-server', 'MCP Server'],
  ['/vector-database', 'Vector Database'],
  ['/tool-studio', 'Tool Studio'],
  ['/observability', 'Observability'],
];

function toActivityPath(url, serverUrl) {
  if (!url || !serverUrl) return null;
  let target, base;
  try {
    target = new URL(url);
    base = new URL(serverUrl);
  } catch {
    return null;
  }
  if (target.origin !== base.origin) return null;
  if (SKIP_PATH_PREFIXES.some((prefix) => target.pathname.startsWith(prefix))) return null;
  return target.pathname + target.search;
}

function labelForEntry(entry) {
  let label = entry.title;
  if (!label) {
    const route = ROUTE_LABELS.find(([prefix]) => entry.path.startsWith(prefix));
    if (route) label = route[1];
    else if (entry.path === '/' || entry.path.startsWith('/?')) label = 'Home';
    else label = entry.path;
  }
  return label.length > LABEL_MAX_LENGTH ? `${label.slice(0, LABEL_MAX_LENGTH - 1)}…` : label;
}

function createRecentActivityStore({ filePath, limit = STORE_LIMIT, now = Date.now }) {
  let entries = load();

  function load() {
    try {
      const parsed = JSON.parse(fs.readFileSync(filePath, 'utf8'));
      const list = Array.isArray(parsed?.entries) ? parsed.entries : [];
      return list
        .filter((entry) => entry && typeof entry.path === 'string' && entry.path.startsWith('/'))
        .map((entry) => ({
          path: entry.path,
          title: typeof entry.title === 'string' ? entry.title.trim() : '',
          at: Number(entry.at) || 0,
        }))
        .slice(0, limit);
    } catch {
      return [];
    }
  }

  function save() {
    try {
      fs.mkdirSync(path.dirname(filePath), { recursive: true });
      fs.writeFileSync(filePath, JSON.stringify({ version: 1, entries }, null, 2));
    } catch {
    }
  }

  function record(activityPath, title) {
    if (typeof activityPath !== 'string' || !activityPath.startsWith('/')) return false;
    const cleanTitle = typeof title === 'string' ? title.trim() : '';
    const existing = entries.find((entry) => entry.path === activityPath);
    const nextTitle = cleanTitle || existing?.title || '';
    if (existing && entries[0] === existing && existing.title === nextTitle) return false;
    entries = [
      { path: activityPath, title: nextTitle, at: now() },
      ...entries.filter((entry) => entry.path !== activityPath),
    ].slice(0, limit);
    save();
    return true;
  }

  function list() {
    return entries.map((entry) => ({ ...entry }));
  }

  function clear() {
    entries = [];
    save();
  }

  return { record, list, clear };
}

module.exports = { createRecentActivityStore, toActivityPath, labelForEntry };
