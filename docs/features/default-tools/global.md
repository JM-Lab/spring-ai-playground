description: Default Tools - Global reference. 21 tools that call public HTTPS APIs - GitHub, Wikipedia, weather, finance, geo, search.

# Default Tools - Global

The 21 tools in `default-tool-specs-network.json` call **public global HTTPS endpoints** - most of them anonymous, all of them outside Korea. Categories span code (GitHub), encyclopedia (Wikipedia), forum (Hacker News, Stack Overflow), finance (CoinGecko, open.er-api.com), geo (ipapi.co, mledoze/countries, Nominatim, sunrise-sunset, USGS), weather (Open-Meteo), and government data (Nager.Date public holidays).

None of them need an API key - they live entirely off the providers' anonymous rate-limit tiers. Tool actions execute with host-`allowlist` egress, so every fetch goes through [the SSRF four-layer guard](../tool-studio/index.md#ssrf-four-layer-guard) regardless of whether the destination is a literal IP or a DNS host.

The grouping below mirrors the `tags` axis each tool carries - the same axis you can filter by in the Tool Studio tool list.

## Browse the 21 global APIs { #browse-the-global-apis }

All run with host-`allowlist` egress (SSRF four-layer guard) at sandbox **L3**. Tag chips: `github` · `search` · `finance` · `geo` · `weather`.

<div class="tcg-grid" markdown>

<div class="tcg-card t-github tcg-card--clickable" id="getGithubRepo" data-tool-id="getGithubRepo" data-tool-title="getGithubRepo" markdown>
<div class="tcg-name"><span class="tcg-name__text">getGithubRepo</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-github:</div>
<div class="tcg-type">web · github <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Fetches public metadata for a GitHub repository (no authentication needed; subject to GitHub's 60 requests/hour anonymous rate limit).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `owner` · `repo`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Returns: { fullName, description, stars, forks, openIssues, language, license, defaultBranch, lastPush, topics, homepage, htmlUrl }.

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `owner` | `STRING` | ✓ | GitHub user or org login (e.g. 'spring-projects') |
| `repo` | `STRING` | ✓ | Repository name (e.g. 'spring-ai') |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `api.github.com` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Fetches public metadata for a GitHub repository.
 *
 * No authentication needed; GitHub allows 60 requests per hour from
 * an anonymous client (rate-limit headers are returned in the response).
 *
 * On 404, returns { found: false, owner, repo } instead of throwing.
 */

const url = 'https://api.github.com/repos/'
  + encodeURIComponent(owner) + '/' + encodeURIComponent(repo);

const resp = await fetch(url, {
  headers: {
    'Accept': 'application/vnd.github+json',
    'User-Agent': 'spring-ai-playground',
  },
  maxLength: 1_000_000,
});

if (resp.status === 404) return { found: false, owner, repo };
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

const data = resp.json();
return {
  fullName:      data.full_name,
  description:   data.description,
  stars:         data.stargazers_count,
  forks:         data.forks_count,
  openIssues:    data.open_issues_count,
  language:      data.language,
  license:       data.license && data.license.spdx_id,
  defaultBranch: data.default_branch,
  lastPush:      data.pushed_at,
  topics:        data.topics || [],
  homepage:      data.homepage,
  htmlUrl:       data.html_url,
};

```

</details>

</div>
</div>

<div class="tcg-card t-wiki tcg-card--clickable" id="searchWikipedia" data-tool-id="searchWikipedia" data-tool-title="searchWikipedia" markdown>
<div class="tcg-name"><span class="tcg-name__text">searchWikipedia</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-wikipedia:</div>
<div class="tcg-type">web · search <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Looks up a Wikipedia page summary by title. No authentication required. Uses the public REST API at en.wikipedia.org/api/rest_v1/page/summary.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `title` · `lang`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Returns: { title, description, extract (plain-text summary), thumbnail, pageUrl }. If the title is not found, returns { found: false, title }.

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `title` | `STRING` | ✓ | Article title (case-insensitive, spaces ok) |
| `lang` | `STRING` |  | Language code (e.g. 'en', 'ko', default 'en') |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `en.wikipedia.org`, `ko.wikipedia.org`, `ja.wikipedia.org`, `es.wikipedia.org`, `de.wikipedia.org`, `fr.wikipedia.org`, `zh.wikipedia.org` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Looks up a Wikipedia article summary by title.
 *
 * Uses the public REST API:  https://{lang}.wikipedia.org/api/rest_v1/page/summary/{title}
 * No auth, generous rate limits, JSON response.
 *
 * `lang` is the wiki subdomain - 'en', 'ko', 'ja', etc. Defaults to 'en'.
 */

if (title == null || title === '') throw new Error('title required');
const language = (lang && lang !== '') ? lang : 'en';

const url = 'https://' + language + '.wikipedia.org/api/rest_v1/page/summary/'
  + encodeURIComponent(String(title).replace(/ /g, '_'));

const resp = await fetch(url, {
  headers: { 'Accept': 'application/json', 'User-Agent': 'spring-ai-playground' },
  maxLength: 1_000_000,
});

if (resp.status === 404) return { found: false, title };
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

const data = resp.json();
return {
  title:       data.title,
  description: data.description,
  extract:     data.extract,
  thumbnail:   data.thumbnail && data.thumbnail.source,
  pageUrl:     data.content_urls && data.content_urls.desktop && data.content_urls.desktop.page,
};

```

</details>

</div>
</div>

<div class="tcg-card t-hn tcg-card--clickable" id="searchHackerNews" data-tool-id="searchHackerNews" data-tool-title="searchHackerNews" markdown>
<div class="tcg-name"><span class="tcg-name__text">searchHackerNews</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-ycombinator:</div>
<div class="tcg-type">web · search <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Searches Hacker News stories via the public Algolia HN Search API (no auth needed).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `query` · `hits` · `tag`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Returns up to `hits` results, each as: { id, title, url, points, author, commentCount, createdAt, hnLink }.

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `query` | `STRING` | ✓ | Search query string |
| `hits` | `INTEGER` |  | Max results to return (1-20, default 5) |
| `tag` | `STRING` |  | HN tag filter: story \| comment \| poll \| etc (optional) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `hn.algolia.com` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Searches Hacker News via the Algolia HN Search API.
 *
 * Endpoint:  https://hn.algolia.com/api/v1/search?query=...&hitsPerPage=...&tags=...
 *
 * Available tags: story, comment, poll, pollopt, show_hn, ask_hn, front_page.
 * No authentication, public rate limit.
 */

if (query == null || query === '') throw new Error('query required');
const n = (Number.isInteger(hits) && hits > 0 && hits <= 20) ? hits : 5;

const params = new URLSearchParams();
params.set('query', String(query));
params.set('hitsPerPage', String(n));
if (tag && tag !== '') params.set('tags', String(tag));

const resp = await fetch('https://hn.algolia.com/api/v1/search?' + params.toString(), {
  maxLength: 2_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

const data = resp.json();
return (data.hits || []).map(h => ({
  id:           h.objectID,
  title:        h.title || h.story_title,
  url:          h.url || h.story_url,
  points:       h.points,
  author:       h.author,
  commentCount: h.num_comments,
  createdAt:    h.created_at,
  hnLink:       'https://news.ycombinator.com/item?id=' + h.objectID,
}));

```

</details>

</div>
</div>

<div class="tcg-card t-stack tcg-card--clickable" id="searchStackOverflow" data-tool-id="searchStackOverflow" data-tool-title="searchStackOverflow" markdown>
<div class="tcg-name"><span class="tcg-name__text">searchStackOverflow</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-stackoverflow:</div>
<div class="tcg-type">web · search <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Searches Stack Overflow questions via the public Stack Exchange API (anonymous, capped at 300 requests / IP / day).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `query` · `pageSize` · `sort` · `tags`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Returns up to `pageSize` results sorted by `sort` (relevance | activity | votes | creation), each as: { id, title, score, answerCount, isAnswered, tags, link, createdAt, owner }.

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `query` | `STRING` | ✓ | Search text (intitle) |
| `pageSize` | `INTEGER` |  | Max results (1-30, default 5) |
| `sort` | `STRING` |  | relevance \| activity \| votes \| creation |
| `tags` | `STRING` |  | Semicolon-separated tag filter (e.g. 'java;spring') |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `api.stackexchange.com` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Searches Stack Overflow questions via the Stack Exchange API.
 *
 * Endpoint: https://api.stackexchange.com/2.3/search/advanced
 *
 * Anonymous quota: 300 requests / IP / day. Returns paginated results
 * (this tool returns the first page only - adjust `pageSize` up to 30).
 */

if (query == null || query === '') throw new Error('query required');
const n = (Number.isInteger(pageSize) && pageSize > 0 && pageSize <= 30) ? pageSize : 5;
const orderBy = (sort === 'activity' || sort === 'votes' || sort === 'creation') ? sort : 'relevance';

const params = new URLSearchParams();
params.set('order', 'desc');
params.set('sort', orderBy);
params.set('q', String(query));
params.set('site', 'stackoverflow');
params.set('pagesize', String(n));
if (tags && tags !== '') params.set('tagged', String(tags));

const resp = await fetch('https://api.stackexchange.com/2.3/search/advanced?' + params.toString(), {
  headers: { 'Accept': 'application/json' },
  maxLength: 2_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

const data = resp.json();
return (data.items || []).map(q => ({
  id:          q.question_id,
  title:       q.title,
  score:       q.score,
  answerCount: q.answer_count,
  isAnswered:  q.is_answered,
  tags:        q.tags || [],
  link:        q.link,
  createdAt:   q.creation_date,
  owner:       q.owner && q.owner.display_name,
}));

```

</details>

</div>
</div>

<div class="tcg-card t-github tcg-card--clickable" id="getGithubUser" data-tool-id="getGithubUser" data-tool-title="getGithubUser" markdown>
<div class="tcg-name"><span class="tcg-name__text">getGithubUser</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-github:</div>
<div class="tcg-type">web · github <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Fetches public profile information for a GitHub user or organisation (no auth - 60 req/h anonymous).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `login`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Returns: { login, type, name, company, blog, location, bio, publicRepos, publicGists, followers, following, createdAt, htmlUrl, avatarUrl }.

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `login` | `STRING` | ✓ | GitHub user or org login |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `api.github.com` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Fetches GitHub public user / organisation profile.
 * Endpoint: GET https://api.github.com/users/{login}
 */

if (login == null || login === '') throw new Error('login required');
const resp = await fetch('https://api.github.com/users/' + encodeURIComponent(login), {
  headers: { 'Accept': 'application/vnd.github+json', 'User-Agent': 'spring-ai-playground' },
  maxLength: 1_000_000,
});
if (resp.status === 404) return { found: false, login };
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };
const d = resp.json();
return {
  login:       d.login,
  type:        d.type,
  name:        d.name,
  company:     d.company,
  blog:        d.blog,
  location:    d.location,
  bio:         d.bio,
  publicRepos: d.public_repos,
  publicGists: d.public_gists,
  followers:   d.followers,
  following:   d.following,
  createdAt:   d.created_at,
  htmlUrl:     d.html_url,
  avatarUrl:   d.avatar_url,
};

```

</details>

</div>
</div>

<div class="tcg-card t-github tcg-card--clickable" id="listGithubRepoIssues" data-tool-id="listGithubRepoIssues" data-tool-title="listGithubRepoIssues" markdown>
<div class="tcg-name"><span class="tcg-name__text">listGithubRepoIssues</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-github:</div>
<div class="tcg-type">web · github <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Lists issues on a public GitHub repository (no auth). Excludes pull requests by default. Anonymous quota 60 req/h.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `owner` · `repo` · `state` · `perPage` · `page`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Returns up to `perPage` issues, each as: { number, title, state, author, labels, commentCount, createdAt, updatedAt, htmlUrl }.

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `owner` | `STRING` | ✓ | Repo owner |
| `repo` | `STRING` | ✓ | Repo name |
| `state` | `STRING` |  | open \| closed \| all |
| `perPage` | `INTEGER` |  | Max issues per page (1-100, default 10) |
| `page` | `INTEGER` |  | Page number (1-based, default 1) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `api.github.com` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Lists issues on a public GitHub repository.
 * Endpoint: GET https://api.github.com/repos/{owner}/{repo}/issues
 *
 * Pull requests are filtered out client-side (the API includes them in /issues
 * by default and they're indistinguishable except for the `pull_request` key).
 */

if (owner == null || owner === '') throw new Error('owner required');
if (repo  == null || repo  === '') throw new Error('repo required');
const n  = (Number.isInteger(perPage) && perPage > 0 && perPage <= 100) ? perPage : 10;
const pg = (Number.isInteger(page) && page > 0) ? page : 1;
const st = (state === 'closed' || state === 'all') ? state : 'open';

const url = 'https://api.github.com/repos/' + encodeURIComponent(owner) + '/' + encodeURIComponent(repo)
  + '/issues?state=' + st + '&per_page=' + n + '&page=' + pg;
const resp = await fetch(url, {
  headers: { 'Accept': 'application/vnd.github+json', 'User-Agent': 'spring-ai-playground' },
  maxLength: 5_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };
const issues = resp.json();
return (issues || []).filter(i => !i.pull_request).map(i => ({
  number:       i.number,
  title:        i.title,
  state:        i.state,
  author:       i.user && i.user.login,
  labels:       (i.labels || []).map(l => typeof l === 'string' ? l : l.name),
  commentCount: i.comments,
  createdAt:    i.created_at,
  updatedAt:    i.updated_at,
  htmlUrl:      i.html_url,
}));

```

</details>

</div>
</div>

<div class="tcg-card t-github tcg-card--clickable" id="listGithubRepoReleases" data-tool-id="listGithubRepoReleases" data-tool-title="listGithubRepoReleases" markdown>
<div class="tcg-name"><span class="tcg-name__text">listGithubRepoReleases</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-github:</div>
<div class="tcg-type">web · github <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Lists releases on a public GitHub repository (no auth).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `owner` · `repo` · `perPage`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Returns: [{ tag, name, draft, prerelease, publishedAt, htmlUrl, body }].

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `owner` | `STRING` | ✓ | Repo owner |
| `repo` | `STRING` | ✓ | Repo name |
| `perPage` | `INTEGER` |  | Max releases (1-30, default 5) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `api.github.com` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Lists releases on a public GitHub repository.
 * Endpoint: GET https://api.github.com/repos/{owner}/{repo}/releases
 */

if (owner == null || owner === '') throw new Error('owner required');
if (repo  == null || repo  === '') throw new Error('repo required');
const n = (Number.isInteger(perPage) && perPage > 0 && perPage <= 30) ? perPage : 5;

const url = 'https://api.github.com/repos/' + encodeURIComponent(owner) + '/' + encodeURIComponent(repo)
  + '/releases?per_page=' + n;
const resp = await fetch(url, {
  headers: { 'Accept': 'application/vnd.github+json', 'User-Agent': 'spring-ai-playground' },
  maxLength: 5_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };
return (resp.json() || []).map(r => ({
  tag:         r.tag_name,
  name:        r.name,
  draft:       r.draft,
  prerelease:  r.prerelease,
  publishedAt: r.published_at,
  htmlUrl:     r.html_url,
  body:        r.body,
}));

```

</details>

</div>
</div>

<div class="tcg-card t-github tcg-card--clickable" id="getGithubLatestRelease" data-tool-id="getGithubLatestRelease" data-tool-title="getGithubLatestRelease" markdown>
<div class="tcg-name"><span class="tcg-name__text">getGithubLatestRelease</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-github:</div>
<div class="tcg-type">web · github <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Fetches the latest non-draft, non-prerelease release of a public GitHub repository (no auth).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `owner` · `repo`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Returns: { tag, name, publishedAt, htmlUrl, body, assets: [{ name, downloadUrl, size }] }. 404 → { found: false, owner, repo }.

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `owner` | `STRING` | ✓ | Repo owner |
| `repo` | `STRING` | ✓ | Repo name |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `api.github.com` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Fetches the latest stable release of a public GitHub repository.
 * Endpoint: GET https://api.github.com/repos/{owner}/{repo}/releases/latest
 */

if (owner == null || owner === '') throw new Error('owner required');
if (repo  == null || repo  === '') throw new Error('repo required');

const url = 'https://api.github.com/repos/' + encodeURIComponent(owner) + '/' + encodeURIComponent(repo)
  + '/releases/latest';
const resp = await fetch(url, {
  headers: { 'Accept': 'application/vnd.github+json', 'User-Agent': 'spring-ai-playground' },
  maxLength: 2_000_000,
});
if (resp.status === 404) return { found: false, owner, repo };
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };
const r = resp.json();
return {
  tag:         r.tag_name,
  name:        r.name,
  publishedAt: r.published_at,
  htmlUrl:     r.html_url,
  body:        r.body,
  assets:      (r.assets || []).map(a => ({
    name: a.name, downloadUrl: a.browser_download_url, size: a.size,
  })),
};

```

</details>

</div>
</div>

<div class="tcg-card t-github tcg-card--clickable" id="getGithubFileContent" data-tool-id="getGithubFileContent" data-tool-title="getGithubFileContent" markdown>
<div class="tcg-name"><span class="tcg-name__text">getGithubFileContent</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-github:</div>
<div class="tcg-type">web · github <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Fetches the raw text content of a file from a public GitHub repository (no auth).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `owner` · `repo` · `path` · `ref`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

For directories this returns a listing instead: [{ name, type, path }]. Files over ~1 MB are not supported by this endpoint and return success=false.

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `owner` | `STRING` | ✓ | Repo owner |
| `repo` | `STRING` | ✓ | Repo name |
| `path` | `STRING` | ✓ | Path inside the repo (e.g. 'README.adoc') |
| `ref` | `STRING` |  | Branch / tag / commit SHA (default: repo's default branch) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `api.github.com` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Fetches a file (or directory listing) from a public GitHub repository.
 * Endpoint: GET https://api.github.com/repos/{owner}/{repo}/contents/{path}?ref={ref}
 *
 * File response: base64-encoded content + sha + size. We decode to UTF-8 text.
 * Directory response: array of entries; we return a slimmed listing.
 */

if (owner == null || owner === '') throw new Error('owner required');
if (repo  == null || repo  === '') throw new Error('repo required');
if (path  == null || path  === '') throw new Error('path required');

const refQs = (ref && ref !== '') ? ('?ref=' + encodeURIComponent(ref)) : '';
const segments = String(path).split('/').filter(Boolean).map(encodeURIComponent).join('/');
const url = 'https://api.github.com/repos/' + encodeURIComponent(owner) + '/' + encodeURIComponent(repo)
  + '/contents/' + segments + refQs;
const resp = await fetch(url, {
  headers: { 'Accept': 'application/vnd.github+json', 'User-Agent': 'spring-ai-playground' },
  maxLength: 5_000_000,
});
if (resp.status === 404) return { found: false, owner, repo, path };
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };
const data = resp.json();
if (Array.isArray(data)) {
  // Directory listing
  return data.map(e => ({ name: e.name, type: e.type, path: e.path, size: e.size }));
}
// File - decode base64 content. GitHub wraps lines every 60 chars; strip whitespace first.
if (data.encoding !== 'base64' || data.type !== 'file') {
  return { found: true, type: data.type, name: data.name, path: data.path, size: data.size,
           note: 'unsupported content; use a smaller file or different endpoint' };
}
const b64 = String(data.content || '').replace(/\s+/g, '');
const bin = atob(b64);
const bytes = new Uint8Array(bin.length);
for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
const text = new TextDecoder('utf-8', { fatal: false }).decode(bytes);
return { name: data.name, path: data.path, size: data.size, sha: data.sha,
         encoding: 'utf-8', content: text };

```

</details>

</div>
</div>

<div class="tcg-card t-github tcg-card--clickable" id="searchGithubRepos" data-tool-id="searchGithubRepos" data-tool-title="searchGithubRepos" markdown>
<div class="tcg-name"><span class="tcg-name__text">searchGithubRepos</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-github:</div>
<div class="tcg-type">web · github · search <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Searches public GitHub repositories by query (no auth - anonymous limit 10 requests/minute).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `query` · `sort` · `perPage`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Returns up to `perPage` results: [{ fullName, description, stars, forks, language, htmlUrl, updatedAt }].

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `query` | `STRING` | ✓ | GitHub search query (e.g. 'spring-ai language:java') |
| `sort` | `STRING` |  | stars \| forks \| updated \| best-match (default) |
| `perPage` | `INTEGER` |  | Max results (1-30, default 5) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `api.github.com` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Searches public GitHub repositories.
 * Endpoint: GET https://api.github.com/search/repositories?q=...&sort=...&per_page=...
 *
 * Anonymous rate limit: 10 requests / minute / IP.
 */

if (query == null || query === '') throw new Error('query required');
const n = (Number.isInteger(perPage) && perPage > 0 && perPage <= 30) ? perPage : 5;
const sortValid = (sort === 'stars' || sort === 'forks' || sort === 'updated');
const sortQs = sortValid ? ('&sort=' + sort + '&order=desc') : '';

const url = 'https://api.github.com/search/repositories?q=' + encodeURIComponent(query)
  + '&per_page=' + n + sortQs;
const resp = await fetch(url, {
  headers: { 'Accept': 'application/vnd.github+json', 'User-Agent': 'spring-ai-playground' },
  maxLength: 5_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };
const data = resp.json();
return (data.items || []).map(r => ({
  fullName:    r.full_name,
  description: r.description,
  stars:       r.stargazers_count,
  forks:       r.forks_count,
  language:    r.language,
  htmlUrl:     r.html_url,
  updatedAt:   r.updated_at,
}));

```

</details>

</div>
</div>

<div class="tcg-card t-github tcg-card--clickable" id="listGithubRepoContributors" data-tool-id="listGithubRepoContributors" data-tool-title="listGithubRepoContributors" markdown>
<div class="tcg-name"><span class="tcg-name__text">listGithubRepoContributors</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-github:</div>
<div class="tcg-type">web · github <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Lists top contributors to a public GitHub repository (no auth).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `owner` · `repo` · `perPage`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Returns: [{ login, contributions, htmlUrl, avatarUrl }] sorted by commit count desc.

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `owner` | `STRING` | ✓ | Repo owner |
| `repo` | `STRING` | ✓ | Repo name |
| `perPage` | `INTEGER` |  | Max contributors (1-100, default 10) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `api.github.com` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Lists top contributors to a public GitHub repository.
 * Endpoint: GET https://api.github.com/repos/{owner}/{repo}/contributors
 */

if (owner == null || owner === '') throw new Error('owner required');
if (repo  == null || repo  === '') throw new Error('repo required');
const n = (Number.isInteger(perPage) && perPage > 0 && perPage <= 100) ? perPage : 10;

const url = 'https://api.github.com/repos/' + encodeURIComponent(owner) + '/' + encodeURIComponent(repo)
  + '/contributors?per_page=' + n;
const resp = await fetch(url, {
  headers: { 'Accept': 'application/vnd.github+json', 'User-Agent': 'spring-ai-playground' },
  maxLength: 2_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };
return (resp.json() || []).map(c => ({
  login:         c.login,
  contributions: c.contributions,
  htmlUrl:       c.html_url,
  avatarUrl:     c.avatar_url,
}));

```

</details>

</div>
</div>

<div class="tcg-card t-crypto tcg-card--clickable" id="getCryptoPrice" data-tool-id="getCryptoPrice" data-tool-title="getCryptoPrice" markdown>
<div class="tcg-name"><span class="tcg-name__text">getCryptoPrice</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-currency-btc:</div>
<div class="tcg-type">web · finance <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Fetches current crypto prices from CoinGecko's public Simple Price API (no auth, generous rate limit). Pass coin ids like 'bitcoin,ethereum' and currency ids like 'usd,krw'.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `ids` · `currencies`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Returns: { <coinId>: { <currency>: price, ... }, ... }

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `ids` | `STRING` | ✓ | Comma-separated CoinGecko coin ids |
| `currencies` | `STRING` |  | Comma-separated target currencies (e.g. usd, krw) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `api.coingecko.com` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * CoinGecko public Simple Price endpoint.
 *
 * GET https://api.coingecko.com/api/v3/simple/price?ids={ids}&vs_currencies={currencies}
 *
 * No authentication required for the public tier (~10-30 req/min/IP).
 * Returns a flat map: {bitcoin:{usd:62000, krw:84000000}, ethereum:{usd:...}}.
 */

if (ids == null || ids === '') throw new Error('ids required');
const vs = (currencies && currencies !== '') ? currencies : 'usd';

const url = 'https://api.coingecko.com/api/v3/simple/price'
  + '?ids=' + encodeURIComponent(ids)
  + '&vs_currencies=' + encodeURIComponent(vs);

const resp = await fetch(url, {
  headers: { 'Accept': 'application/json', 'User-Agent': 'spring-ai-playground' },
  maxLength: 500_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };
return resp.json();

```

</details>

</div>
</div>

<div class="tcg-card t-currency tcg-card--clickable" id="convertCurrency" data-tool-id="convertCurrency" data-tool-title="convertCurrency" markdown>
<div class="tcg-name"><span class="tcg-name__text">convertCurrency</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-currency-usd:</div>
<div class="tcg-type">web · finance <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Converts between fiat currencies using open.er-api.com daily reference rates (no key).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `from` · `to` · `amount`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Returns: { from, to, amount, rate, result, date }.

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `from` | `STRING` | ✓ | Source currency code (ISO 4217, e.g. USD) |
| `to` | `STRING` | ✓ | Target currency code (ISO 4217, e.g. KRW) |
| `amount` | `NUMBER` |  | Amount in the source currency (default 1) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `open.er-api.com` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Currency conversion via the open.er-api.com daily rates API (no key).
 * (api.exchangerate.host started requiring an access key in 2026, so the tool moved off it.)
 *
 * GET https://open.er-api.com/v6/latest/{from}
 *
 * One request fetches every rate for the source currency; the conversion is computed locally.
 */

if (from == null || from === '') throw new Error('from required');
if (to   == null || to   === '') throw new Error('to required');
const amt = (amount == null || amount === '') ? 1 : Number(amount);
if (!Number.isFinite(amt)) throw new Error('amount must be a finite number');

const base  = String(from).trim().toUpperCase();
const quote = String(to).trim().toUpperCase();

const resp = await fetch('https://open.er-api.com/v6/latest/' + encodeURIComponent(base), {
  headers: { 'Accept': 'application/json' },
  maxLength: 200_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };
const d = resp.json();
if (d.result !== 'success') return { success: false, message: d['error-type'] || 'unexpected response' };
const rate = d.rates && d.rates[quote];
if (rate == null) throw new Error('unknown currency code: ' + quote);
return {
  from:   base,
  to:     quote,
  amount: amt,
  rate:   rate,
  result: amt * rate,
  date:   d.time_last_update_utc,
};

```

</details>

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="getIpInfo" data-tool-id="getIpInfo" data-tool-title="getIpInfo" markdown>
<div class="tcg-name"><span class="tcg-name__text">getIpInfo</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-ip-network-outline:</div>
<div class="tcg-type">web · geo <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Returns geolocation and ASN info for an IP address (or the caller's IP if `ip` is omitted) via ipapi.co (no auth, 1000 req/day).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `ip`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Returns: { ip, city, region, country, countryName, latitude, longitude, timezone, asn, org, isp }.

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `ip` | `STRING` |  | IPv4 / IPv6 address (omit for caller's own IP) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `ipapi.co` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * IP geolocation lookup via ipapi.co (1000 free req/day, no key).
 *
 * GET https://ipapi.co/{ip}/json/
 *
 * If `ip` is omitted, ipapi resolves the caller's IP - useful for sanity-checking
 * what the playground's outbound IP looks like to the rest of the internet.
 */

const path = (ip && ip !== '') ? (encodeURIComponent(ip) + '/json/') : 'json/';
const resp = await fetch('https://ipapi.co/' + path, {
  headers: { 'Accept': 'application/json', 'User-Agent': 'spring-ai-playground' },
  maxLength: 500_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };
const d = resp.json();
return {
  ip:          d.ip,
  city:        d.city,
  region:      d.region,
  country:     d.country_code,
  countryName: d.country_name,
  latitude:    d.latitude,
  longitude:   d.longitude,
  timezone:    d.timezone,
  asn:         d.asn,
  org:         d.org,
  isp:         d.org,
};

```

</details>

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="getCountryInfo" data-tool-id="getCountryInfo" data-tool-title="getCountryInfo" markdown>
<div class="tcg-name"><span class="tcg-name__text">getCountryInfo</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-earth:</div>
<div class="tcg-type">web · geo <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Fetches country information from the open mledoze/countries dataset (via the jsDelivr CDN, no key) by partial or full name.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `name`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Returns an array of matches (capped at 10), each: { name, officialName, capital, region, subregion, area, languages, currencies, callingCode, flagEmoji, latlng }.

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `name` | `STRING` | ✓ | Country name (partial match - e.g. 'korea', 'germany') |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `cdn.jsdelivr.net` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Country metadata from the open mledoze/countries dataset, served by the jsDelivr CDN (no key).
 * (restcountries.com moved behind API keys in 2026, so the tool ships its own open data source.)
 *
 * GET https://cdn.jsdelivr.net/gh/mledoze/countries@master/countries.json
 *
 * Partial matches are supported (e.g. "korea" returns both Koreas); results are capped at 10.
 */

if (name == null || name === '') throw new Error('name required');
const resp = await fetch('https://cdn.jsdelivr.net/gh/mledoze/countries@master/countries.json', {
  headers: { 'Accept': 'application/json' },
  maxLength: 5_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

const needle = String(name).trim().toLowerCase();
const matches = (resp.json() || []).filter(c => {
  const common   = (c.name && c.name.common)   || '';
  const official = (c.name && c.name.official) || '';
  return common.toLowerCase().includes(needle) || official.toLowerCase().includes(needle);
});

return matches.slice(0, 10).map(c => ({
  name:         c.name && c.name.common,
  officialName: c.name && c.name.official,
  capital:      c.capital,
  region:       c.region,
  subregion:    c.subregion,
  area:         c.area,
  languages:    c.languages ? Object.values(c.languages) : [],
  currencies:   c.currencies ? Object.keys(c.currencies) : [],
  callingCode:  (c.idd && c.idd.root)
                  ? c.idd.root + ((c.idd.suffixes && c.idd.suffixes[0]) || '')
                  : null,
  flagEmoji:    c.flag,
  latlng:       c.latlng,
}));

```

</details>

</div>
</div>

<div class="tcg-card t-arxiv tcg-card--clickable" id="searchArxiv" data-tool-id="searchArxiv" data-tool-title="searchArxiv" markdown>
<div class="tcg-name"><span class="tcg-name__text">searchArxiv</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-arxiv:</div>
<div class="tcg-type">web · search <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Searches arXiv preprints via the public Atom-feed API (no auth). Results are parsed from XML.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `query` · `max` · `sortBy`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Returns up to `max` entries, each: { id, title, summary, authors, published, updated, primaryCategory, pdfUrl, abstractUrl }.

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `query` | `STRING` | ✓ | Search query (arXiv search_query syntax, e.g. 'all:transformer') |
| `max` | `INTEGER` |  | Max results (1-50, default 5) |
| `sortBy` | `STRING` |  | relevance \| lastUpdatedDate \| submittedDate |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `export.arxiv.org` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * arXiv search via the public Atom-feed API.
 *
 * GET http://export.arxiv.org/api/query?search_query={query}&max_results={n}
 *
 * The response is XML (Atom). We parse it via safety.parser.xml and project
 * each <entry> to a compact object.
 */

if (query == null || query === '') throw new Error('query required');
const n  = (Number.isInteger(max) && max > 0 && max <= 50) ? max : 5;
const so = (sortBy === 'lastUpdatedDate' || sortBy === 'submittedDate') ? sortBy : 'relevance';

const url = 'http://export.arxiv.org/api/query'
  + '?search_query=' + encodeURIComponent(query)
  + '&max_results=' + n
  + '&sortBy=' + so + '&sortOrder=descending';

const resp = await fetch(url, {
  headers: { 'Accept': 'application/atom+xml' },
  maxLength: 5_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

const root = safety.parser.xml(resp.text());

// `root.children` is a list of {tag, attrs, text, children}. We pick <entry> nodes.
function pickChild(node, tag) {
  for (const c of node.children || []) if (c.tag === tag) return c;
  return null;
}
function pickAllChildren(node, tag) {
  const out = [];
  for (const c of node.children || []) if (c.tag === tag) out.push(c);
  return out;
}

const entries = pickAllChildren(root, 'entry');
return entries.map(e => {
  const idNode      = pickChild(e, 'id');
  const titleNode   = pickChild(e, 'title');
  const summaryNode = pickChild(e, 'summary');
  const pubNode     = pickChild(e, 'published');
  const updNode     = pickChild(e, 'updated');
  const catNode     = pickChild(e, 'primary_category') || pickChild(e, 'category');
  const links       = pickAllChildren(e, 'link');
  const authors     = pickAllChildren(e, 'author').map(a => {
    const n = pickChild(a, 'name'); return n ? n.text : null;
  }).filter(Boolean);
  let pdfUrl = null, abs = null;
  for (const l of links) {
    const attrs = l.attrs || {};
    if (attrs.title === 'pdf') pdfUrl = attrs.href;
    if (attrs.rel === 'alternate') abs = attrs.href;
  }
  return {
    id:              idNode && idNode.text,
    title:           titleNode && titleNode.text.replace(/\s+/g, ' ').trim(),
    summary:         summaryNode && summaryNode.text.replace(/\s+/g, ' ').trim(),
    authors,
    published:       pubNode && pubNode.text,
    updated:         updNode && updNode.text,
    primaryCategory: catNode && catNode.attrs && catNode.attrs.term,
    pdfUrl,
    abstractUrl:     abs,
  };
});

```

</details>

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="getPublicHolidays" data-tool-id="getPublicHolidays" data-tool-title="getPublicHolidays" markdown>
<div class="tcg-name"><span class="tcg-name__text">getPublicHolidays</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-calendar-star-outline:</div>
<div class="tcg-type">web <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Returns public holidays for a given country and year via Nager.Date (no auth).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `year` · `countryCode`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Returns: [{ date, localName, name, fixed, global, types }]. Country codes are 2-letter ISO 3166-1 alpha-2 (KR / US / JP / GB / DE / FR / IT / ES / CN / ...).

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `year` | `INTEGER` | ✓ | Calendar year (e.g. 2026) |
| `countryCode` | `STRING` |  | 2-letter ISO country code (default 'KR') |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `date.nager.at` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Public holidays via Nager.Date (no auth, free).
 *
 * GET https://date.nager.at/api/v3/PublicHolidays/{year}/{countryCode}
 *
 * Supports 100+ countries. ISO 3166-1 alpha-2 country codes.
 */

if (year == null) throw new Error('year required');
const y = Number(year);
if (!Number.isInteger(y) || y < 1900 || y > 2100) throw new Error('year out of range');
const cc = (countryCode && countryCode !== '') ? String(countryCode).toUpperCase() : 'KR';

const url = 'https://date.nager.at/api/v3/PublicHolidays/' + y + '/' + encodeURIComponent(cc);
const resp = await fetch(url, {
  headers: { 'Accept': 'application/json' },
  maxLength: 1_000_000,
});
if (resp.status === 404) return [];
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };
return (resp.json() || []).map(h => ({
  date:      h.date,
  localName: h.localName,
  name:      h.name,
  fixed:     h.fixed,
  global:    h.global,
  types:     h.types || [],
}));

```

</details>

</div>
</div>

<div class="tcg-card t-meteo tcg-card--clickable" id="getOpenMeteoForecast" data-tool-id="getOpenMeteoForecast" data-tool-title="getOpenMeteoForecast" markdown>
<div class="tcg-name"><span class="tcg-name__text">getOpenMeteoForecast</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-weather-cloudy-clock:</div>
<div class="tcg-type">web · weather <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Fetches a multi-day weather forecast from Open-Meteo (no auth, 10k req/day for non-commercial). Open-Meteo serves official ECMWF/GFS/ICON model output - far richer than wttr.in but requires lat/lon (use `geocodeAddress` first if you only have a city name).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `latitude` · `longitude` · `days` · `timezone`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Returns: { latitude, longitude, timezone, daily: { time, temperatureMax, temperatureMin, precipitationSum, windSpeedMax }, hourly: { time[24], temperature[24], precipitationProbability[24] } } (first 24 h trimmed).

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `latitude` | `NUMBER` | ✓ | Latitude (e.g. 37.5665 for Seoul) |
| `longitude` | `NUMBER` | ✓ | Longitude (e.g. 126.9780 for Seoul) |
| `days` | `INTEGER` |  | Forecast days (1-16, default 3) |
| `timezone` | `STRING` |  | IANA tz (default 'auto') |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `api.open-meteo.com` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Open-Meteo multi-day forecast.
 *
 * GET https://api.open-meteo.com/v1/forecast?latitude=..&longitude=..&forecast_days=..
 *     &daily=temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max
 *     &hourly=temperature_2m,precipitation_probability
 *     &timezone=...
 *
 * Lat/lon ranges: latitude ∈ [-90, 90], longitude ∈ [-180, 180].
 * The hourly arrays are trimmed to the first 24 h to keep payloads small.
 */

if (latitude == null)  throw new Error('latitude required');
if (longitude == null) throw new Error('longitude required');
const lat = Number(latitude), lon = Number(longitude);
if (!Number.isFinite(lat) || lat < -90 || lat > 90)   throw new Error('latitude out of range');
if (!Number.isFinite(lon) || lon < -180 || lon > 180) throw new Error('longitude out of range');
const d  = (Number.isInteger(days) && days > 0 && days <= 16) ? days : 3;
const tz = (timezone && timezone !== '') ? timezone : 'auto';

const url = 'https://api.open-meteo.com/v1/forecast'
  + '?latitude=' + lat + '&longitude=' + lon
  + '&forecast_days=' + d + '&timezone=' + encodeURIComponent(tz)
  + '&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max'
  + '&hourly=temperature_2m,precipitation_probability';

const resp = await fetch(url, {
  headers: { 'Accept': 'application/json' },
  maxLength: 5_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };
const j = resp.json();

const daily  = j.daily  || {};
const hourly = j.hourly || {};
function trim(arr) { return Array.isArray(arr) ? arr.slice(0, 24) : []; }

return {
  latitude:  j.latitude,
  longitude: j.longitude,
  timezone:  j.timezone,
  daily: {
    time:             daily.time,
    temperatureMax:   daily.temperature_2m_max,
    temperatureMin:   daily.temperature_2m_min,
    precipitationSum: daily.precipitation_sum,
    windSpeedMax:     daily.wind_speed_10m_max,
  },
  hourly: {
    time:                     trim(hourly.time),
    temperature:              trim(hourly.temperature_2m),
    precipitationProbability: trim(hourly.precipitation_probability),
  },
};

```

</details>

</div>
</div>

<div class="tcg-card t-osm tcg-card--clickable" id="geocodeAddress" data-tool-id="geocodeAddress" data-tool-title="geocodeAddress" markdown>
<div class="tcg-name"><span class="tcg-name__text">geocodeAddress</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-openstreetmap:</div>
<div class="tcg-type">web · geo <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Forward-geocodes a free-form address to coordinates via OpenStreetMap Nominatim (no key). Nominatim's usage policy requires a descriptive User-Agent and at most 1 req/s - we set both.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `address` · `limit`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Returns up to `limit` matches: [{ displayName, latitude, longitude, country, city, type, importance }].

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `address` | `STRING` | ✓ | Address / place text (e.g. 'Seoul, South Korea' or 'Eiffel Tower, Paris') |
| `limit` | `INTEGER` |  | Max matches (1-10, default 3) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `nominatim.openstreetmap.org` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Forward geocoding via OpenStreetMap Nominatim.
 *
 * GET https://nominatim.openstreetmap.org/search?q={address}&format=json&limit={n}&addressdetails=1
 *
 * Honour OSM's policy: descriptive User-Agent, polite cadence (1 req/s).
 */

if (address == null || address === '') throw new Error('address required');
const n = (Number.isInteger(limit) && limit > 0 && limit <= 10) ? limit : 3;

const url = 'https://nominatim.openstreetmap.org/search'
  + '?q=' + encodeURIComponent(address)
  + '&format=json&limit=' + n + '&addressdetails=1';

const resp = await fetch(url, {
  headers: { 'Accept': 'application/json',
             'User-Agent': 'spring-ai-playground/0.2 (contact: github.com/spring-projects/spring-ai-community)' },
  maxLength: 5_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };
return (resp.json() || []).map(r => ({
  displayName: r.display_name,
  latitude:    r.lat ? Number(r.lat) : null,
  longitude:   r.lon ? Number(r.lon) : null,
  country:     r.address && r.address.country,
  city:        r.address && (r.address.city || r.address.town || r.address.village),
  type:        r.type,
  importance:  r.importance,
}));

```

</details>

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="getSunriseSunset" data-tool-id="getSunriseSunset" data-tool-title="getSunriseSunset" markdown>
<div class="tcg-name"><span class="tcg-name__text">getSunriseSunset</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-weather-sunset:</div>
<div class="tcg-type">web · geo <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Returns sunrise / sunset / twilight times for a given lat-lon and date via sunrise-sunset.org (no auth).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `latitude` · `longitude` · `date` · `timezone`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Returns: { sunrise, sunset, solarNoon, dayLength, civilTwilightBegin, civilTwilightEnd } as ISO strings in the requested timezone.

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `latitude` | `NUMBER` | ✓ | Latitude in decimal degrees |
| `longitude` | `NUMBER` | ✓ | Longitude in decimal degrees |
| `date` | `STRING` |  | ISO date (YYYY-MM-DD), defaults to today |
| `timezone` | `STRING` |  | IANA tz for the response (default 'UTC') |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `api.sunrise-sunset.org` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * Sunrise / sunset times for a given location and date.
 *
 * GET https://api.sunrise-sunset.org/json?lat=..&lng=..&date=..&formatted=0
 *
 * Returns ISO timestamps (formatted=0 prevents the API from returning local
 * strings). We convert them into the requested IANA timezone.
 */

if (latitude == null || longitude == null) throw new Error('latitude/longitude required');
const lat = Number(latitude), lon = Number(longitude);
if (!Number.isFinite(lat) || !Number.isFinite(lon)) throw new Error('latitude/longitude must be numeric');
const d  = (date && date !== '') ? date : 'today';
const tz = (timezone && timezone !== '') ? timezone : 'UTC';

const url = 'https://api.sunrise-sunset.org/json'
  + '?lat=' + lat + '&lng=' + lon
  + '&date=' + encodeURIComponent(d)
  + '&formatted=0';
const resp = await fetch(url, {
  headers: { 'Accept': 'application/json' },
  maxLength: 200_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };
const j = resp.json();
if (j.status !== 'OK') return { success: false, status: j.status };

function inTz(iso) {
  if (!iso) return null;
  const dt = new Date(iso);
  const fmt = new Intl.DateTimeFormat('en-CA', {
    timeZone: tz,
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
    hour12: false,
  });
  const parts = {};
  for (const p of fmt.formatToParts(dt)) parts[p.type] = p.value;
  return parts.year + '-' + parts.month + '-' + parts.day
       + 'T' + (parts.hour === '24' ? '00' : parts.hour) + ':' + parts.minute + ':' + parts.second;
}

const r = j.results || {};
return {
  sunrise:             inTz(r.sunrise),
  sunset:              inTz(r.sunset),
  solarNoon:           inTz(r.solar_noon),
  dayLength:           r.day_length,
  civilTwilightBegin:  inTz(r.civil_twilight_begin),
  civilTwilightEnd:    inTz(r.civil_twilight_end),
  timezone:            tz,
};

```

</details>

</div>
</div>

<div class="tcg-card t-usgs tcg-card--clickable" id="getRecentEarthquakes" data-tool-id="getRecentEarthquakes" data-tool-title="getRecentEarthquakes" markdown>
<div class="tcg-name"><span class="tcg-name__text">getRecentEarthquakes</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-vibrate:</div>
<div class="tcg-type">web · geo <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Fetches recent earthquakes from the USGS public catalog (no auth).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `minMagnitude` · `lookbackHours` · `limit`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**More detail**

Returns up to `limit` events: [{ time, place, magnitude, type, latitude, longitude, depthKm, url, tsunami }] filtered by minimum magnitude and the past `lookbackHours` hours.

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `minMagnitude` | `NUMBER` |  | Minimum magnitude (default 4.5) |
| `lookbackHours` | `INTEGER` |  | Hours to look back (1-720, default 24) |
| `limit` | `INTEGER` |  | Max events (1-100, default 20) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `earthquake.usgs.gov` (SSRF-guarded); no filesystem.

<details class="tcg-sysprompt" markdown>
<summary>JS source</summary>

```javascript
/**
 * USGS Earthquake API (FDSN web service).
 *
 * GET https://earthquake.usgs.gov/fdsnws/event/1/query?
 *     format=geojson&starttime=...&minmagnitude=...&limit=...
 *
 * `time` field is epoch millis - we convert to ISO. Depth is in km already.
 */

const mm  = (minMagnitude  == null || minMagnitude  === '') ? 4.5 : Number(minMagnitude);
const lh  = (Number.isInteger(lookbackHours) && lookbackHours > 0 && lookbackHours <= 720) ? lookbackHours : 24;
const lim = (Number.isInteger(limit) && limit > 0 && limit <= 100) ? limit : 20;
if (!Number.isFinite(mm)) throw new Error('minMagnitude must be numeric');

const start = new Date(Date.now() - lh * 3_600_000).toISOString();
const url = 'https://earthquake.usgs.gov/fdsnws/event/1/query'
  + '?format=geojson&starttime=' + encodeURIComponent(start)
  + '&minmagnitude=' + mm
  + '&limit=' + lim
  + '&orderby=time';

const resp = await fetch(url, {
  headers: { 'Accept': 'application/json' },
  maxLength: 5_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };
const data = resp.json();

return (data.features || []).map(f => {
  const p = f.properties || {};
  const g = f.geometry && f.geometry.coordinates;
  return {
    time:      p.time ? new Date(p.time).toISOString() : null,
    place:     p.place,
    magnitude: p.mag,
    type:      p.magType,
    latitude:  g && g[1],
    longitude: g && g[0],
    depthKm:   g && g[2],
    url:       p.url,
    tsunami:   p.tsunami === 1,
  };
});

```

</details>

</div>
</div>

</div>

## Composition patterns (anonymous-API chains)

All 22 run anonymously off rate-limit tiers, so they are the cheapest tools to chain - no credential plumbing, just URL composition:

- **City → coordinates → forecast** - `geocodeAddress(address)` (Nominatim) → `getOpenMeteoForecast(lat, lon, days)` so the agent can answer "is it raining tomorrow in *city*?" without hard-coding lat/lon.
- **Release radar** - `getGithubLatestRelease(owner, repo)` → run `openaiResponseGenerator` over the `.body` to get a three-sentence release-notes digest.
- **Knowledge cross-check** - `searchWikipedia(title)` + `searchHackerNews(query)` + `searchStackOverflow(query)` in parallel; the agent reconciles the three views.
- **IP triage** - `getIpInfo(ip)` → branch on `country_code` / `org` → call `getRecentEarthquakes(lat, lon)` or `getOpenMeteoForecast(lat, lon)` for the same coordinates.
- **arXiv → summary** - `searchArxiv(query)` → top-N abstracts as prompt fragments → `openaiResponseGenerator` for a literature snapshot.

[Tutorial 8: Default Tool Recipes](../../tutorials/8-default-tool-recipes.md) walks the first two patterns (geo-anchored weather + release radar) end-to-end.

## Keys & secrets

**None.** All 22 endpoints are anonymous. Rate limits are the real constraint:

| Provider | Anonymous quota |
|---|---|
| GitHub (8 tools) | 60 req/h (`searchGithubRepos` further capped at 10 req/min) |
| Stack Exchange | 300 req / IP / day |
| ipapi.co | 1 000 req / day |
| Open-Meteo | 10 000 req / day (non-commercial) |
| Nominatim (OpenStreetMap) | 1 req / s + descriptive User-Agent required (both honoured by the helper) |
| Wikipedia / HN Algolia / arXiv / mledoze/countries / Nager.Date / USGS / open.er-api.com / CoinGecko / sunrise-sunset | generous (no published per-day cap) |

If you need higher quotas you can fork a tool and add a vendor key - the same `${ENV_VAR}` static-variable mechanism the [Examples](examples.md) tools use.

→ [Tool Studio: SSRF four-layer guard](../tool-studio/index.md#ssrf-four-layer-guard) - the network policy these tools run under.
