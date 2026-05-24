description: Default Tools — Filesystem reference. 10 safety.fs-wrapped tools (read · list · stat · grep · slice · sort · cut · find · write) rooted at the FS base.

# Default Tools — Filesystem

The 10 tools in `default-tool-specs-builtin-fs.json` are the `safety.fs` surface as ready-to-call tools — a small shell-style filesystem pipeline covering read, list, stat, grep, slice, sort, cut, find, and write. **All paths are resolved against the per-app base path** (`TOOL_STUDIO_FS_BASE`, default `${user.home}/spring-ai-playground/fs-tool-workspace`); any path whose `normalize()` lands outside the base is rejected before any I/O.

Because they ride on `java.nio.file.Path` / `Files`, separator handling (`/` vs `\`), case folding, and symlink semantics are normalised at the JVM layer — these tools behave identically on macOS, Windows, and Linux. See [Tool Studio: Cross-platform by design](../tool-studio/index.md#cross-platform-by-design) for the mechanics, and [Tool Studio: Filesystem mode](../tool-studio/index.md#filesystem-mode) for the read-only / read-write sandbox split.

## The 10 filesystem tools { #the-filesystem-tools }

<div class="tcg-grid" markdown>

<div class="tcg-card tcg-card--clickable" id="readTextFile" data-tool-id="readTextFile" data-tool-title="readTextFile" markdown>
<div class="tcg-name"><span class="tcg-name__text">readTextFile</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-file-document-outline:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Reads a UTF-8 text file from disk and returns its contents as a single string.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `path`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

All paths are resolved relative to the playground's configured filesystem base path; anything outside it is rejected. Uses safety.fs.readText().

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `path` | `STRING` | ✓ | Relative path inside the FS base directory |

**Sandbox** — Sandbox needs **`fileRead`** (L3). Paths resolve against `TOOL_STUDIO_FS_BASE` (defaults to `${user.home}/spring-ai-playground/fs-tool-workspace`).

**JS source**

```javascript
/**
 * Reads a UTF-8 text file inside the playground's FS base directory.
 *
 * Path is resolved RELATIVE to the base path (which is set via
 * `spring.ai.playground.tool-studio.fs.base-path` / TOOL_STUDIO_FS_BASE
 * env, defaulting to the user's home directory). Anything attempting
 * to escape the base (e.g. `../`) is rejected by safety.fs.
 *
 * Uses host helper: safety.fs.readText.
 */

if (path == null || path === '') throw new Error('path required');
return safety.fs.readText(path);

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="listDir" data-tool-id="listDir" data-tool-title="listDir" markdown>
<div class="tcg-name"><span class="tcg-name__text">listDir</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-folder-outline:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Lists the immediate entries (files and subdirectories) of a directory under the FS base path. Returns an array of relative names (not full paths). Uses safety.fs.list().
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `dir`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `dir` | `STRING` |  | Relative directory path (default '.') |

**Sandbox** — Sandbox needs **`fileRead`** (L3). Paths resolve against `TOOL_STUDIO_FS_BASE` (defaults to `${user.home}/spring-ai-playground/fs-tool-workspace`).

**JS source**

```javascript
/**
 * Lists immediate entries (files + directories) of a directory.
 *
 * Returned as an array of leaf names — no recursion, no full paths.
 * Use `findFiles` for recursive globbing.
 *
 * Uses host helper: safety.fs.list.
 */

const target = (dir == null || dir === '') ? '.' : dir;
return safety.fs.list(target);

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="statFile" data-tool-id="statFile" data-tool-title="statFile" markdown>
<div class="tcg-name"><span class="tcg-name__text">statFile</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-information-outline:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Returns size, last-modified timestamp, and a directory flag for a path inside the FS base. Uses safety.fs.stat().
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `path`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `path` | `STRING` | ✓ | Relative path inside the FS base directory |

**Sandbox** — Sandbox needs **`fileRead`** (L3). Paths resolve against `TOOL_STUDIO_FS_BASE` (defaults to `${user.home}/spring-ai-playground/fs-tool-workspace`).

**JS source**

```javascript
/**
 * Returns { size, mtime, directory } for a path inside the FS base.
 *
 * - size       — file size in bytes (0 for directories).
 * - mtime      — ISO timestamp of last modification.
 * - directory  — true if the path is a directory.
 *
 * Uses host helper: safety.fs.stat.
 */

if (path == null || path === '') throw new Error('path required');
return safety.fs.stat(path);

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="lineCount" data-tool-id="lineCount" data-tool-title="lineCount" markdown>
<div class="tcg-name"><span class="tcg-name__text">lineCount</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-counter:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Counts the lines in a UTF-8 text file. Uses safety.fs.lineCount().
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `path`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `path` | `STRING` | ✓ | Relative path to the file |

**Sandbox** — Sandbox needs **`fileRead`** (L3). Paths resolve against `TOOL_STUDIO_FS_BASE` (defaults to `${user.home}/spring-ai-playground/fs-tool-workspace`).

**JS source**

```javascript
/**
 * Counts lines in a UTF-8 text file inside the FS base.
 *
 * Uses host helper: safety.fs.lineCount.
 */

if (path == null || path === '') throw new Error('path required');
return safety.fs.lineCount(path);

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="sliceFile" data-tool-id="sliceFile" data-tool-title="sliceFile" markdown>
<div class="tcg-name"><span class="tcg-name__text">sliceFile</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-content-cut:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Returns a slice of lines from a UTF-8 text file (head / tail / range). `start` is 0-based inclusive, `end` is 0-based exclusive (Python-style slice). Negative values count from the end of the file. Uses safety.fs.slice().
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `path` · `start` · `end`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `path` | `STRING` | ✓ | Relative path to the file |
| `start` | `INTEGER` |  | First line index (0-based inclusive; negatives from end) |
| `end` | `INTEGER` |  | End line index (0-based exclusive; negatives from end) |

**Sandbox** — Sandbox needs **`fileRead`** (L3). Paths resolve against `TOOL_STUDIO_FS_BASE` (defaults to `${user.home}/spring-ai-playground/fs-tool-workspace`).

**JS source**

```javascript
/**
 * Returns lines [start, end) from a UTF-8 text file (Python-slice semantics).
 *
 * - `start` is 0-based and inclusive.
 * - `end`   is 0-based and exclusive.
 * - Negative values count from the end: -1 == size - 1.
 * - Missing `start` / `end` defaults to the file's full range.
 *
 * Uses host helper: safety.fs.slice.
 */

if (path == null || path === '') throw new Error('path required');
return safety.fs.slice(path, start, end);

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="sortFile" data-tool-id="sortFile" data-tool-title="sortFile" markdown>
<div class="tcg-name"><span class="tcg-name__text">sortFile</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-sort:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Sorts the lines of a UTF-8 text file and returns the sorted lines as an array. Options: reverse / numeric / caseInsensitive / unique. Uses safety.fs.sort().
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `path` · `reverse` · `numeric` · `caseInsensitive` · `unique`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `path` | `STRING` | ✓ | Relative path to the file |
| `reverse` | `BOOLEAN` |  | Sort descending |
| `numeric` | `BOOLEAN` |  | Sort lines numerically |
| `caseInsensitive` | `BOOLEAN` |  | Ignore case when comparing |
| `unique` | `BOOLEAN` |  | Drop duplicate lines |

**Sandbox** — Sandbox needs **`fileRead`** (L3). Paths resolve against `TOOL_STUDIO_FS_BASE` (defaults to `${user.home}/spring-ai-playground/fs-tool-workspace`).

**JS source**

```javascript
/**
 * Sorts a file's lines and returns them as an array.
 *
 * Options:
 *   reverse           — descending order
 *   numeric           — numeric comparison (otherwise lexical)
 *   caseInsensitive   — compare lowercased
 *   unique            — drop duplicates
 *
 * Uses host helper: safety.fs.sort.
 */

if (path == null || path === '') throw new Error('path required');
return safety.fs.sort(path, {
  reverse: !!reverse,
  numeric: !!numeric,
  caseInsensitive: !!caseInsensitive,
  unique: !!unique,
});

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="grepFile" data-tool-id="grepFile" data-tool-title="grepFile" markdown>
<div class="tcg-name"><span class="tcg-name__text">grepFile</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-file-find-outline:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Searches a UTF-8 text file for lines matching a JavaScript regex. Returns an array of matching lines (optionally numbered). Uses safety.fs.grep().
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `pattern` · `path` · `caseInsensitive` · `numbered` · `limit`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `pattern` | `STRING` | ✓ | Regex pattern (JavaScript flavour) |
| `path` | `STRING` | ✓ | Relative path to the file |
| `caseInsensitive` | `BOOLEAN` |  | Match case-insensitively |
| `numbered` | `BOOLEAN` |  | Prefix each result with 'N:' (1-based line number) |
| `limit` | `INTEGER` |  | Max matches to return (0 = no limit) |

**Sandbox** — Sandbox needs **`fileRead`** (L3). Paths resolve against `TOOL_STUDIO_FS_BASE` (defaults to `${user.home}/spring-ai-playground/fs-tool-workspace`).

**JS source**

```javascript
/**
 * Greps a file's lines against a regex and returns the hits.
 *
 * Result is an array of matching lines. With `numbered=true` each entry
 * is prefixed by its 1-based line number, e.g. "42: TODO fix this".
 *
 * Uses host helper: safety.fs.grep.
 */

if (pattern == null || pattern === '') throw new Error('pattern required');
if (path == null || path === '')       throw new Error('path required');
return safety.fs.grep(pattern, path, {
  caseInsensitive: !!caseInsensitive,
  numbered: !!numbered,
  limit: Number.isInteger(limit) ? limit : 0,
});

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="findFiles" data-tool-id="findFiles" data-tool-title="findFiles" markdown>
<div class="tcg-name"><span class="tcg-name__text">findFiles</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-folder-search-outline:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Recursively finds files matching a glob inside a directory. Glob supports `*` and `?`. Optional max recursion depth and type filter ('file' or 'dir'). Uses safety.fs.find().
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `dir` · `glob` · `maxDepth` · `type`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `dir` | `STRING` |  | Relative directory to search from (default '.') |
| `glob` | `STRING` |  | Glob pattern (default '*') |
| `maxDepth` | `INTEGER` |  | Max recursion depth (0 = unlimited) |
| `type` | `STRING` |  | 'file' \| 'dir' \| omit for both |

**Sandbox** — Sandbox needs **`fileRead`** (L3). Paths resolve against `TOOL_STUDIO_FS_BASE` (defaults to `${user.home}/spring-ai-playground/fs-tool-workspace`).

**JS source**

```javascript
/**
 * Recursively finds entries matching a glob inside a directory.
 *
 * - Glob accepts `*` and `?` wildcards (POSIX glob, NOT regex).
 * - maxDepth limits recursion; 0 means unlimited.
 * - type='file' / 'dir' filters; omit to return both.
 *
 * Uses host helper: safety.fs.find.
 */

const target = (dir == null || dir === '') ? '.' : dir;
const pattern = (glob == null || glob === '') ? '*' : glob;
return safety.fs.find(target, pattern, {
  maxDepth: Number.isInteger(maxDepth) ? maxDepth : 0,
  type: (type === 'file' || type === 'dir') ? type : null,
});

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="cutFileFields" data-tool-id="cutFileFields" data-tool-title="cutFileFields" markdown>
<div class="tcg-name"><span class="tcg-name__text">cutFileFields</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-table-column:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Extracts selected fields from each line of a delimited file (CSV/TSV/etc.). Uses safety.fs.cut(). 1-based field numbers, comma-separated alternatives via the array.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `path` · `fields` · `delimiter` · `regex`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `path` | `STRING` | ✓ | Relative path to the file |
| `fields` | `ARRAY` | ✓ | Array of 1-based field indices to keep, e.g. [1, 3] |
| `delimiter` | `STRING` |  | Field delimiter character or regex (default '\t' tab) |
| `regex` | `BOOLEAN` |  | Treat `delimiter` as a regex pattern instead of literal |

**Sandbox** — Sandbox needs **`fileRead`** (L3). Paths resolve against `TOOL_STUDIO_FS_BASE` (defaults to `${user.home}/spring-ai-playground/fs-tool-workspace`).

**JS source**

```javascript
/**
 * Extracts selected fields from each line of a delimited file.
 *
 * - `fields`     — 1-based field indices to keep (array). [1, 3] picks columns 1 and 3.
 * - `delimiter`  — single character (literal) OR a regex pattern when `regex=true`.
 *                  Defaults to tab (\t).
 *
 * Uses host helper: safety.fs.cut. Each returned row is a delimiter-joined string.
 */

if (path == null || path === '') throw new Error('path required');
if (fields == null) throw new Error('fields required');
const fieldArr = [];
for (const f of fields) fieldArr.push(Number(f));

return safety.fs.cut(path, {
  fields:    fieldArr,
  delimiter: (typeof delimiter === 'string' && delimiter.length > 0) ? delimiter : null,
  regex:     !!regex,
});

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="writeTextFile" data-tool-id="writeTextFile" data-tool-title="writeTextFile" markdown>
<div class="tcg-name"><span class="tcg-name__text">writeTextFile</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-file-edit-outline:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l4">L4</span></div>
<div class="tcg-body" markdown>
Writes a UTF-8 text file inside the FS base path (creating parent directories as needed). Overwrites any existing file. Requires `fileWrite` permission on the sandbox.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `path` · `content`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `path` | `STRING` | ✓ | Relative path to write |
| `content` | `STRING` | ✓ | Full text content to write (UTF-8) |

**Sandbox** — Sandbox needs **`fileWrite`** (L4). Paths resolve against `TOOL_STUDIO_FS_BASE`; the helper auto-creates parent directories.

**JS source**

```javascript
/**
 * Writes a UTF-8 text file inside the FS base directory.
 *
 * - Overwrites any existing file at `path`.
 * - Path is resolved RELATIVE to the FS base; escape attempts are rejected.
 * - This tool needs the `fileWrite` sandbox permission (set on the spec).
 *
 * Uses host helper: safety.fs.writeText.
 */

if (path == null || path === '') throw new Error('path required');
if (content == null)             throw new Error('content required');
safety.fs.writeText(path, String(content));
return { ok: true, path, bytes: new TextEncoder().encode(String(content)).length };

```

</div>
</div>

</div>

## Composition patterns (shell-style filesystem chains)

These ten tools mirror the standard Unix-shell pipeline shape, but every step is a JSON-returning function so the agent can reason between calls:

- **Read → filter → trim → save** — `listDir(dir)` → `grepFile(pattern, path)` → `sliceFile(path, start, end)` → `writeTextFile(outPath, content)`. The canonical "summarise recent errors from a log directory" flow.
- **Find → cut → ETL** — `findFiles(dir, glob='*.csv')` → loop with `cutFileFields(path, fields=[1,3])` to project a directory of CSVs into one structured dataset.
- **Sort dedupe → count** — `sortFile(path, numeric=true, unique=true)` → `lineCount(path)` to deduplicate a numeric stream in place and report the resulting size.
- **Stat-first guard** — `statFile(path)` → branch on `size` / `lastModified` → only run the rest of the pipeline if the file changed since the last run.

[Tutorial 8: Default Tool Recipes](../../tutorials/8-default-tool-recipes.md) walks the **Read → filter → trim → save** chain end-to-end as `summariseRecentLogs`.

## Keys & secrets

One configuration value, no real secrets.

| Variable | What it does | Default | Where to set |
|---|---|---|---|
| `TOOL_STUDIO_FS_BASE` | Per-app `safety.fs` base path — every path resolved against it; `normalize()` rejects any escape. | `${user.home}/spring-ai-playground/fs-tool-workspace` | Launcher **Environment Variables** card, or `export TOOL_STUDIO_FS_BASE=/path` before launch |

The `File Toolkit` preset opts every read tool into `fileRead` automatically; `writeTextFile` requires `fileWrite` (L4) which you enable per-tool in the **Sandbox & Capabilities** pane — see [Tool Studio: Filesystem mode](../tool-studio/index.md#filesystem-mode).

→ [Tool Studio: Filesystem mode](../tool-studio/index.md#filesystem-mode) — `fileRead` / `fileWrite` semantics and base-path enforcement.
