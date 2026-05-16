Description: Default Tools — Utilities reference. 26 pure-compute tools — text, datetime, math, security, encoding, crypto, CSV. No network, no env vars, no filesystem.

# Default Tools — Utilities

The 26 tools in `default-tool-specs-builtin.json` (16) and `default-tool-specs-builtin-helpers.json` (10) cover text manipulation, date arithmetic, math, security scanners, encoding, cryptographic primitives, and CSV serialisation / parsing. They share one property — **no network, no filesystem, no env vars**. Everything runs in-memory at sandbox **L0** by default; the only outside helpers in play are `safety.parser.csv` for `formatCsv` / `parseCsv` and `crypto.subtle` for the crypto group — both still purely in-memory.

Because they ride on JVM stdlib — `java.security.MessageDigest`, `javax.crypto`, JCE, the JDK regex engine — every one of these runs identically on macOS, Windows, and Linux. See [Tool Studio: Cross-platform by design](../tool-studio.md#cross-platform-by-design) for the mechanics.

The 26 tools split by concern.

## Browse the 26 utilities { #browse-the-utilities }

Every tool below runs at sandbox **L0** with no env vars. Use the directory on the [Default Tools index](index.md#browse-all-tools) for cross-page search; this page is the full per-tool reference for the 26.

<div class="tcg-grid" markdown>

<div class="tcg-card tcg-card--clickable" id="timezoneConvert" data-tool-id="timezoneConvert" data-tool-title="timezoneConvert" markdown>
<div class="tcg-name"><span class="tcg-name__text">timezoneConvert</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-clock-alert-outline:</div>
<div class="tcg-type">datetime · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Converts a moment in time between IANA time zones. Returns the same instant rendered in the target zone (ISO with offset).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text` · `toTimeZone`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `text` | `STRING` | ✓ | ISO date/time text |
| `toTimeZone` | `STRING` | ✓ | Target IANA time zone |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Renders the same instant in a different IANA time zone, with offset.
 *
 * Output is ISO-8601 with explicit ±HH:MM offset (NOT 'Z'), so the
 * time zone is unambiguous in the rendered string.
 *
 * Pure ES2024 — Intl.DateTimeFormat for components + manual offset calc.
 */

if (text == null || text === '') throw new Error('text required');
if (toTimeZone == null || toTimeZone === '') throw new Error('toTimeZone required');
const d = new Date(String(text));
if (isNaN(d.getTime())) throw new Error('invalid date: ' + text);

const fmt = new Intl.DateTimeFormat('en-CA', {
  timeZone: toTimeZone,
  year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', second: '2-digit',
  hour12: false,
  fractionalSecondDigits: 3,
});

const parts = {};
for (const p of fmt.formatToParts(d)) parts[p.type] = p.value;

const hh = parts.hour === '24' ? '00' : parts.hour;
// Reconstruct the wall-clock instant in target zone, treat it as UTC,
// then derive the offset from the real UTC moment.
const wallUtc = Date.UTC(
  Number(parts.year), Number(parts.month) - 1, Number(parts.day),
  Number(hh), Number(parts.minute), Number(parts.second));
const offsetMin = Math.round((wallUtc - d.getTime()) / 60_000);

const sign = offsetMin >= 0 ? '+' : '-';
const aoh = Math.abs(offsetMin);
const oh = String(Math.floor(aoh / 60)).padStart(2, '0');
const om = String(aoh % 60).padStart(2, '0');
const frac = parts.fractionalSecond || '000';

return parts.year + '-' + parts.month + '-' + parts.day
     + 'T' + hh + ':' + parts.minute + ':' + parts.second + '.' + frac
     + sign + oh + ':' + om;

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="dateDiff" data-tool-id="dateDiff" data-tool-title="dateDiff" markdown>
<div class="tcg-name"><span class="tcg-name__text">dateDiff</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-calendar-arrow-right:</div>
<div class="tcg-type">datetime · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Computes b - a in the requested unit (days|hours|minutes|seconds|milliseconds). Returns a number which may be fractional.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `a` · `b` · `unit`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `a` | `STRING` | ✓ | Start ISO date/time |
| `b` | `STRING` | ✓ | End ISO date/time |
| `unit` | `STRING` |  | Unit of the result (default 'milliseconds') |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Computes `b - a` in the requested unit. Result is a (possibly fractional)
 * number — e.g. 5.5 days, 36.0 hours, 0.25 seconds.
 *
 * `unit` accepts: days / hours / minutes / seconds / milliseconds.
 *
 * Pure ES2024 — Date.getTime() based; not calendar-aware (use dateMath for
 * month/year-level arithmetic).
 */

if (a == null || b == null) throw new Error('a and b required');
const ta = new Date(String(a)).getTime();
const tb = new Date(String(b)).getTime();
if (isNaN(ta) || isNaN(tb)) throw new Error('invalid date');

const diff = tb - ta;
switch (String(unit || 'milliseconds').toLowerCase()) {
  case 'day':    case 'days':    return diff / 86_400_000;
  case 'hour':   case 'hours':   return diff / 3_600_000;
  case 'minute': case 'minutes': return diff / 60_000;
  case 'second': case 'seconds': return diff / 1_000;
  case 'millisecond': case 'milliseconds': case 'ms': return diff;
  default: throw new Error('unsupported unit: ' + unit);
}

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="urlEncode" data-tool-id="urlEncode" data-tool-title="urlEncode" markdown>
<div class="tcg-name"><span class="tcg-name__text">urlEncode</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-link-variant:</div>
<div class="tcg-type">text · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Percent-encodes a string for use in a URL component. Equivalent to encodeURIComponent.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `text` | `STRING` | ✓ | Text to URL-encode |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Percent-encodes a string for safe use inside a URL component.
 *
 * Encodes every character that is not in the RFC 3986 "unreserved" set
 * (ALPHA / DIGIT / -._~). Spaces become %20 (NOT '+'); '&', '=', '?', '#'
 * and other reserved characters are all encoded.
 *
 * Pure ES2024 — uses the standard encodeURIComponent only.
 */

if (text == null) throw new Error('text required');
return encodeURIComponent(String(text));

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="dateMath" data-tool-id="dateMath" data-tool-title="dateMath" markdown>
<div class="tcg-name"><span class="tcg-name__text">dateMath</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-calendar-clock:</div>
<div class="tcg-type">datetime · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Adds (or subtracts) a duration to a date and returns the resulting ISO timestamp. Unit: years|months|weeks|days|hours|minutes|seconds|milliseconds.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text` · `amount` · `unit`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `text` | `STRING` | ✓ | ISO date/time text |
| `amount` | `INTEGER` | ✓ | Amount to add (negative to subtract) |
| `unit` | `STRING` | ✓ | Duration unit |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Adds (or subtracts) a duration to an ISO date.
 *
 * `unit` accepts: years / months / weeks / days / hours / minutes /
 *                  seconds / milliseconds (both singular and plural).
 * Negative `amount` performs subtraction.
 *
 * Pure ES2024 — uses Date UTC setters so DST never bites.
 */

if (text == null || text === '') throw new Error('text required');
const d = new Date(String(text));
if (isNaN(d.getTime())) throw new Error('invalid date: ' + text);

const n = Number(amount);
if (!Number.isFinite(n)) throw new Error('amount must be a finite number');

switch (String(unit || '').toLowerCase()) {
  case 'year':   case 'years':        d.setUTCFullYear(d.getUTCFullYear() + n); break;
  case 'month':  case 'months':       d.setUTCMonth(d.getUTCMonth() + n); break;
  case 'week':   case 'weeks':        d.setUTCDate(d.getUTCDate() + n * 7); break;
  case 'day':    case 'days':         d.setUTCDate(d.getUTCDate() + n); break;
  case 'hour':   case 'hours':        d.setUTCHours(d.getUTCHours() + n); break;
  case 'minute': case 'minutes':      d.setUTCMinutes(d.getUTCMinutes() + n); break;
  case 'second': case 'seconds':      d.setUTCSeconds(d.getUTCSeconds() + n); break;
  case 'millisecond': case 'milliseconds': case 'ms':
    d.setUTCMilliseconds(d.getUTCMilliseconds() + n); break;
  default: throw new Error('unsupported unit: ' + unit);
}
return d.toISOString();

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="parseDate" data-tool-id="parseDate" data-tool-title="parseDate" markdown>
<div class="tcg-name"><span class="tcg-name__text">parseDate</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-calendar-import:</div>
<div class="tcg-type">datetime · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Parses a date/time string (ISO 8601 or RFC 2822) and returns its components plus epochMillis.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text` · `timeZone`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `text` | `STRING` | ✓ | Date/time text |
| `timeZone` | `STRING` |  | IANA time zone to interpret the components in (default UTC) |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Parses a date/time string and returns its individual components in the
 * requested IANA time zone (default UTC), plus the canonical ISO timestamp
 * and epoch millis.
 *
 * Return shape:
 *   { iso, epochMillis, year, month, day, hour, minute, second,
 *     weekday, timeZone }
 *
 * Pure ES2024 — uses Date + Intl.DateTimeFormat.formatToParts.
 */

if (text == null || text === '') throw new Error('text required');
const d = new Date(String(text));
if (isNaN(d.getTime())) throw new Error('invalid date: ' + text);

const tz = timeZone || 'UTC';
const fmt = new Intl.DateTimeFormat('en-CA', {
  timeZone: tz,
  year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', second: '2-digit',
  hour12: false,
  weekday: 'short',
});

const parts = {};
for (const part of fmt.formatToParts(d)) parts[part.type] = part.value;

return {
  iso: d.toISOString(),
  epochMillis: d.getTime(),
  year: Number(parts.year),
  month: Number(parts.month),
  day: Number(parts.day),
  // Some engines emit '24' for midnight; normalise to '0' for sanity.
  hour: Number(parts.hour === '24' ? '0' : parts.hour),
  minute: Number(parts.minute),
  second: Number(parts.second),
  weekday: parts.weekday,
  timeZone: tz,
};

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="cronNext" data-tool-id="cronNext" data-tool-title="cronNext" markdown>
<div class="tcg-name"><span class="tcg-name__text">cronNext</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-timer-cog-outline:</div>
<div class="tcg-type">datetime · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Computes the next datetime matching a standard 5-field cron expression (minute hour day month weekday). Supports * , - / and ? (treated as *). Returns ISO timestamp in UTC.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `expression` · `from` · `count`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `expression` | `STRING` | ✓ | Cron expression (5 fields) |
| `from` | `STRING` |  | Starting ISO datetime (default: now) |
| `count` | `INTEGER` |  | Number of next occurrences to return (default 1, max 100) |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Computes the next N occurrences matching a 5-field cron expression.
 *
 * Field layout:  minute  hour  day-of-month  month  day-of-week
 * Supports `*`, `?`, comma lists (`1,3,5`), ranges (`1-5`) and step
 * suffixes (`* / 15`, `1-23/2`).
 *
 * All times are computed in UTC and returned as ISO timestamps.
 *
 * Pure ES2024 — Date + Set, no external scheduler library.
 */

if (expression == null || expression === '') throw new Error('expression required');
const fields = String(expression).trim().split(/\s+/);
if (fields.length !== 5) throw new Error('expected 5 cron fields, got ' + fields.length);

const ranges = [[0, 59], [0, 23], [1, 31], [1, 12], [0, 6]];

function parseField(spec, [min, max]) {
  // '*' / '?' → full range
  if (spec === '*' || spec === '?') {
    const out = new Set();
    for (let i = min; i <= max; i++) out.add(i);
    return out;
  }
  const out = new Set();
  for (const part of spec.split(',')) {
    const stepIdx = part.indexOf('/');
    const step = stepIdx >= 0 ? parseInt(part.slice(stepIdx + 1), 10) : 1;
    const range = stepIdx >= 0 ? part.slice(0, stepIdx) : part;
    let from, to;
    if (range === '*')          { from = min; to = max; }
    else if (range.includes('-')) { const [aa, bb] = range.split('-').map(s => parseInt(s, 10)); from = aa; to = bb; }
    else                          { const v = parseInt(range, 10); from = v; to = v; }
    for (let i = from; i <= to; i += step) out.add(i);
  }
  return out;
}

const sets = fields.map((f, i) => parseField(f, ranges[i]));

const start = (from && from !== '') ? new Date(String(from)) : new Date();
if (isNaN(start.getTime())) throw new Error('invalid from: ' + from);
const want = count == null ? 1 : Number(count);
if (!Number.isInteger(want) || want < 1 || want > 100) throw new Error('count must be 1..100');

const results = [];
const d = new Date(start.getTime());
d.setUTCSeconds(0, 0);
d.setUTCMinutes(d.getUTCMinutes() + 1);

const limit = 366 * 24 * 60; // walk up to one year forward
let steps = 0;
while (results.length < want && steps < limit) {
  steps++;
  if (sets[0].has(d.getUTCMinutes())
   && sets[1].has(d.getUTCHours())
   && sets[2].has(d.getUTCDate())
   && sets[3].has(d.getUTCMonth() + 1)
   && sets[4].has(d.getUTCDay())) {
    results.push(d.toISOString());
  }
  d.setUTCMinutes(d.getUTCMinutes() + 1);
}
if (results.length === 0) throw new Error('no occurrence found within one year');
return results;

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="diffText" data-tool-id="diffText" data-tool-title="diffText" markdown>
<div class="tcg-name"><span class="tcg-name__text">diffText</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-file-compare:</div>
<div class="tcg-type">text · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Returns a line-by-line diff between two texts. Each entry is {op, line} where op is one of '=' (unchanged), '-' (only in a), '+' (only in b).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `a` · `b`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `a` | `STRING` | ✓ | First text (the 'before' side) |
| `b` | `STRING` | ✓ | Second text (the 'after' side) |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Line-by-line diff between two texts via longest-common-subsequence.
 *
 * Returns an ordered list of { op, line } entries where `op` is:
 *   '='  unchanged line (present in both)
 *   '-'  line only present in `a` (deletion)
 *   '+'  line only present in `b` (insertion)
 *
 * Cost is O(n*m) which is fine for typical configs / smallish files.
 * Pure ES2024.
 */

const A = (a == null ? '' : String(a)).split('\n');
const B = (b == null ? '' : String(b)).split('\n');
const n = A.length, m = B.length;

// 1) Build LCS length table.
const lcs = Array.from({length: n + 1}, () => new Int32Array(m + 1));
for (let i = 0; i < n; i++) {
  for (let j = 0; j < m; j++) {
    lcs[i+1][j+1] = A[i] === B[j]
      ? lcs[i][j] + 1
      : Math.max(lcs[i+1][j], lcs[i][j+1]);
  }
}

// 2) Walk the table backwards to recover the edit script.
const out = [];
let i = n, j = m;
while (i > 0 && j > 0) {
  if (A[i-1] === B[j-1])              { out.push({ op: '=', line: A[i-1] }); i--; j--; }
  else if (lcs[i-1][j] >= lcs[i][j-1]) { out.push({ op: '-', line: A[i-1] }); i--; }
  else                                  { out.push({ op: '+', line: B[j-1] }); j--; }
}
while (i > 0) { out.push({ op: '-', line: A[i-1] }); i--; }
while (j > 0) { out.push({ op: '+', line: B[j-1] }); j--; }
return out.reverse();

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="sortLines" data-tool-id="sortLines" data-tool-title="sortLines" markdown>
<div class="tcg-name"><span class="tcg-name__text">sortLines</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-sort-alphabetical-ascending:</div>
<div class="tcg-type">text · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Sorts lines of text alphabetically. Supports reverse and case-insensitive options.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text` · `reverse` · `caseInsensitive`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `text` | `STRING` | ✓ | Multi-line text to sort |
| `reverse` | `BOOLEAN` |  | Sort descending |
| `caseInsensitive` | `BOOLEAN` |  | Compare case-insensitively |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Sorts text by line.
 *
 * - `reverse=true`        — descending order.
 * - `caseInsensitive=true` — compare with String.localeCompare ignoring case.
 *
 * Pure ES2024.
 */

if (text == null) return '';
const lines = String(text).split(/\r?\n/);

const cmp = caseInsensitive
  ? (x, y) => x.toLowerCase().localeCompare(y.toLowerCase())
  : (x, y) => (x < y ? -1 : x > y ? 1 : 0);
lines.sort(cmp);
if (reverse) lines.reverse();

return lines.join('\n');

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="piiDetect" data-tool-id="piiDetect" data-tool-title="piiDetect" markdown>
<div class="tcg-name"><span class="tcg-name__text">piiDetect</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-shield-account-outline:</div>
<div class="tcg-type">security · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Scans text for personally identifiable information patterns (email, US SSN, US phone, credit card, IPv4). Returns an array of {type, masked, index}.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `text` | `STRING` | ✓ | Text to scan |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Scans text for common PII patterns and returns each finding with its
 * masked value + character offset.
 *
 * Detected:
 *   email           (RFC-shaped, very permissive)
 *   us-phone        ((+1)? AAA-PPP-NNNN with delimiters)
 *   us-ssn          (AAA-GG-SSSS, valid area/group/serial blocks)
 *   ipv4            (0-255.0-255.0-255.0-255)
 *   credit-card     (13-19 digits + Luhn checksum)
 *
 * Each finding's value is masked so the raw PII never leaves the tool.
 * Pure ES2024.
 */

if (text == null || text === '') return [];
const s = String(text);

function luhn(num) {
  // Standard mod-10 check used by all major card brands.
  let sum = 0, even = false;
  for (let i = num.length - 1; i >= 0; i--) {
    let d = num.charCodeAt(i) - 48;
    if (d < 0 || d > 9) return false;
    if (even) { d *= 2; if (d > 9) d -= 9; }
    sum += d; even = !even;
  }
  return sum > 0 && sum % 10 === 0;
}

const mask = v => v.length <= 6
  ? '*'.repeat(v.length)
  : v.slice(0, 2) + '*'.repeat(v.length - 4) + v.slice(-2);

const patterns = [
  { type: 'email',     re: /\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b/g },
  { type: 'us-phone',  re: /\b(?:\+?1[-. ]?)?\(?[2-9]\d{2}\)?[-. ]?\d{3}[-. ]?\d{4}\b/g },
  { type: 'us-ssn',    re: /\b(?!000|666|9\d{2})\d{3}[- ](?!00)\d{2}[- ](?!0000)\d{4}\b/g },
  { type: 'ipv4',      re: /\b(?:25[0-5]|2[0-4]\d|1?\d{1,2})(?:\.(?:25[0-5]|2[0-4]\d|1?\d{1,2})){3}\b/g },
];

const out = [];
for (const { type, re } of patterns) {
  for (const m of s.matchAll(re)) {
    out.push({ type, masked: mask(m[0]), index: m.index });
  }
}

// Credit card detection — extract digit groups then validate Luhn.
const ccRe = /\b(?:\d[ -]?){13,19}\b/g;
for (const m of s.matchAll(ccRe)) {
  const digits = m[0].replace(/[ -]/g, '');
  if (digits.length >= 13 && digits.length <= 19 && luhn(digits)) {
    out.push({ type: 'credit-card', masked: mask(digits), index: m.index });
  }
}

out.sort((x, y) => x.index - y.index);
return out;

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="regexExtract" data-tool-id="regexExtract" data-tool-title="regexExtract" markdown>
<div class="tcg-name"><span class="tcg-name__text">regexExtract</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-regex:</div>
<div class="tcg-type">text · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Returns all regex matches in the input. With the 'g' flag every match is returned; without it the first match (with capture groups) is returned.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text` · `pattern` · `flags`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `text` | `STRING` | ✓ | Text to search |
| `pattern` | `STRING` | ✓ | JavaScript-flavoured regex pattern |
| `flags` | `STRING` |  | Regex flags (e.g. 'g', 'i', 'gi') |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Returns every match of a JavaScript regex against the input text.
 *
 * - With the `g` flag, every match is returned (matchAll semantics).
 * - Without `g`, the first match (and its capture groups) is returned.
 *
 * Each result item is:  { match, groups: [...], index }
 *
 * Pure ES2024 — uses RegExp, matchAll, match.
 */

if (text == null) return [];
if (pattern == null || pattern === '') throw new Error('pattern required');

const flagsStr = (typeof flags === 'string') ? flags : '';
const re = new RegExp(pattern, flagsStr);

if (re.global) {
  // Global flag: collect every match.
  const out = [];
  for (const m of String(text).matchAll(re)) {
    out.push({ match: m[0], groups: Array.from(m).slice(1), index: m.index });
  }
  return out;
}

// Non-global: return at most one match (with its groups).
const m = String(text).match(re);
if (!m) return [];
return [{ match: m[0], groups: Array.from(m).slice(1), index: m.index }];

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="formatDate" data-tool-id="formatDate" data-tool-title="formatDate" markdown>
<div class="tcg-name"><span class="tcg-name__text">formatDate</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-calendar-text-outline:</div>
<div class="tcg-type">datetime · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Formats a date (ISO string or epoch ms) using a pattern with tokens yyyy/MM/dd HH:mm:ss SSS. Time zone aware.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text` · `pattern` · `timeZone`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `text` | `STRING` | ✓ | ISO date/time text |
| `pattern` | `STRING` |  | Format pattern (e.g. 'yyyy-MM-dd HH:mm:ss') |
| `timeZone` | `STRING` |  | IANA time zone (default UTC) |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Formats an ISO date/time string with a token pattern in the given zone.
 *
 * Supported tokens:  yyyy MM dd HH mm ss SSS
 * Anything else in the pattern is passed through literally.
 *
 * Pure ES2024 — uses Intl.DateTimeFormat.formatToParts.
 */

if (text == null || text === '') throw new Error('text required');
const d = new Date(String(text));
if (isNaN(d.getTime())) throw new Error('invalid date: ' + text);

const tz = timeZone || 'UTC';
const pat = pattern || 'yyyy-MM-dd HH:mm:ss';

const fmt = new Intl.DateTimeFormat('en-CA', {
  timeZone: tz,
  year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', second: '2-digit',
  hour12: false,
  fractionalSecondDigits: 3,
});

const parts = {};
for (const part of fmt.formatToParts(d)) parts[part.type] = part.value;

const map = {
  'yyyy': parts.year,
  'MM':   parts.month,
  'dd':   parts.day,
  'HH':   parts.hour === '24' ? '00' : parts.hour,
  'mm':   parts.minute,
  'ss':   parts.second,
  'SSS':  parts.fractionalSecond || '000',
};
return pat.replace(/yyyy|SSS|MM|dd|HH|mm|ss/g, t => map[t] || t);

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="stats" data-tool-id="stats" data-tool-title="stats" markdown>
<div class="tcg-name"><span class="tcg-name__text">stats</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-sigma:</div>
<div class="tcg-type">math · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Returns summary statistics (count, sum, min, max, mean, median, stddev) for an array of numbers.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `numbers`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `numbers` | `ARRAY` | ✓ | Array of numbers |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Summary statistics for a numeric array.
 *
 * Returns: { count, sum, min, max, mean, median, stddev }.
 * Empty array → null for every aggregate except count/sum (both 0).
 *
 * Pure ES2024.
 */

if (numbers == null) throw new Error('numbers required');

// Coerce + validate each element.
const xs = [];
for (const v of numbers) {
  const n = Number(v);
  if (!Number.isFinite(n)) throw new Error('non-numeric value: ' + v);
  xs.push(n);
}
if (xs.length === 0) {
  return { count: 0, sum: 0, min: null, max: null, mean: null, median: null, stddev: null };
}

const sum = xs.reduce((a, b) => a + b, 0);
const mean = sum / xs.length;
const sorted = xs.slice().sort((a, b) => a - b);
const mid = Math.floor(sorted.length / 2);
const median = sorted.length % 2
  ? sorted[mid]
  : (sorted[mid - 1] + sorted[mid]) / 2;
// Population variance (divide by N, not N-1).
const variance = xs.reduce((a, b) => a + (b - mean) ** 2, 0) / xs.length;

return {
  count: xs.length,
  sum,
  min: sorted[0],
  max: sorted[sorted.length - 1],
  mean,
  median,
  stddev: Math.sqrt(variance),
};

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="secretPatternDetect" data-tool-id="secretPatternDetect" data-tool-title="secretPatternDetect" markdown>
<div class="tcg-name"><span class="tcg-name__text">secretPatternDetect</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-key-alert-outline:</div>
<div class="tcg-type">security · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Scans text for well-known secret patterns (AWS keys, GitHub tokens, Slack tokens, OpenAI keys, Stripe keys, private keys). Returns an array of {type, masked, index}.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `text` | `STRING` | ✓ | Text to scan |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Scans text for high-confidence secret patterns and returns each finding
 * with its masked value + character offset.
 *
 * Detected formats:
 *   AWS access key id          (AKIA…)
 *   GitHub PAT / OAuth / app   (ghp_, gho_, ghu_, ghs_, ghr_)
 *   Slack token                (xox[abprs]-…)
 *   OpenAI API key             (sk-…)
 *   Stripe key                 (sk_/pk_/rk_test|live_…)
 *   PEM private key block      (-----BEGIN … PRIVATE KEY-----)
 *   JWT compact form           (eyJ…eyJ….…)
 *
 * Each finding is masked to `XXXX****XXXX` form so the secret never leaks
 * into the result. Pure ES2024.
 */

if (text == null || text === '') return [];
const s = String(text);

const patterns = [
  { type: 'aws-access-key-id', re: /\bAKIA[0-9A-Z]{16}\b/g },
  { type: 'github-token',      re: /\b(ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{36,}\b/g },
  { type: 'slack-token',       re: /\bxox[abprs]-[A-Za-z0-9-]{10,}\b/g },
  { type: 'openai-api-key',    re: /\bsk-(proj-)?[A-Za-z0-9_-]{20,}\b/g },
  { type: 'stripe-key',        re: /\b(sk|pk|rk)_(test|live)_[A-Za-z0-9]{16,}\b/g },
  { type: 'private-key-block', re: /-----BEGIN (RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----/g },
  { type: 'jwt',               re: /\beyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b/g },
];

const mask = v => v.length <= 8
  ? '*'.repeat(v.length)
  : v.slice(0, 4) + '*'.repeat(v.length - 8) + v.slice(-4);

const out = [];
for (const { type, re } of patterns) {
  for (const m of s.matchAll(re)) {
    out.push({ type, masked: mask(m[0]), index: m.index });
  }
}
out.sort((x, y) => x.index - y.index);
return out;

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="regexReplace" data-tool-id="regexReplace" data-tool-title="regexReplace" markdown>
<div class="tcg-name"><span class="tcg-name__text">regexReplace</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-find-replace:</div>
<div class="tcg-type">text · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Replaces regex matches in the input with the given replacement string. Supports $1, $2 group back-references.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text` · `pattern` · `replacement` · `flags`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `text` | `STRING` | ✓ | Text to transform |
| `pattern` | `STRING` | ✓ | JavaScript-flavoured regex pattern |
| `replacement` | `STRING` | ✓ | Replacement string (supports $1, $2, ...) |
| `flags` | `STRING` |  | Regex flags (e.g. 'g', 'i', 'gi') |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Regex-replaces text using a JavaScript-flavoured pattern.
 *
 * Replacement string supports the usual back-references: $1, $2, $&, $`, $'.
 * Add the `g` flag to replace every match; otherwise only the first match
 * is replaced.
 *
 * Pure ES2024 — uses RegExp + String.replace.
 */

if (text == null) return '';
if (pattern == null || pattern === '') throw new Error('pattern required');

const flagsStr = (typeof flags === 'string') ? flags : '';
const re = new RegExp(pattern, flagsStr);
return String(text).replace(re, replacement == null ? '' : String(replacement));

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="evalExpression" data-tool-id="evalExpression" data-tool-title="evalExpression" markdown>
<div class="tcg-name"><span class="tcg-name__text">evalExpression</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-calculator-variant-outline:</div>
<div class="tcg-type">math · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Evaluates a safe arithmetic/logical expression (no eval, no host access). Supports + - * / % ** parens, &&, ||, !, ==, !=, <, <=, >, >=, and numeric/string/boolean literals plus variables from the variables object.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `expression` · `variables`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `expression` | `STRING` | ✓ | Expression to evaluate |
| `variables` | `OBJECT` |  | Map of variable bindings |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Safe arithmetic / logical expression evaluator.
 *
 * Supports:  + - * / % **   ( )   !
 *            && || ! ==  != < <= > >=
 *            number / string literals, true / false / null
 *            variables resolved from the `variables` parameter.
 *
 * Does NOT call `eval`, `new Function`, or any host class — the parser is
 * hand-written so untrusted expressions cannot break out of the sandbox.
 *
 * Pure ES2024.
 */

if (expression == null || expression === '') throw new Error('expression required');

// Copy polyglot foreign map into a plain JS object once.
const rawVars = variables || {};
const vars = {};
for (const k of Object.keys(rawVars)) vars[k] = rawVars[k];

const src = String(expression);
let i = 0;

function peek() { return src[i]; }
function next() { return src[i++]; }
function skipWs() { while (i < src.length && /\s/.test(src[i])) i++; }

function readNumber() {
  let s = '';
  while (i < src.length && /[0-9.]/.test(src[i])) s += next();
  if (i < src.length && (src[i] === 'e' || src[i] === 'E')) {
    s += next();
    if (src[i] === '+' || src[i] === '-') s += next();
    while (i < src.length && /[0-9]/.test(src[i])) s += next();
  }
  const n = parseFloat(s);
  if (!Number.isFinite(n)) throw new Error('bad number: ' + s);
  return n;
}

function readString(q) {
  next(); let s = '';
  while (i < src.length && src[i] !== q) {
    if (src[i] === '\\' && i + 1 < src.length) { i++; s += src[i++]; continue; }
    s += next();
  }
  if (src[i] !== q) throw new Error('unterminated string');
  next();
  return s;
}

function readIdent() {
  let s = '';
  while (i < src.length && /[A-Za-z0-9_$]/.test(src[i])) s += next();
  return s;
}

function parseValue() {
  skipWs();
  const c = peek();
  if (c === '(') { next(); const v = parseExpr(); skipWs(); if (next() !== ')') throw new Error('missing )'); return v; }
  if (c === '!') { next(); return !parseValue(); }
  if (c === '-') { next(); return -parseValue(); }
  if (c === '+') { next(); return +parseValue(); }
  if (c === '"' || c === "'") return readString(c);
  if (/[0-9.]/.test(c)) return readNumber();
  if (/[A-Za-z_$]/.test(c)) {
    const id = readIdent();
    if (id === 'true')  return true;
    if (id === 'false') return false;
    if (id === 'null')  return null;
    if (!(id in vars)) throw new Error('undefined variable: ' + id);
    return vars[id];
  }
  throw new Error('unexpected: ' + c);
}

// Operator precedence climbing — Pow > Mul/Div > Add/Sub > Cmp > Eq > And > Or.
function parsePow() { let a = parseValue(); skipWs();
  if (src[i] === '*' && src[i+1] === '*') { i += 2; return a ** parsePow(); }
  return a;
}
function parseMul() { let a = parsePow();
  while (true) {
    skipWs();
    const c = peek();
    if      (c === '*' && src[i+1] !== '*') { next(); a = a * parsePow(); }
    else if (c === '/')                      { next(); a = a / parsePow(); }
    else if (c === '%')                      { next(); a = a % parsePow(); }
    else break;
  }
  return a;
}
function parseAdd() { let a = parseMul();
  while (true) {
    skipWs();
    const c = peek();
    if      (c === '+') { next(); a = a + parseMul(); }
    else if (c === '-') { next(); a = a - parseMul(); }
    else break;
  }
  return a;
}
function parseCmp() { let a = parseAdd(); skipWs();
  if (src[i] === '<' && src[i+1] === '=') { i += 2; return a <= parseAdd(); }
  if (src[i] === '>' && src[i+1] === '=') { i += 2; return a >= parseAdd(); }
  if (src[i] === '<')                       { next(); return a < parseAdd(); }
  if (src[i] === '>')                       { next(); return a > parseAdd(); }
  return a;
}
function parseEq() { let a = parseCmp(); skipWs();
  while (src[i] === '=' && src[i+1] === '=') { i += 2; if (src[i] === '=') i++; a = (a == parseCmp()); skipWs(); }
  while (src[i] === '!' && src[i+1] === '=') { i += 2; if (src[i] === '=') i++; a = (a != parseCmp()); skipWs(); }
  return a;
}
function parseAnd() { let a = parseEq();  skipWs(); while (src[i] === '&' && src[i+1] === '&') { i += 2; a = a && parseEq();  skipWs(); } return a; }
function parseOr () { let a = parseAnd(); skipWs(); while (src[i] === '|' && src[i+1] === '|') { i += 2; a = a || parseAnd(); skipWs(); } return a; }
function parseExpr() { return parseOr(); }

const result = parseExpr();
skipWs();
if (i < src.length) throw new Error('unexpected trailing input at ' + i);
return result;

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="formatCsv" data-tool-id="formatCsv" data-tool-title="formatCsv" markdown>
<div class="tcg-name"><span class="tcg-name__text">formatCsv</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-file-table-outline:</div>
<div class="tcg-type">data · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Serialises an array of rows into CSV text. Rows may be arrays (use header param) or objects (keys become the header). RFC 4180 quoting.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `rows` · `header` · `delimiter`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `rows` | `ARRAY` | ✓ | Array of row objects/arrays |
| `header` | `ARRAY` |  | Optional explicit column order |
| `delimiter` | `STRING` |  | Field delimiter (default ',') |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Serialises an array of rows into RFC 4180 CSV.
 *
 * Rows can be either:
 *   - objects  →  the first row's keys (or the explicit `header` array)
 *                  define the column order.
 *   - arrays   →  rows are written as-is; pass `header` to add a header row.
 *
 * Cells that contain the delimiter, a double-quote, CR or LF are wrapped in
 * `"…"` and embedded quotes are doubled per RFC 4180.
 *
 * Pure ES2024.
 */

if (rows == null) throw new Error('rows required');

// Polyglot foreign collections aren't iterable through `[...rows]` directly,
// so collect via for-of (which works for both JS arrays and Java Lists).
const rowArr = [];
for (const r of rows) rowArr.push(r);

const headerArr = [];
if (header != null) for (const h of header) headerArr.push(String(h));

const delim = (typeof delimiter === 'string' && delimiter.length > 0) ? delimiter[0] : ',';

function quote(v) {
  if (v == null) return '';
  const s = String(v);
  if (s.includes(delim) || s.includes('"') || s.includes('\n') || s.includes('\r')) {
    return '"' + s.replace(/"/g, '""') + '"';
  }
  return s;
}

function isArrayLike(v) {
  return v != null && typeof v !== 'string'
      && typeof v[Symbol.iterator] === 'function';
}

// Decide on column order: explicit header > union of object keys > none.
let cols = headerArr.length > 0 ? headerArr : null;
if (!cols && rowArr.length > 0 && !isArrayLike(rowArr[0])) {
  const seen = new Set();
  cols = [];
  for (const r of rowArr) {
    for (const k of Object.keys(r)) {
      if (!seen.has(k)) { seen.add(k); cols.push(k); }
    }
  }
}

const out = [];
if (cols) out.push(cols.map(quote).join(delim));
for (const r of rowArr) {
  if (isArrayLike(r)) {
    const cells = [];
    for (const c of r) cells.push(quote(c));
    out.push(cells.join(delim));
  } else if (cols) {
    out.push(cols.map(k => quote(r[k])).join(delim));
  } else {
    out.push(quote(r));
  }
}
return out.join('\n');

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="base64" data-tool-id="base64" data-tool-title="base64" markdown>
<div class="tcg-name"><span class="tcg-name__text">base64</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-code-tags:</div>
<div class="tcg-type">encoding · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Encodes UTF-8 text to base64, or decodes base64 back to UTF-8 text. Use mode='encode' (default) or 'decode'. URL-safe variant via urlSafe=true.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text` · `mode` · `urlSafe`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `text` | `STRING` | ✓ | Text to encode/decode |
| `mode` | `STRING` |  | encode \| decode |
| `urlSafe` | `BOOLEAN` |  | Use URL-safe alphabet (- _ instead of + /) |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Base64 encode / decode of UTF-8 text.
 *
 * - `mode='encode'` (default) — UTF-8 text → base64 string.
 * - `mode='decode'`           — base64 string → UTF-8 text.
 * - `urlSafe=true`            — emit/accept the URL-safe alphabet
 *                                (`-` `_` instead of `+` `/`, padding stripped).
 *
 * Uses host-injected btoa / atob and the standard TextEncoder / TextDecoder.
 */

if (text == null) throw new Error('text required');
const m = (mode || 'encode').toLowerCase();

if (m === 'encode') {
  const bytes = new TextEncoder().encode(String(text));
  // btoa expects a Latin-1 binary string — assemble it byte-by-byte.
  let bin = '';
  for (const b of bytes) bin += String.fromCharCode(b);
  let b64 = btoa(bin);
  if (urlSafe) b64 = b64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
  return b64;
}

if (m === 'decode') {
  let s = String(text);
  if (urlSafe) {
    s = s.replace(/-/g, '+').replace(/_/g, '/');
    const pad = s.length % 4; if (pad) s += '='.repeat(4 - pad);
  }
  const bin = atob(s);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return new TextDecoder('utf-8', { fatal: false }).decode(bytes);
}

throw new Error('mode must be encode or decode');

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="hex" data-tool-id="hex" data-tool-title="hex" markdown>
<div class="tcg-name"><span class="tcg-name__text">hex</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-numeric:</div>
<div class="tcg-type">encoding · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Encodes UTF-8 text to hex string, or decodes hex back to UTF-8 text. Use mode='encode' (default) or 'decode'.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text` · `mode` · `upperCase`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `text` | `STRING` | ✓ | Text to encode/decode |
| `mode` | `STRING` |  | encode \| decode |
| `upperCase` | `BOOLEAN` |  | Use uppercase hex when encoding |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Hex encode / decode of UTF-8 text.
 *
 * - `mode='encode'` (default) — UTF-8 text → lowercase hex string.
 * - `mode='decode'`           — hex string → UTF-8 text.
 * - `upperCase=true`          — emit uppercase hex when encoding.
 *
 * Whitespace inside the hex input is ignored on decode.
 * Uses host TextEncoder / TextDecoder.
 */

if (text == null) throw new Error('text required');
const m = (mode || 'encode').toLowerCase();

if (m === 'encode') {
  const bytes = new TextEncoder().encode(String(text));
  let out = '';
  for (const b of bytes) out += b.toString(16).padStart(2, '0');
  return upperCase ? out.toUpperCase() : out;
}

if (m === 'decode') {
  const s = String(text).replace(/\s+/g, '');
  if (s.length % 2 !== 0)       throw new Error('hex length must be even');
  if (!/^[0-9a-fA-F]*$/.test(s)) throw new Error('invalid hex character');
  const bytes = new Uint8Array(s.length / 2);
  for (let i = 0; i < bytes.length; i++) bytes[i] = parseInt(s.substr(i * 2, 2), 16);
  return new TextDecoder('utf-8', { fatal: false }).decode(bytes);
}

throw new Error('mode must be encode or decode');

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="uuid" data-tool-id="uuid" data-tool-title="uuid" markdown>
<div class="tcg-name"><span class="tcg-name__text">uuid</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-identifier:</div>
<div class="tcg-type">crypto · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Generates a cryptographically random UUID v4 string.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; —</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Generates a cryptographically random UUID v4.
 *
 * Uses the host-injected crypto.randomUUID() (no static seeding —
 * each call is unpredictable).
 */

return crypto.randomUUID();

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="hash" data-tool-id="hash" data-tool-title="hash" markdown>
<div class="tcg-name"><span class="tcg-name__text">hash</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-pound:</div>
<div class="tcg-type">crypto · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Computes the cryptographic hash of UTF-8 text. Algorithms: SHA-256 (default), SHA-384, SHA-512. Returns lowercase hex.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text` · `algorithm`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `text` | `STRING` | ✓ | Text to hash |
| `algorithm` | `STRING` |  | SHA-256 \| SHA-384 \| SHA-512 |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Computes a cryptographic hash of UTF-8 text and returns lowercase hex.
 *
 * Supported algorithms:  SHA-256 (default), SHA-384, SHA-512.
 *
 * Uses the host-injected WebCrypto API (crypto.subtle.digest).
 */

if (text == null) throw new Error('text required');
const alg = algorithm || 'SHA-256';

const bytes  = new TextEncoder().encode(String(text));
const digest = await crypto.subtle.digest(alg, bytes);

const arr = Array.isArray(digest) ? digest : Array.from(digest);
return arr.map(b => (b & 0xff).toString(16).padStart(2, '0')).join('');

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="hmac" data-tool-id="hmac" data-tool-title="hmac" markdown>
<div class="tcg-name"><span class="tcg-name__text">hmac</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-key-variant:</div>
<div class="tcg-type">crypto · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Computes an HMAC signature over UTF-8 text using a secret. Algorithms: SHA-256 (default), SHA-384, SHA-512. Returns lowercase hex.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `secret` · `text` · `algorithm`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `secret` | `STRING` | ✓ | Secret key (UTF-8 string) |
| `text` | `STRING` | ✓ | Text to sign |
| `algorithm` | `STRING` |  | SHA-256 \| SHA-384 \| SHA-512 |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Computes an HMAC over UTF-8 text using a shared secret. Returns lowercase hex.
 *
 * Supported HMAC hashes:  SHA-256 (default), SHA-384, SHA-512.
 *
 * Uses WebCrypto:  importKey('raw', …, HMAC) → sign('HMAC', key, data).
 */

if (secret == null || secret === '') throw new Error('secret required');
if (text   == null)                  throw new Error('text required');
const alg = algorithm || 'SHA-256';

const keyBytes = new TextEncoder().encode(String(secret));
const key = await crypto.subtle.importKey(
  'raw', keyBytes, { name: 'HMAC', hash: alg }, false, ['sign']);

const sig = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(String(text)));

const arr = Array.isArray(sig) ? sig : Array.from(sig);
return arr.map(b => (b & 0xff).toString(16).padStart(2, '0')).join('');

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="secureRandom" data-tool-id="secureRandom" data-tool-title="secureRandom" markdown>
<div class="tcg-name"><span class="tcg-name__text">secureRandom</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-dice-6-outline:</div>
<div class="tcg-type">crypto · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Generates cryptographically secure random bytes. encoding: 'hex' (default), 'base64', or 'base64url'.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `bytes` · `encoding`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `bytes` | `INTEGER` |  | Number of random bytes (default 16, max 4096) |
| `encoding` | `STRING` |  | hex \| base64 \| base64url |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Generates cryptographically secure random bytes.
 *
 * - `bytes`     — number of bytes (1..4096, default 16).
 * - `encoding`  — 'hex' (default), 'base64', or 'base64url'.
 *
 * Uses crypto.getRandomValues, which delegates to the OS CSPRNG.
 */

const n = bytes == null ? 16 : Number(bytes);
if (!Number.isInteger(n) || n < 1 || n > 4096) throw new Error('bytes must be 1..4096');

const buf = new Uint8Array(n);
crypto.getRandomValues(buf);

const enc = (encoding || 'hex').toLowerCase();
if (enc === 'hex') {
  let out = '';
  for (const b of buf) out += b.toString(16).padStart(2, '0');
  return out;
}

let bin = '';
for (const b of buf) bin += String.fromCharCode(b);
let b64 = btoa(bin);
if (enc === 'base64url') b64 = b64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
return b64;

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="passwordGenerate" data-tool-id="passwordGenerate" data-tool-title="passwordGenerate" markdown>
<div class="tcg-name"><span class="tcg-name__text">passwordGenerate</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-form-textbox-password:</div>
<div class="tcg-type">crypto · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Generates a strong random password from selected character classes. Uses crypto.getRandomValues for unbiased selection.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `length` · `includeLowercase` · `includeUppercase` · `includeDigits` · `includeSymbols`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `length` | `INTEGER` |  | Password length (default 16, min 4, max 256) |
| `includeLowercase` | `BOOLEAN` |  | Include a-z |
| `includeUppercase` | `BOOLEAN` |  | Include A-Z |
| `includeDigits` | `BOOLEAN` |  | Include 0-9 |
| `includeSymbols` | `BOOLEAN` |  | Include ASCII punctuation |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Generates a strong random password from selected character classes.
 *
 * Guarantees one character from EACH enabled class, then fills the rest
 * uniformly from the union and shuffles cryptographically. Uses unbiased
 * rejection sampling — no modulo bias.
 *
 * Defaults: length 16, classes lower/upper/digits enabled, symbols off.
 * Uses host crypto.getRandomValues.
 */

const len = length == null ? 16 : Number(length);
if (!Number.isInteger(len) || len < 4 || len > 256) throw new Error('length must be 4..256');

const lo = 'abcdefghijklmnopqrstuvwxyz';
const up = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
const di = '0123456789';
const sy = '!@#$%^&*()-_=+[]{};:,.<>/?';

const useLo = includeLowercase !== false;
const useUp = includeUppercase !== false;
const useDi = includeDigits    !== false;
const useSy = includeSymbols    === true;

let pool = '';
if (useLo) pool += lo;
if (useUp) pool += up;
if (useDi) pool += di;
if (useSy) pool += sy;
if (pool.length === 0) throw new Error('at least one character class required');

const required = [];
if (useLo) required.push(lo);
if (useUp) required.push(up);
if (useDi) required.push(di);
if (useSy) required.push(sy);

// Unbiased pick via rejection sampling on the high-resolution random pool.
function pick(alphabet) {
  const buf = new Uint32Array(1);
  const max = Math.floor(0xFFFFFFFF / alphabet.length) * alphabet.length;
  do { crypto.getRandomValues(buf); } while (buf[0] >= max);
  return alphabet[buf[0] % alphabet.length];
}

const chars = required.map(pick);
while (chars.length < len) chars.push(pick(pool));

// Fisher-Yates shuffle, also with rejection-sampled randomness.
for (let i = chars.length - 1; i > 0; i--) {
  const buf = new Uint32Array(1);
  const max = Math.floor(0xFFFFFFFF / (i + 1)) * (i + 1);
  do { crypto.getRandomValues(buf); } while (buf[0] >= max);
  const j = buf[0] % (i + 1);
  [chars[i], chars[j]] = [chars[j], chars[i]];
}
return chars.join('');

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="jwtDecode" data-tool-id="jwtDecode" data-tool-title="jwtDecode" markdown>
<div class="tcg-name"><span class="tcg-name__text">jwtDecode</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-shield-key-outline:</div>
<div class="tcg-type">crypto · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Decodes a JWT without verifying its signature. Returns the header and payload as JSON, plus signaturePresent.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `token`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `token` | `STRING` | ✓ | JWT compact form (header.payload.signature) |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Decodes a JWT (header + payload) WITHOUT verifying the signature.
 *
 * Returns:  { header, payload, signaturePresent }
 *
 * Use this for inspection only. To validate authenticity, pass the token
 * through `jwtVerify` with the shared secret.
 *
 * Uses host-injected atob + standard TextDecoder.
 */

if (token == null || token === '') throw new Error('token required');
const parts = String(token).split('.');
if (parts.length < 2) throw new Error('not a JWT (expected at least 2 segments)');

function b64urlDecode(s) {
  s = s.replace(/-/g, '+').replace(/_/g, '/');
  const pad = s.length % 4; if (pad) s += '='.repeat(4 - pad);
  return new TextDecoder('utf-8', { fatal: false })
    .decode(Uint8Array.from(atob(s), c => c.charCodeAt(0)));
}

const header  = JSON.parse(b64urlDecode(parts[0]));
const payload = JSON.parse(b64urlDecode(parts[1]));
return {
  header,
  payload,
  signaturePresent: parts.length >= 3 && parts[2].length > 0,
};

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="jwtVerify" data-tool-id="jwtVerify" data-tool-title="jwtVerify" markdown>
<div class="tcg-name"><span class="tcg-name__text">jwtVerify</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-shield-check-outline:</div>
<div class="tcg-type">crypto · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Verifies a HS256/HS384/HS512 JWT signature using a shared secret and returns the decoded payload on success. Also checks exp and nbf claims when present.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `token` · `secret`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `token` | `STRING` | ✓ | JWT compact form |
| `secret` | `STRING` | ✓ | Shared HMAC secret |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Verifies a HS256/HS384/HS512 JWT signature with a shared secret and
 * returns the decoded payload, plus any structural validity issues.
 *
 * Return shape:
 *   { valid, algorithm, header, payload, reasons }
 *
 * `reasons` is empty on success; otherwise it lists every failed check
 * (bad signature, token expired, token not yet valid).
 *
 * Uses host WebCrypto + atob.  Constant-time-style equality (XOR-accumulate)
 * so the verifier doesn't leak signature bytes via timing.
 */

if (token  == null || token  === '') throw new Error('token required');
if (secret == null || secret === '') throw new Error('secret required');

const parts = String(token).split('.');
if (parts.length !== 3) throw new Error('JWT must have 3 segments');

function b64urlDecode(s) {
  s = s.replace(/-/g, '+').replace(/_/g, '/');
  const pad = s.length % 4; if (pad) s += '='.repeat(4 - pad);
  return Uint8Array.from(atob(s), c => c.charCodeAt(0));
}
function b64urlDecodeJson(s) {
  return JSON.parse(new TextDecoder().decode(b64urlDecode(s)));
}

const header  = b64urlDecodeJson(parts[0]);
const payload = b64urlDecodeJson(parts[1]);

const alg = String(header.alg || '').toUpperCase();
const hashAlg = ({ HS256: 'SHA-256', HS384: 'SHA-384', HS512: 'SHA-512' })[alg];
if (!hashAlg) throw new Error('unsupported alg: ' + alg);

const keyBytes = new TextEncoder().encode(String(secret));
const key = await crypto.subtle.importKey(
  'raw', keyBytes, { name: 'HMAC', hash: hashAlg }, false, ['sign']);

const signingInput = new TextEncoder().encode(parts[0] + '.' + parts[1]);
const expected     = await crypto.subtle.sign('HMAC', key, signingInput);

// Constant-time compare expected vs provided signature.
const expectedBytes = Array.isArray(expected) ? expected : Array.from(expected);
const actualBytes   = Array.from(b64urlDecode(parts[2]));
let acc = 0;
const len = Math.max(expectedBytes.length, actualBytes.length);
for (let idx = 0; idx < len; idx++) {
  acc |= (expectedBytes[idx] || 0) ^ (actualBytes[idx] || 0);
}
const sigOk = expectedBytes.length === actualBytes.length && acc === 0;

const now = Math.floor(Date.now() / 1000);
const reasons = [];
if (!sigOk)                                                   reasons.push('bad signature');
if (typeof payload.exp === 'number' && now >= payload.exp)    reasons.push('token expired');
if (typeof payload.nbf === 'number' && now <  payload.nbf)    reasons.push('token not yet valid');

return {
  valid: reasons.length === 0,
  algorithm: alg,
  header,
  payload,
  reasons,
};

```

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="parseCsv" data-tool-id="parseCsv" data-tool-title="parseCsv" markdown>
<div class="tcg-name"><span class="tcg-name__text">parseCsv</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-file-import-outline:</div>
<div class="tcg-type">data · util <span class="risk risk-l0">L0</span></div>
<div class="tcg-body" markdown>
Parses CSV text into an array of rows. If header=true, each row is an object keyed by the first row.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `text` · `header` · `delimiter`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; —</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `text` | `STRING` | ✓ | CSV text |
| `header` | `BOOLEAN` |  | Treat the first row as the header |
| `delimiter` | `STRING` |  | Field delimiter (default ',') |

**Sandbox** — Runs at sandbox **L0** baseline — no filesystem, default-strict network (SSRF-defended).

**JS source**

```javascript
/**
 * Parses RFC 4180 CSV via the host-bridged Apache Commons CSV parser
 * (`safety.parser.csv`).
 *
 * - `header=true`        — first row is the header; each subsequent row is
 *                          returned as an object keyed by header.
 * - `delimiter`          — single-character field separator (default ',').
 *
 * Always available — no Java interop or network needed.
 */

if (text == null || text === '') return [];
const opts = { header: !!header };
if (delimiter) opts.delimiter = String(delimiter);
return safety.parser.csv(String(text), opts);

```

</div>
</div>

</div>

## Composition patterns (in-memory data pipelines)

The 26 utilities are deliberately I/O-free, which makes them perfect for chains the agent runs entirely in-memory:

- **Text-shell ETL** — `regexExtract(text, pattern)` → `sortLines(text)` → `formatCsv(rows)` to turn raw log/text into structured CSV in one tool turn.
- **Crypto sign-and-mint** — `secureRandom(bytes=32)` for the secret → `hmac(secret, payload)` for the signature → `base64(text)` to wire-encode — issuing a signed token without any `Java.type(...)` interop.
- **JWT inspect → re-encode** — `jwtDecode(token)` exposes header + payload → mutate fields in the agent prompt → `hmac` + `base64` to rebuild a signed JWT (HS256/384/512).
- **Time math chain** — `parseDate(text)` → `dateMath(text, amount, unit)` → `formatDate(text, pattern, timeZone)` to normalise a user-supplied datetime to a target zone with a single call.
- **PII / secret scrub** — `piiDetect(text)` + `secretPatternDetect(text)` over a chat transcript before forwarding it to a model — both return `[{type, masked, index}]` so the agent can redact in-place.

[Tutorial 8: Default Tool Recipes](../../tutorials/8-default-tool-recipes.md) walks one of these end-to-end (the text-shell ETL chain).

## Keys & secrets

**None.** All 26 utilities run with the default sandbox baseline — no network, no filesystem, no env vars. That is what makes them the safe slice of the catalog to expose to the model with zero setup.

→ [Tool Studio: Built-in JavaScript Helpers](../tool-studio.md#built-in-javascript-helpers) — the underlying `crypto.subtle`, `safety.parser.csv`, regex helpers each of these tools wraps.
→ [Index](index.md) — overview of all 86 default tools and the five reference pages.
