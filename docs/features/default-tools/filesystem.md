description: Default Tools - Filesystem reference. 18 safety.fs-wrapped tools (list-roots · read · list · stat · grep · search · count · slice · sort · cut · find · write · append · edit · copy · move · delete · deleteDir) over readable roots and a writable working directory.

# Default Tools - Filesystem

The 18 tools in `default-tool-specs-builtin-fs.json` are the `safety.fs` surface as ready-to-call tools - a shell-style filesystem pipeline covering list-roots, read, list, stat, grep, recursive content search, count, slice, sort, cut, find, write, append, targeted edit, copy, move, and delete (file and directory). They operate within two boundaries:

- **Readable roots** - directories the tools may **read** from, recursively. The user's home directory is a readable root by default.
- A **working directory** (`spring.ai.playground.tool-studio.fs.base-path`, default `${user.home}/spring-ai-playground/workspace`) - the only **writable** location, and where relative paths resolve. When a tool runs inside a chat, writes are confined to a **per-conversation subdirectory** of it (`<workspace>/<conversationId>/`), created on first write and removed when you delete the conversation; `listAllowedDirectories` reports that exact path at runtime. This holds however the chat reaches the tool - a fixed selection travels through the built-in MCP server's loopback connection, which carries the conversation identity along with the call. A caller with **no** conversation - an external MCP client invoking the same exposed tools - writes to the working directory root instead.

So a relative path resolves under the working directory; an absolute path may be **read** anywhere under a readable root, but **writes** are confined to the working directory. Call **`listAllowedDirectories`** first to learn the exact absolute paths before reading or writing.

**Three capability tiers.** Read tools (`listDir`, `readTextFile`, `grepFile`, `searchInFiles`, ...) need only `fileRead` and rank **L3**. Write tools that create or amend a file (`writeTextFile`, `appendTextFile`, `editTextFile`, `copyFile`) need `fileWrite` and rank **L4**. Tools that remove or relocate data (`moveFile`, `deleteFile`, `deleteDir`) carry a `destructive` flag and rank **L5** - deliberately above a plain overwrite, because the loss is not recoverable from the file itself. Every write and destructive tool is gated by [human-in-the-loop approval](../../hitl-architecture.md), so its risk chip shows two levels with an arrow (`L5 → L4`, `L4 → L3`): the level **before** the arrow is the inherent posture that still drives sandbox permissions and the audit log, and the level **after** is the effective posture once a mandatory approval is credited (one band lower). A poisoned tool description forfeits that credit, and the runtime denies the call outright if approval is unavailable, so a credited mitigation can never silently no-op.

Because they ride on `java.nio.file.Path` / `Files`, separator handling (`/` vs `\`), case folding, and symlink semantics are normalised at the JVM layer - these tools behave identically on macOS, Windows, and Linux. See [Tool Studio: Cross-platform by design](../tool-studio/index.md#cross-platform-by-design) for the mechanics, and [Tool Studio: Filesystem mode](../tool-studio/index.md#filesystem-mode) for the read-only / read-write sandbox split.

## The 18 filesystem tools { #the-filesystem-tools }

<div class="tcg-grid" markdown>

<div class="tcg-card tcg-card--clickable" id="listAllowedDirectories" data-tool-id="listAllowedDirectories" data-tool-title="listAllowedDirectories" markdown>
<div class="tcg-name"><span class="tcg-name__text">listAllowedDirectories</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-folder-key-outline:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Reports the filesystem boundaries these tools operate within: the readable roots (anything under them can be read) and the working directory (the only writable location; a per-conversation subdirectory when run from a chat). Call this first. Uses safety.fs.readRoots() / workspace().
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; -</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Reports which directories the filesystem tools may touch: the readable roots (your home directory and the app's spring-ai-playground folder - anything under them can be read) and the single working directory for this chat (the only writable location, where relative paths resolve). Files the user uploads are saved under the working directory's `uploads/` subfolder, so after calling this you can `listDir` the working directory or its `uploads/` folder to see the files already there, then `readTextFile` one to re-process it. Call this first to learn the absolute paths. Takes no arguments. Uses safety.fs.readRoots() / workspace().

**Parameters**

*(none - takes no arguments)*

**Sandbox** - Sandbox needs **`fileRead`** (L3). Reports the boundaries only; it reads no file contents.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Reports the filesystem boundaries these tools operate within.
 *
 * - readRoots         — absolute roots that may be READ (recursively).
 * - workingDirectory  — the single writable dir; relative paths resolve here.
 *
 * Uses host helpers: safety.fs.readRoots, safety.fs.workspace.
 */

return {
  readRoots: Array.from(safety.fs.readRoots()),
  workingDirectory: safety.fs.workspace(),
};

```

</details>

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="readTextFile" data-tool-id="readTextFile" data-tool-title="readTextFile" markdown>
<div class="tcg-name"><span class="tcg-name__text">readTextFile</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-file-document-outline:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Reads a UTF-8 text file from disk and returns its contents as a single string.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `path`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

A relative path resolves under the working directory; an absolute path is allowed anywhere under a readable root (the user's home directory by default). Call `listAllowedDirectories` first to learn the working directory and readable roots. Uses safety.fs.readText().

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `path` | `STRING` | ✓ | Path to read - relative (under the working directory) or absolute (under a readable root) |

**Sandbox** - Sandbox needs **`fileRead`** (L3). A relative path resolves under the working directory; an absolute path may sit anywhere under a readable root (`${user.home}` by default). Call `listAllowedDirectories` to see both.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Reads a UTF-8 text file inside the playground's FS base directory.
 *
 * Path is resolved RELATIVE to the base path (which is set via
 * `spring.ai.playground.tool-studio.fs.base-path`,
 * defaulting to the user's home directory). Anything attempting
 * to escape the base (e.g. `../`) is rejected by safety.fs.
 *
 * Uses host helper: safety.fs.readText.
 */

if (path == null || path === '') throw new Error('path required');
return safety.fs.readText(path);

```

</details>

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="listDir" data-tool-id="listDir" data-tool-title="listDir" markdown>
<div class="tcg-name"><span class="tcg-name__text">listDir</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-folder-outline:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Lists the immediate entries (files and subdirectories) of a directory. Entries in the working directory come back as relative names; entries elsewhere under a readable root come back as absolute paths. Uses safety.fs.list().
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `dir`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `dir` | `STRING` |  | Directory to list - relative (default '.', under the working directory) or absolute (under a readable root) |

**Sandbox** - Sandbox needs **`fileRead`** (L3). A relative path resolves under the working directory; an absolute path may sit anywhere under a readable root (`${user.home}` by default). Call `listAllowedDirectories` to see both.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Lists immediate entries (files + directories) of a directory.
 *
 * Returned as an array of leaf names - no recursion, no full paths.
 * Use `findFiles` for recursive globbing.
 *
 * Uses host helper: safety.fs.list.
 */

const target = (dir == null || dir === '') ? '.' : dir;
return safety.fs.list(target);

```

</details>

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="statFile" data-tool-id="statFile" data-tool-title="statFile" markdown>
<div class="tcg-name"><span class="tcg-name__text">statFile</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-information-outline:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Returns size, last-modified timestamp, and a directory flag for a path (relative under the working directory, or absolute under a readable root). Uses safety.fs.stat().
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `path`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `path` | `STRING` | ✓ | Path to read - relative (under the working directory) or absolute (under a readable root) |

**Sandbox** - Sandbox needs **`fileRead`** (L3). A relative path resolves under the working directory; an absolute path may sit anywhere under a readable root (`${user.home}` by default). Call `listAllowedDirectories` to see both.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Returns { size, mtime, directory } for a path inside the FS base.
 *
 * - size       - file size in bytes (0 for directories).
 * - mtime      - ISO timestamp of last modification.
 * - directory  - true if the path is a directory.
 *
 * Uses host helper: safety.fs.stat.
 */

if (path == null || path === '') throw new Error('path required');
return safety.fs.stat(path);

```

</details>

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
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `path` | `STRING` | ✓ | Path to the file - relative (under the working directory) or absolute (under a readable root) |

**Sandbox** - Sandbox needs **`fileRead`** (L3). A relative path resolves under the working directory; an absolute path may sit anywhere under a readable root (`${user.home}` by default). Call `listAllowedDirectories` to see both.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Counts lines in a UTF-8 text file inside the FS base.
 *
 * Uses host helper: safety.fs.lineCount.
 */

if (path == null || path === '') throw new Error('path required');
return safety.fs.lineCount(path);

```

</details>

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
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `path` | `STRING` | ✓ | Path to the file - relative (under the working directory) or absolute (under a readable root) |
| `start` | `INTEGER` |  | First line index (0-based inclusive; negatives from end) |
| `end` | `INTEGER` |  | End line index (0-based exclusive; negatives from end) |

**Sandbox** - Sandbox needs **`fileRead`** (L3). A relative path resolves under the working directory; an absolute path may sit anywhere under a readable root (`${user.home}` by default). Call `listAllowedDirectories` to see both.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

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

</details>

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
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `path` | `STRING` | ✓ | Path to the file - relative (under the working directory) or absolute (under a readable root) |
| `reverse` | `BOOLEAN` |  | Sort descending |
| `numeric` | `BOOLEAN` |  | Sort lines numerically |
| `caseInsensitive` | `BOOLEAN` |  | Ignore case when comparing |
| `unique` | `BOOLEAN` |  | Drop duplicate lines |

**Sandbox** - Sandbox needs **`fileRead`** (L3). A relative path resolves under the working directory; an absolute path may sit anywhere under a readable root (`${user.home}` by default). Call `listAllowedDirectories` to see both.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Sorts a file's lines and returns them as an array.
 *
 * Options:
 *   reverse           - descending order
 *   numeric           - numeric comparison (otherwise lexical)
 *   caseInsensitive   - compare lowercased
 *   unique            - drop duplicates
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

</details>

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
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `pattern` | `STRING` | ✓ | Regex pattern (JavaScript flavour) |
| `path` | `STRING` | ✓ | Path to the file - relative (under the working directory) or absolute (under a readable root) |
| `caseInsensitive` | `BOOLEAN` |  | Match case-insensitively |
| `numbered` | `BOOLEAN` |  | Prefix each result with 'N:' (1-based line number) |
| `limit` | `INTEGER` |  | Max matches to return (0 = no limit) |

**Sandbox** - Sandbox needs **`fileRead`** (L3). A relative path resolves under the working directory; an absolute path may sit anywhere under a readable root (`${user.home}` by default). Call `listAllowedDirectories` to see both.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

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

</details>

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
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `dir` | `STRING` |  | Directory to search from - relative (default '.') or absolute (under a readable root) |
| `glob` | `STRING` |  | Glob pattern (default '*') |
| `maxDepth` | `INTEGER` |  | Max recursion depth (0 = unlimited) |
| `type` | `STRING` |  | 'file' \| 'dir' \| omit for both |

**Sandbox** - Sandbox needs **`fileRead`** (L3). A relative path resolves under the working directory; an absolute path may sit anywhere under a readable root (`${user.home}` by default). Call `listAllowedDirectories` to see both.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

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

</details>

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
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `path` | `STRING` | ✓ | Path to the file - relative (under the working directory) or absolute (under a readable root) |
| `fields` | `ARRAY` | ✓ | Array of 1-based field indices to keep, e.g. [1, 3] |
| `delimiter` | `STRING` |  | Field delimiter character or regex (default '\t' tab) |
| `regex` | `BOOLEAN` |  | Treat `delimiter` as a regex pattern instead of literal |

**Sandbox** - Sandbox needs **`fileRead`** (L3). A relative path resolves under the working directory; an absolute path may sit anywhere under a readable root (`${user.home}` by default). Call `listAllowedDirectories` to see both.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Extracts selected fields from each line of a delimited file.
 *
 * - `fields`     - 1-based field indices to keep (array). [1, 3] picks columns 1 and 3.
 * - `delimiter`  - single character (literal) OR a regex pattern when `regex=true`.
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

</details>

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="writeTextFile" data-tool-id="writeTextFile" data-tool-title="writeTextFile" markdown>
<div class="tcg-name"><span class="tcg-name__text">writeTextFile</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-file-edit-outline:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l4">L4</span></div>
<div class="tcg-body" markdown>
Writes a UTF-8 text file inside the working directory (creating parent directories as needed). Overwrites any existing file. Requires `fileWrite` permission on the sandbox.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `path` · `content`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `path` | `STRING` | ✓ | Path to write, inside the working directory |
| `content` | `STRING` | ✓ | Full text content to write (UTF-8) |

**Sandbox** - Sandbox needs **`fileWrite`** (L4). `spring.ai.playground.tool-studio.fs.base-path` (default `${user.home}/spring-ai-playground/workspace`) is the workspace root; writes from a chat are confined to a per-conversation subdirectory under it, and the returned `path` is the absolute location actually written. The helper auto-creates parent directories.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Writes a UTF-8 text file inside the workspace directory.
 *
 * - Overwrites any existing file at `path`.
 * - Path is resolved RELATIVE to the workspace; escape attempts are rejected.
 * - This tool needs the `fileWrite` sandbox permission (set on the spec).
 *
 * Uses host helper: safety.fs.writeText.
 */

if (path == null || path === '') throw new Error('path required');
if (content == null)             throw new Error('content required');
const writtenPath = safety.fs.writeText(path, String(content));
return { ok: true, path: writtenPath, bytes: new TextEncoder().encode(String(content)).length };

```

</details>

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="searchInFiles" data-tool-id="searchInFiles" data-tool-title="searchInFiles" markdown>
<div class="tcg-name"><span class="tcg-name__text">searchInFiles</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-text-search:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Recursively searches file **contents** across a directory tree for lines matching a regex, returning each hit as `{ file, line, text }`. Where `grepFile` scans one file, this opens every file under `dir` whose name matches `glob`. Uses safety.fs.searchInFiles().
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `pattern` · `dir` · `glob` · `caseInsensitive` · `maxDepth` · `limit`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Answers "which files mention X" or "where is Y used" in one call. Binary / non-UTF-8 files are skipped, and `limit` caps the total number of hits. A relative `dir` resolves under the working directory; an absolute `dir` must sit under a readable root. Uses safety.fs.searchInFiles().

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `pattern` | `STRING` | ✓ | Regex pattern (JavaScript flavour) matched against each line |
| `dir` | `STRING` |  | Directory to search from (default '.', under the working directory; absolute under a readable root) |
| `glob` | `STRING` |  | Filename glob limiting which files are scanned (default '*') |
| `caseInsensitive` | `BOOLEAN` |  | Match case-insensitively |
| `maxDepth` | `INTEGER` |  | Max recursion depth (0 = unlimited) |
| `limit` | `INTEGER` |  | Max total matches to return (0 = default cap) |

**Sandbox** - Sandbox needs **`fileRead`** (L3). Read-only, no approval required. A relative path resolves under the working directory; an absolute path may sit anywhere under a readable root.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
if (pattern == null || pattern === '') throw new Error('pattern required');
const target = (dir == null || dir === '') ? '.' : dir;
const g = (glob == null || glob === '') ? '*' : glob;
return safety.fs.searchInFiles(target, g, pattern, {
  caseInsensitive: !!caseInsensitive,
  maxDepth: Number.isInteger(maxDepth) ? maxDepth : 0,
  limit: Number.isInteger(limit) ? limit : 0,
});
```

</details>

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="appendTextFile" data-tool-id="appendTextFile" data-tool-title="appendTextFile" markdown>
<div class="tcg-name"><span class="tcg-name__text">appendTextFile</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-file-plus-outline:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l4">L4</span></div>
<div class="tcg-body" markdown>
Appends UTF-8 text to the **end** of a file, creating it if absent. Unlike `writeTextFile` (which overwrites), this adds after the existing bytes - ideal for logs and incremental output. Uses safety.fs.appendText().
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `path` · `content`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `path` | `STRING` | ✓ | File to append to, inside the working directory |
| `content` | `STRING` | ✓ | UTF-8 text appended verbatim after the existing content |

**Sandbox** - Sandbox needs **`fileWrite`** (inherent L4). Gated by human-in-the-loop approval, so the effective chip reads `L4 → L3`. Writes are confined to the working directory; escape attempts are rejected.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
if (path == null || path === '') throw new Error('path required');
if (content == null)             throw new Error('content required');
const writtenPath = safety.fs.appendText(path, String(content));
return { ok: true, path: writtenPath, bytesAppended: new TextEncoder().encode(String(content)).length };
```

</details>

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="editTextFile" data-tool-id="editTextFile" data-tool-title="editTextFile" markdown>
<div class="tcg-name"><span class="tcg-name__text">editTextFile</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-file-replace-outline:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l4">L4</span></div>
<div class="tcg-body" markdown>
Makes a **targeted** edit by replacing an exact substring, without rewriting the whole file. By default `oldString` must occur exactly once (else rejected as ambiguous); `replaceAll` swaps every occurrence. Uses safety.fs.editText().
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `path` · `oldString` · `newString` · `replaceAll`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Use this instead of `writeTextFile` whenever you only need to change part of a file. Include enough surrounding context to make `oldString` unique, or set `replaceAll=true`. Returns `{ ok, path, replacements }`. Uses safety.fs.editText().

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `path` | `STRING` | ✓ | File to edit, inside the working directory |
| `oldString` | `STRING` | ✓ | Exact text to find (verbatim). Must be unique unless `replaceAll` is true |
| `newString` | `STRING` | ✓ | Replacement text (empty string deletes the match) |
| `replaceAll` | `BOOLEAN` |  | Replace every occurrence instead of requiring a single unique match |

**Sandbox** - Sandbox needs **`fileWrite`** (inherent L4). Gated by human-in-the-loop approval, so the effective chip reads `L4 → L3`. Reads then rewrites the target in place; confined to the working directory.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
if (path == null || path === '')           throw new Error('path required');
if (oldString == null || oldString === '') throw new Error('oldString required');
const result = safety.fs.editText(path, String(oldString), newString == null ? '' : String(newString), !!replaceAll);
return { ok: true, path: result.path, replacements: result.replacements };
```

</details>

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="copyFile" data-tool-id="copyFile" data-tool-title="copyFile" markdown>
<div class="tcg-name"><span class="tcg-name__text">copyFile</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-content-copy:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l4">L4</span></div>
<div class="tcg-body" markdown>
Copies a file to a new location. The source may be any **readable** file (a readable root, or an upload); the destination must be inside the working directory. The source is left intact. Uses safety.fs.copy().
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `from` · `to`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Parent directories are created automatically and an existing destination is overwritten. Useful for pulling a home-directory or uploaded file into the writable workspace before processing it. Uses safety.fs.copy().

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `from` | `STRING` | ✓ | Source file - relative (working directory) or absolute (under a readable root) |
| `to` | `STRING` | ✓ | Destination path, inside the working directory |

**Sandbox** - Sandbox needs **`fileWrite`** (inherent L4). Gated by human-in-the-loop approval, so the effective chip reads `L4 → L3`. The source is read from any readable root; only the destination write is confined to the working directory.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
if (from == null || from === '') throw new Error('from required');
if (to == null || to === '')     throw new Error('to required');
const path = safety.fs.copy(String(from), String(to));
return { ok: true, path };
```

</details>

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="moveFile" data-tool-id="moveFile" data-tool-title="moveFile" markdown>
<div class="tcg-name"><span class="tcg-name__text">moveFile</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-file-move-outline:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l5">L5</span></div>
<div class="tcg-body" markdown>
Moves or renames a file within the working directory. **Destructive**: the source no longer exists at its old path afterwards, and an existing destination is overwritten. Uses safety.fs.move().
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `from` · `to`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `from` | `STRING` | ✓ | Source file, inside the working directory |
| `to` | `STRING` | ✓ | Destination path (new name), inside the working directory |

**Sandbox** - Sandbox needs **`fileWrite`** and is flagged **`destructive`** (inherent L5). Gated by human-in-the-loop approval, so the effective chip reads `L5 → L4`. Both source and destination are confined to the working directory.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
if (from == null || from === '') throw new Error('from required');
if (to == null || to === '')     throw new Error('to required');
const path = safety.fs.move(String(from), String(to));
return { ok: true, path };
```

</details>

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="deleteFile" data-tool-id="deleteFile" data-tool-title="deleteFile" markdown>
<div class="tcg-name"><span class="tcg-name__text">deleteFile</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-file-remove-outline:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l5">L5</span></div>
<div class="tcg-body" markdown>
Permanently deletes a single file from the working directory. **Destructive** and irreversible. Directories are rejected - use `deleteDir`. Uses safety.fs.delete().
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `path`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `path` | `STRING` | ✓ | File to delete, inside the working directory |

**Sandbox** - Sandbox needs **`fileWrite`** and is flagged **`destructive`** (inherent L5). Gated by human-in-the-loop approval, so the effective chip reads `L5 → L4`. Returns `{ ok, deleted, path }` - `deleted` is false when the file did not exist.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
if (path == null || path === '') throw new Error('path required');
const deleted = safety.fs.delete(String(path));
return { ok: true, deleted, path: String(path) };
```

</details>

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="deleteDir" data-tool-id="deleteDir" data-tool-title="deleteDir" markdown>
<div class="tcg-name"><span class="tcg-name__text">deleteDir</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-folder-remove-outline:</div>
<div class="tcg-type">file · pipeline <span class="risk risk-l5">L5</span></div>
<div class="tcg-body" markdown>
Permanently deletes a directory **and all of its contents** (recursive) from the working directory. **Destructive** and irreversible. The workspace root itself and plain files are rejected. Uses safety.fs.deleteDir().
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `path`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `path` | `STRING` | ✓ | Directory to delete recursively, inside the working directory (cannot be the workspace root) |

**Sandbox** - Sandbox needs **`fileWrite`** and is flagged **`destructive`** (inherent L5). Gated by human-in-the-loop approval, so the effective chip reads `L5 → L4`. Returns `{ ok, removed, path }` - `removed` is the number of entries deleted.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
if (path == null || path === '') throw new Error('path required');
const removed = safety.fs.deleteDir(String(path));
return { ok: true, removed, path: String(path) };
```

</details>

</div>
</div>

</div>

## Composition patterns (shell-style filesystem chains)

These tools mirror the standard Unix-shell pipeline shape, but every step is a JSON-returning function so the agent can reason between calls:

- **Read → filter → trim → save** - `listDir(dir)` → `grepFile(pattern, path)` → `sliceFile(path, start, end)` → `writeTextFile(outPath, content)`. The canonical "summarise recent errors from a log directory" flow.
- **Find → cut → ETL** - `findFiles(dir, glob='*.csv')` → loop with `cutFileFields(path, fields=[1,3])` to project a directory of CSVs into one structured dataset.
- **Sort dedupe → count** - `sortFile(path, numeric=true, unique=true)` → `lineCount(path)` to deduplicate a numeric stream in place and report the resulting size.
- **Stat-first guard** - `statFile(path)` → branch on `size` / `lastModified` → only run the rest of the pipeline if the file changed since the last run.
- **Search → edit** - `searchInFiles(dir, glob, pattern)` finds every hit across the tree, then `editTextFile(path, oldString, newString)` applies a targeted, unique-match change to each file - the code-refactor loop.
- **Copy → transform → clean up** - `copyFile(upload, 'work.csv')` pulls an upload into the writable workspace; process it, then `deleteFile('work.csv')` or `deleteDir('tmp')` removes the scratch copy. Every write and destructive step pauses for approval first.

[Tutorial 8: Default Tool Recipes](../../tutorials/8-default-tool-recipes.md) walks the **Read → filter → trim → save** chain end-to-end as `summariseRecentLogs`.

## Keys & secrets

One configuration value, no real secrets.

| Variable | What it does | Default | Where to set |
|---|---|---|---|
| `SPRING_AI_PLAYGROUND_TOOL_STUDIO_FS_BASE_PATH` | The `safety.fs` **working directory** - the only writable location, and where relative paths resolve. Reads also reach the readable roots (the home directory by default). | `${user.home}/spring-ai-playground/workspace` | Launcher **Environment Variables** card, or `export SPRING_AI_PLAYGROUND_TOOL_STUDIO_FS_BASE_PATH=/path` before launch |

The `File Toolkit` preset opts every read tool (now including `searchInFiles`) into `fileRead` automatically. The write tools (`writeTextFile`, `appendTextFile`, `editTextFile`, `copyFile`) require `fileWrite` (L4), and the destructive tools (`moveFile`, `deleteFile`, `deleteDir`) additionally carry the `destructive` flag (L5); you enable these per-tool in the **Sandbox & Capabilities** pane - see [Tool Studio: Filesystem mode](../tool-studio/index.md#filesystem-mode). In Agentic Chat, the **[Workspace organizer](../agentic-chat/prompt-presets.md#workspace-organizer)** preset wires the full set - survey, restructure, clean up - with every mutating call gated by [human-in-the-loop](../human-in-the-loop.md) approval.

→ [Tool Studio: Filesystem mode](../tool-studio/index.md#filesystem-mode) - `fileRead` / `fileWrite` semantics and base-path enforcement.
