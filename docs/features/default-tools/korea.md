description: Default Tools - Korea reference. 21 Korea-locale tools - Upbit, Bithumb, Naver, Kakao, KMA, KOFIC, KRX, data.go.kr keychain.

# Default Tools - Korea

The 21 tools in `default-tool-specs-kr.json` are **Korea-locale services** - crypto exchanges (Upbit, Bithumb), search and local discovery (Naver, Kakao, K-pop iTunes, K-beauty, Korea Tourism), government open-data services (data.go.kr keychain, Seoul Open Data Plaza), and Korea-specific finance and disaster feeds (KAMIS, KOFIC, KRX, MOLIT, MFDS, MOIS).

Most return **Korean text in their response payloads** - names, addresses, codenames - so a chat agent calling them should be locale-aware. Eight are no-key (Upbit endpoints, Bithumb endpoints, iTunes K-pop, Open Beauty Facts), the other thirteen need provider-issued keys. Provider keys live in the tool's static variables as `${ENV_VAR}` placeholders that resolve at runtime from the JVM environment - they are not committed to the spec.

Like the global network tools, every fetch runs through [the SSRF four-layer guard](../tool-studio/index.md#ssrf-four-layer-guard) in the default host-`allowlist` egress mode.

## Browse the 21 services { #browse-the-services }

Crypto markets (6, no key) · Search & local (5, mixed) · Finance & data (4, mixed) · Weather & disaster (3, mixed) · Government dispatcher (2 + 1 generic). All run at sandbox **L3** with host-`allowlist` egress.

<div class="tcg-grid" markdown>

<div class="tcg-card t-crypto tcg-card--clickable" id="getUpbitTicker" data-tool-id="getUpbitTicker" data-tool-title="getUpbitTicker" markdown>
<div class="tcg-name"><span class="tcg-name__text">getUpbitTicker</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-currency-btc:</div>
<div class="tcg-type">web · korea · finance <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Fetches the current KRW ticker(s) from Upbit, a major Korean crypto exchange (no auth). `markets` is comma-separated, e.g. 'KRW-BTC,KRW-ETH'. Returns an array of { market, tradePrice, openingPrice, highPrice, lowPrice, change, changeRate, accTradeVolume24h, timestamp }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `markets`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `markets` | `STRING` | ✓ | Comma-separated Upbit market codes |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `api.upbit.com` (SSRF-guarded); no filesystem.

**JS source**

```javascript
/**
 * Upbit Korean exchange ticker (no auth).
 *
 * GET https://api.upbit.com/v1/ticker?markets=KRW-BTC,KRW-ETH
 *
 * Upbit denominates KRW pairs natively, so this is the easiest way to fetch
 * Korean-won-denominated crypto prices.
 */

if (markets == null || markets === '') throw new Error('markets required');
const url = 'https://api.upbit.com/v1/ticker?markets=' + encodeURIComponent(markets);

const resp = await fetch(url, {
  headers: { 'Accept': 'application/json' },
  maxLength: 1_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

return (resp.json() || []).map(t => ({
  market:            t.market,
  tradePrice:        t.trade_price,
  openingPrice:      t.opening_price,
  highPrice:         t.high_price,
  lowPrice:          t.low_price,
  change:            t.change,
  changeRate:        t.change_rate,
  accTradeVolume24h: t.acc_trade_volume_24h,
  timestamp:         t.timestamp,
}));

```

</div>
</div>

<div class="tcg-card t-crypto tcg-card--clickable" id="getUpbitOrderbook" data-tool-id="getUpbitOrderbook" data-tool-title="getUpbitOrderbook" markdown>
<div class="tcg-name"><span class="tcg-name__text">getUpbitOrderbook</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-chart-box-outline:</div>
<div class="tcg-type">web · korea · finance <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Fetches Upbit live orderbook (bids/asks) for one or more KRW markets (no auth). `markets` is comma-separated like 'KRW-BTC,KRW-ETH'. Optional `level` controls price aggregation upstream. Returns an array of { market, timestamp, totalAskSize, totalBidSize, units:[{askPrice,bidPrice,askSize,bidSize}] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `markets` · `level`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `markets` | `STRING` | ✓ | Comma-separated Upbit market codes |
| `level` | `STRING` |  | Optional price-aggregation level (e.g. 10000 for 10k KRW buckets) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `api.upbit.com` (SSRF-guarded); no filesystem.

**JS source**

```javascript
/**
 * Upbit live orderbook (no auth).
 *
 * GET https://api.upbit.com/v1/orderbook?markets=KRW-BTC,KRW-ETH
 *
 * Returns top-N bid/ask units for each market. `level` is optional and
 * controls aggregation in the upstream API (omit for raw).
 */

if (markets == null || markets === '') throw new Error('markets required');
let url = 'https://api.upbit.com/v1/orderbook?markets=' + encodeURIComponent(markets);
if (level != null && level !== '') url += '&level=' + encodeURIComponent(String(level));

const resp = await fetch(url, {
  headers: { 'Accept': 'application/json' },
  maxLength: 1_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

return (resp.json() || []).map(b => ({
  market:           b.market,
  timestamp:        b.timestamp,
  totalAskSize:     b.total_ask_size,
  totalBidSize:     b.total_bid_size,
  units: (b.orderbook_units || []).map(u => ({
    askPrice: u.ask_price,
    bidPrice: u.bid_price,
    askSize:  u.ask_size,
    bidSize:  u.bid_size,
  })),
}));

```

</div>
</div>

<div class="tcg-card t-crypto tcg-card--clickable" id="getUpbitCandles" data-tool-id="getUpbitCandles" data-tool-title="getUpbitCandles" markdown>
<div class="tcg-name"><span class="tcg-name__text">getUpbitCandles</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-chart-line:</div>
<div class="tcg-type">web · korea · finance <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Fetches Upbit OHLCV candles for a market (no auth). `interval` accepts 'days' (default), 'weeks', 'months', or 'minutes/N' where N is one of 1, 3, 5, 10, 15, 30, 60, 240. `count` is capped at 200 upstream. Returns an array (most recent first) of { market, candleDateTimeKst, candleDateTimeUtc, openingPrice, highPrice, lowPrice, tradePrice, candleAccTradeVolume, candleAccTradePrice }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `market` · `interval` · `count`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `market` | `STRING` | ✓ | Upbit market code, e.g. 'KRW-BTC' |
| `interval` | `STRING` |  | 'days' \| 'weeks' \| 'months' \| 'minutes/1' \| 'minutes/60' ... |
| `count` | `STRING` |  | How many candles (max 200) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `api.upbit.com` (SSRF-guarded); no filesystem.

**JS source**

```javascript
/**
 * Upbit OHLCV candles (no auth).
 *
 * GET https://api.upbit.com/v1/candles/days?market=KRW-BTC&count=10
 * GET https://api.upbit.com/v1/candles/minutes/{unit}?market=KRW-BTC&count=10
 *
 * `interval` accepts: 'days' (default), 'weeks', 'months', or 'minutes/N'
 *   where N is one of 1, 3, 5, 10, 15, 30, 60, 240.
 * `count` caps at 200 upstream.
 */

if (market == null || market === '') throw new Error('market required');
const itv  = (interval == null || interval === '') ? 'days' : String(interval);
const cnt  = (count == null || count === '') ? 30 : Math.max(1, Math.min(200, parseInt(count, 10)));

let path;
if (itv.startsWith('minutes/')) path = 'minutes/' + encodeURIComponent(itv.slice('minutes/'.length));
else if (itv === 'days' || itv === 'weeks' || itv === 'months') path = itv;
else throw new Error("interval must be 'days', 'weeks', 'months', or 'minutes/N'");

const url = 'https://api.upbit.com/v1/candles/' + path
          + '?market=' + encodeURIComponent(market)
          + '&count='  + cnt;

const resp = await fetch(url, {
  headers: { 'Accept': 'application/json' },
  maxLength: 2_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

return (resp.json() || []).map(c => ({
  market:                 c.market,
  candleDateTimeKst:      c.candle_date_time_kst,
  candleDateTimeUtc:      c.candle_date_time_utc,
  openingPrice:           c.opening_price,
  highPrice:              c.high_price,
  lowPrice:               c.low_price,
  tradePrice:             c.trade_price,
  candleAccTradeVolume:   c.candle_acc_trade_volume,
  candleAccTradePrice:    c.candle_acc_trade_price,
}));

```

</div>
</div>

<div class="tcg-card t-crypto tcg-card--clickable" id="listUpbitMarkets" data-tool-id="listUpbitMarkets" data-tool-title="listUpbitMarkets" markdown>
<div class="tcg-name"><span class="tcg-name__text">listUpbitMarkets</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-format-list-bulleted:</div>
<div class="tcg-type">web · korea · finance <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Lists all tradable markets on Upbit (no auth). Pass `quote` (e.g. 'KRW', 'BTC', 'USDT') to filter by quote currency. Returns { count, markets: [{ market, koreanName, englishName }] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `quote`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `quote` | `STRING` |  | Optional quote currency filter (KRW, BTC, USDT). Omit for all. |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `api.upbit.com` (SSRF-guarded); no filesystem.

**JS source**

```javascript
/**
 * Upbit market catalog (no auth).
 *
 * GET https://api.upbit.com/v1/market/all?isDetails=false
 *
 * Lists every tradable market on Upbit. Pass `quote` (e.g. 'KRW', 'BTC',
 * 'USDT') to filter by quote currency. Useful to discover what coins are
 * available before calling ticker/candles.
 */

const resp = await fetch('https://api.upbit.com/v1/market/all?isDetails=false', {
  headers: { 'Accept': 'application/json' },
  maxLength: 2_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

let rows = (resp.json() || []).map(m => ({
  market:       m.market,
  koreanName:   m.korean_name,
  englishName:  m.english_name,
}));

if (quote != null && quote !== '') {
  const prefix = String(quote).toUpperCase() + '-';
  rows = rows.filter(r => r.market && r.market.startsWith(prefix));
}

return { count: rows.length, markets: rows };

```

</div>
</div>

<div class="tcg-card t-crypto tcg-card--clickable" id="getBithumbTicker" data-tool-id="getBithumbTicker" data-tool-title="getBithumbTicker" markdown>
<div class="tcg-name"><span class="tcg-name__text">getBithumbTicker</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-currency-btc:</div>
<div class="tcg-type">web · korea · finance <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Fetches the current KRW ticker for a symbol from Bithumb (no auth). Used as an Upbit alternative or for cross-exchange checks. Returns: { symbol, openingPrice, closingPrice, minPrice, maxPrice, unitsTraded24h, accTradeValue24h, change24h, changeRate24h, fluctate24h, fluctateRate24h }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `symbol`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `symbol` | `STRING` | ✓ | Coin symbol (e.g. BTC, ETH). Pair against KRW is implicit. |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `api.bithumb.com` (SSRF-guarded); no filesystem.

**JS source**

```javascript
/**
 * Bithumb Korean exchange public ticker.
 *
 * GET https://api.bithumb.com/public/ticker/{symbol}_KRW
 *
 * The KRW pair is implicit in the URL path. No auth, no rate-limit listed
 * but be polite.
 */

if (symbol == null || symbol === '') throw new Error('symbol required');
const url = 'https://api.bithumb.com/public/ticker/' + encodeURIComponent(symbol) + '_KRW';

const resp = await fetch(url, {
  headers: { 'Accept': 'application/json' },
  maxLength: 500_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };
const d = resp.json();
if (d.status !== '0000') return { success: false, status: d.status, message: d.message || 'bithumb error' };
const t = d.data || {};
return {
  symbol:            String(symbol).toUpperCase(),
  openingPrice:      Number(t.opening_price),
  closingPrice:      Number(t.closing_price),
  minPrice:          Number(t.min_price),
  maxPrice:          Number(t.max_price),
  unitsTraded24h:    Number(t.units_traded_24H),
  accTradeValue24h:  Number(t.acc_trade_value_24H),
  fluctate24h:       Number(t.fluctate_24H),
  fluctateRate24h:   Number(t.fluctate_rate_24H),
};

```

</div>
</div>

<div class="tcg-card t-crypto tcg-card--clickable" id="getBithumbOrderbook" data-tool-id="getBithumbOrderbook" data-tool-title="getBithumbOrderbook" markdown>
<div class="tcg-name"><span class="tcg-name__text">getBithumbOrderbook</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-chart-box-outline:</div>
<div class="tcg-type">web · korea · finance <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Fetches Bithumb public orderbook depth for a KRW pair (no auth). `count` defaults to 5, max 30. Returns { symbol, paymentCurrency, orderCurrency, timestamp, bids:[{price,quantity}], asks:[{price,quantity}] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `symbol` · `count`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `symbol` | `STRING` | ✓ | Coin symbol (e.g. BTC, ETH). Pair against KRW is implicit. |
| `count` | `STRING` |  | Depth (1-30). Default 5. |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `api.bithumb.com` (SSRF-guarded); no filesystem.

**JS source**

```javascript
/**
 * Bithumb public orderbook depth (no auth).
 *
 * GET https://api.bithumb.com/public/orderbook/{symbol}_KRW?count=5
 *
 * Returns top bids/asks for a KRW pair. `count` defaults to 5, max 30.
 */

if (symbol == null || symbol === '') throw new Error('symbol required');
const cnt = (count == null || count === '') ? 5 : Math.max(1, Math.min(30, parseInt(count, 10)));
const url = 'https://api.bithumb.com/public/orderbook/'
          + encodeURIComponent(symbol) + '_KRW?count=' + cnt;

const resp = await fetch(url, {
  headers: { 'Accept': 'application/json' },
  maxLength: 500_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };
const d = resp.json();
if (d.status !== '0000') return { success: false, status: d.status, message: d.message || 'bithumb error' };
const t = d.data || {};
const lift = arr => (arr || []).map(o => ({ price: Number(o.price), quantity: Number(o.quantity) }));
return {
  symbol:        String(symbol).toUpperCase(),
  paymentCurrency: t.payment_currency,
  orderCurrency:   t.order_currency,
  timestamp:     t.timestamp,
  bids:          lift(t.bids),
  asks:          lift(t.asks),
};

```

</div>
</div>

<div class="tcg-card t-naver tcg-card--clickable" id="searchNaver" data-tool-id="searchNaver" data-tool-title="searchNaver" markdown>
<div class="tcg-name"><span class="tcg-name__text">searchNaver</span> <span class="cost">🔑 × 2</span></div>
<div class="tcg-art" markdown>:simple-naver:</div>
<div class="tcg-type">web · korea · search <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Naver Search API (KR; key required). Searches across blog, news, webkr, encyc, book, image, shop, kin, or cafearticle. Issue Client-Id + Client-Secret at https://developers.naver.com/apps/ and set NAVER_CLIENT_ID + NAVER_CLIENT_SECRET on the tool's staticVariables, or inject as env vars. Returns: { type, total, start, display, items:[{title, description, link, pubDate, bloggerName, author}] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `query` · `type` · `display` · `start`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `NAVER_CLIENT_ID` · `NAVER_CLIENT_SECRET`</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `query` | `STRING` | ✓ | Search keyword - Korean queries typical (e.g. '스프링 AI'); other languages also accepted. |
| `type` | `STRING` |  | blog \| news \| webkr \| encyc \| book \| image \| shop \| kin \| cafearticle |
| `display` | `STRING` |  | Result count (1-100, default 10) |
| `start` | `STRING` |  | Start index (1-1000, default 1) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `openapi.naver.com` (SSRF-guarded); no filesystem.

**JS source**

```javascript
/**
 * Naver Search API (KR; requires Naver Open API credentials).
 *
 * GET https://openapi.naver.com/v1/search/{type}.json?query=...&display=...&start=...
 *
 * Auth headers:
 *   X-Naver-Client-Id:     <Naver Open API client id>
 *   X-Naver-Client-Secret: <Naver Open API client secret>
 *
 * Credentials are pulled from the tool's staticVariables, which support `${ENV_VAR}` -
 * by default `${NAVER_CLIENT_ID}` and `${NAVER_CLIENT_SECRET}`. Issue them at
 * https://developers.naver.com/apps/.
 *
 * `type` is one of: blog | news | webkr | encyc | book | image | shop | kin | cafearticle.
 */

if (query == null || query === '') throw new Error('query required');

const allowed = ['blog', 'news', 'webkr', 'encyc', 'book', 'image', 'shop', 'kin', 'cafearticle'];
const typ = (type == null || type === '') ? 'blog' : String(type);
if (allowed.indexOf(typ) < 0) throw new Error("type must be one of: " + allowed.join(', '));

const dsp = (display == null || display === '') ? 10 : Math.max(1, Math.min(100, parseInt(display, 10)));
const st  = (start   == null || start   === '') ?  1 : Math.max(1, Math.min(1000, parseInt(start, 10)));

const url = 'https://openapi.naver.com/v1/search/' + typ + '.json'
          + '?query='   + encodeURIComponent(query)
          + '&display=' + dsp
          + '&start='   + st;

const resp = await fetch(url, {
  headers: {
    'X-Naver-Client-Id':     naverClientId,
    'X-Naver-Client-Secret': naverClientSecret,
    'Accept': 'application/json',
  },
  maxLength: 2_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

const d = resp.json();
return {
  type:        typ,
  total:       d.total,
  start:       d.start,
  display:     d.display,
  items: (d.items || []).map(it => ({
    title:       (it.title       || '').replace(/<\/?b>/g, ''),
    description: (it.description || '').replace(/<\/?b>/g, ''),
    link:        it.link,
    pubDate:     it.pubDate || it.postdate || null,
    bloggerName: it.bloggername || null,
    author:      it.author      || null,
  })),
};

```

</div>
</div>

<div class="tcg-card t-kakao tcg-card--clickable" id="searchKakaoLocal" data-tool-id="searchKakaoLocal" data-tool-title="searchKakaoLocal" markdown>
<div class="tcg-name"><span class="tcg-name__text">searchKakaoLocal</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:simple-kakaotalk:</div>
<div class="tcg-type">web · korea · geo <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Kakao Local keyword search - finds places/POIs and returns WGS84 coordinates (KR; key required). Issue a REST API key at https://developers.kakao.com/ and set KAKAO_REST_API_KEY on the tool's staticVariables, or inject as env var. Optionally pass (longitude, latitude, radius) to search around a point. Returns: { totalCount, pageableCount, isEnd, places:[{ name, category, categoryGroup, phone, address, roadAddress, latitude, longitude, placeUrl, distance }] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `query` · `size` · `page` · `longitude` · `latitude` · `radius`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `KAKAO_REST_API_KEY`</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `query` | `STRING` | ✓ | Search keyword - Korean queries typical for KR POIs (e.g. '강남역 스타벅스'); other languages also accepted. |
| `size` | `STRING` |  | Results per page (1-15, default 10) |
| `page` | `STRING` |  | Page number (1-45, default 1) |
| `longitude` | `STRING` |  | Center longitude (WGS84). Leave empty for nationwide search. |
| `latitude` | `STRING` |  | Center latitude (WGS84). Leave empty for nationwide search. |
| `radius` | `STRING` |  | Radius in meters (0-20000). Used only when longitude/latitude are set. |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `dapi.kakao.com` (SSRF-guarded); no filesystem.

**JS source**

```javascript
/**
 * Kakao Local - keyword place search (KR; requires Kakao REST API key).
 *
 * GET https://dapi.kakao.com/v2/local/search/keyword.json?query=...&size=...&page=...
 *
 * Auth header: Authorization: KakaoAK <REST_API_KEY>
 *
 * Credential lives in staticVariables - `${KAKAO_REST_API_KEY}` by default.
 * Issue a REST API key at https://developers.kakao.com/.
 *
 * Optional (longitude, latitude, radius) narrows the search to a circle.
 */

if (query == null || query === '') throw new Error('query required');

const sz = (size == null || size === '') ? 10 : Math.max(1, Math.min(15, parseInt(size, 10)));
const pg = (page == null || page === '') ?  1 : Math.max(1, Math.min(45, parseInt(page, 10)));

let url = 'https://dapi.kakao.com/v2/local/search/keyword.json'
        + '?query=' + encodeURIComponent(query)
        + '&size='  + sz
        + '&page='  + pg;

if (longitude != null && longitude !== '' && latitude != null && latitude !== '') {
  url += '&x=' + encodeURIComponent(String(longitude))
       + '&y=' + encodeURIComponent(String(latitude));
  if (radius != null && radius !== '') {
    url += '&radius=' + encodeURIComponent(String(Math.max(0, Math.min(20000, parseInt(radius, 10)))));
  }
}

const resp = await fetch(url, {
  headers: {
    'Authorization': 'KakaoAK ' + kakaoRestApiKey,
    'Accept': 'application/json',
  },
  maxLength: 1_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

const d = resp.json();
const meta = d.meta || {};
return {
  totalCount:    meta.total_count,
  pageableCount: meta.pageable_count,
  isEnd:         meta.is_end,
  places: (d.documents || []).map(p => ({
    name:          p.place_name,
    category:      p.category_name,
    categoryGroup: p.category_group_name,
    phone:         p.phone,
    address:       p.address_name,
    roadAddress:   p.road_address_name,
    latitude:      Number(p.y),
    longitude:     Number(p.x),
    placeUrl:      p.place_url,
    distance:      p.distance ? Number(p.distance) : null,
  })),
};

```

</div>
</div>

<div class="tcg-card t-datagokr tcg-card--clickable" id="getAirKoreaPm" data-tool-id="getAirKoreaPm" data-tool-title="getAirKoreaPm" markdown>
<div class="tcg-name"><span class="tcg-name__text">getAirKoreaPm</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:material-air-filter:</div>
<div class="tcg-type">web · korea · weather <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
AirKorea (data.go.kr) real-time air quality readings by Korean province (KR; data.go.kr key required). Issue the air-quality serviceKey at https://www.data.go.kr/data/15073861/openapi.do and set DATA_GO_KR_AIR_KEY on the tool's staticVariables, or inject as env var. Returns: { sidoName, totalCount, stations:[{ stationName, sidoName, dataTime, pm10, pm25, o3, no2, co, so2, khaiValue, khaiGrade }] }. On auth error: { success:false, status, message }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `sidoName` · `numOfRows`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `DATA_GO_KR_AIR_KEY`</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `sidoName` | `STRING` | ✓ | Korean string required - province name as the AirKorea API expects. Examples: '전국' (nationwide), '서울', '부산', '제주', '경기'. |
| `numOfRows` | `STRING` |  | Stations per call (1-1000, default 100) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `apis.data.go.kr` (SSRF-guarded); no filesystem.

**JS source**

```javascript
/**
 * AirKorea - 시도별 실시간 대기질 (PM10/PM2.5/O3/...) (KR; requires data.go.kr key).
 *
 * GET http://apis.data.go.kr/B552584/ArpltnInforInqireSvc/getCtprvnRltmMesureDnsty
 *     ?sidoName={sido}&pageNo=1&numOfRows=100&returnType=json&serviceKey={key}&ver=1.3
 *
 * Credential lives in staticVariables - `${DATA_GO_KR_AIR_KEY}` by default.
 * Issue a serviceKey at https://www.data.go.kr/data/15073861/openapi.do.
 *
 * `sidoName` is the Korean province name: 전국 | 서울 | 부산 | 대구 | 인천 | 광주 | 대전 |
 *   울산 | 경기 | 강원 | 충북 | 충남 | 전북 | 전남 | 경북 | 경남 | 제주 | 세종.
 *
 * NOTE: data.go.kr endpoints are HTTP not HTTPS - included in the allowlist.
 *       data.go.kr returns HTTP 200 with an error envelope when the serviceKey
 *       is bad - we detect `OpenAPI_ServiceResponse.cmmMsgHeader` and surface
 *       a structured failure.
 */

if (sidoName == null || sidoName === '') throw new Error('sidoName required (예: 서울 / 경기 / 전국)');

const rows = (numOfRows == null || numOfRows === '') ? 100 : Math.max(1, Math.min(1000, parseInt(numOfRows, 10)));

const url = 'http://apis.data.go.kr/B552584/ArpltnInforInqireSvc/getCtprvnRltmMesureDnsty'
          + '?sidoName='   + encodeURIComponent(sidoName)
          + '&pageNo=1'
          + '&numOfRows='  + rows
          + '&returnType=json'
          + '&serviceKey=' + encodeURIComponent(dataGoKrAirKey)
          + '&ver=1.3';

const resp = await fetch(url, {
  headers: { 'Accept': 'application/json' },
  maxLength: 2_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

const d = resp.json();

// data.go.kr returns HTTP 200 with this envelope on auth / quota / service errors.
if (d && d.OpenAPI_ServiceResponse) {
  const hdr = d.OpenAPI_ServiceResponse.cmmMsgHeader || {};
  return {
    success: false,
    status:  hdr.returnReasonCode || 'unknown',
    message: hdr.returnAuthMsg    || hdr.errMsg || 'data.go.kr service error',
  };
}

const body = (d.response && d.response.body) || {};
const items = (body.items || []).map(s => ({
  stationName:    s.stationName,
  sidoName:       s.sidoName,
  dataTime:       s.dataTime,
  pm10:           s.pm10Value === '-' ? null : Number(s.pm10Value),
  pm25:           s.pm25Value === '-' ? null : Number(s.pm25Value),
  o3:             s.o3Value   === '-' ? null : Number(s.o3Value),
  no2:            s.no2Value  === '-' ? null : Number(s.no2Value),
  co:             s.coValue   === '-' ? null : Number(s.coValue),
  so2:            s.so2Value  === '-' ? null : Number(s.so2Value),
  khaiValue:      s.khaiValue === '-' ? null : Number(s.khaiValue),
  khaiGrade:      s.khaiGrade,
}));

return {
  sidoName:    sidoName,
  totalCount:  body.totalCount,
  stations:    items,
};

```

</div>
</div>

<div class="tcg-card t-apple tcg-card--clickable" id="searchKpopOnItunes" data-tool-id="searchKpopOnItunes" data-tool-title="searchKpopOnItunes" markdown>
<div class="tcg-name"><span class="tcg-name__text">searchKpopOnItunes</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:simple-applemusic:</div>
<div class="tcg-type">web · korea <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
iTunes Search API - Korean music catalog including K-pop (no auth). Default country=kr biases results to the Korean iTunes storefront. Suitable for song / musicArtist / album / musicVideo lookups. Each result includes a 30s preview URL and album artwork URL. Catalog metadata is in the storefront language (Korean for kr). `entity`: musicArtist | song | album | musicVideo | mix. `country`: ISO-2 storefront code (kr/us/jp/...). Default kr. Returns: { country, entity, resultCount, results:[{ kind, artistName, trackName, collection, releaseDate, primaryGenre, previewUrl, trackViewUrl, artworkUrl, ... }] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `term` · `entity` · `country` · `limit`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `term` | `STRING` | ✓ | Search keyword (artist / track / album) - Korean (e.g. '블랙핑크') or romanized ('BLACKPINK') both work. |
| `entity` | `STRING` |  | song \| musicArtist \| album \| musicVideo \| mix (default song) |
| `country` | `STRING` |  | ISO-2 storefront code (kr/us/jp/...), default kr |
| `limit` | `STRING` |  | Result count (1-200, default 10) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `itunes.apple.com` (SSRF-guarded); no filesystem.

**JS source**

```javascript
/**
 * iTunes Search API - K-pop & Korean music catalog (no auth).
 *
 * GET https://itunes.apple.com/search?term=BTS&country=kr&media=music&entity=song&limit=10
 *
 * Defaults to Korean storefront (country=kr) which surfaces K-pop releases. Useful for:
 *   - finding K-pop tracks/albums/artists
 *   - getting Apple Music preview URLs (30 s) + album art
 *   - looking up release dates & genres
 *
 * `entity` is one of: musicArtist | song | album | musicVideo | mix.
 * `country` is an ISO-2 storefront code (kr / us / jp / ...).
 */

if (term == null || term === '') throw new Error('term required');

const allowedEntities = ['musicArtist', 'song', 'album', 'musicVideo', 'mix'];
const ent = (entity == null || entity === '') ? 'song' : String(entity);
if (allowedEntities.indexOf(ent) < 0) throw new Error('entity must be one of: ' + allowedEntities.join(', '));

const cc = (country == null || country === '') ? 'kr' : String(country).toLowerCase();
const lim = (limit == null || limit === '') ? 10 : Math.max(1, Math.min(200, parseInt(limit, 10)));

const url = 'https://itunes.apple.com/search'
          + '?term='    + encodeURIComponent(term)
          + '&country=' + encodeURIComponent(cc)
          + '&media=music'
          + '&entity='  + encodeURIComponent(ent)
          + '&limit='   + lim;

const resp = await fetch(url, {
  headers: { 'Accept': 'application/json', 'User-Agent': 'spring-ai-playground' },
  maxLength: 2_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

const d = resp.json();
return {
  country:      cc,
  entity:       ent,
  resultCount:  d.resultCount,
  results: (d.results || []).map(r => ({
    kind:         r.kind || r.wrapperType,
    artistName:   r.artistName,
    trackName:    r.trackName,
    collection:   r.collectionName,
    releaseDate:  r.releaseDate,
    primaryGenre: r.primaryGenreName,
    previewUrl:   r.previewUrl,
    trackViewUrl: r.trackViewUrl,
    artistViewUrl:r.artistViewUrl,
    artworkUrl:   r.artworkUrl100,
    trackTimeMs:  r.trackTimeMillis,
    country:      r.country,
  })),
};

```

</div>
</div>

<div class="tcg-card t-beauty tcg-card--clickable" id="searchKBeautyProducts" data-tool-id="searchKBeautyProducts" data-tool-title="searchKBeautyProducts" markdown>
<div class="tcg-name"><span class="tcg-name__text">searchKBeautyProducts</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-flower-tulip-outline:</div>
<div class="tcg-type">web · korea · search <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
K-beauty cosmetics product search via Open Beauty Facts (no auth). Default country=south-korea biases the lookup to Korean brand catalogs (Innisfree / Laneige / COSRX / ...). Returns ingredients, allergens, packaging, and product image URLs. For a global search pass country='', or other slugs like country='japan'. Note: localized product names may appear in Korean. Pass a barcode (e.g. '8809610706106') as `query` for single-product lookup - useful for ingredient checks. Returns: { country, count, page, pageSize, products:[{ code, productName, brands, countries, categories, allergens, ingredients, packaging, imageUrl, openBeautyFactsUrl }] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `query` · `country` · `pageSize`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; -</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `query` | `STRING` | ✓ | Search keyword or barcode - English typical for ingredients ('snail mucin'), Korean works for brand names ('이니스프리'). |
| `country` | `STRING` |  | Open Beauty Facts country slug. Default 'south-korea'. Empty string for global, 'japan' for Japan, etc. |
| `pageSize` | `STRING` |  | Result count (1-50, default 10) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `world.openbeautyfacts.org` (SSRF-guarded); no filesystem.

**JS source**

```javascript
/**
 * K-beauty product search via Open Beauty Facts (no auth).
 *
 * GET https://world.openbeautyfacts.org/api/v2/search
 *     ?search_terms=...&page_size=...&countries_tags_en=south-korea
 *     &fields=product_name,brands,code,countries_tags,ingredients_text,
 *             image_front_url,packaging,allergens_tags,categories_tags
 *
 * Defaults to `country=south-korea` so the catalog is K-beauty-flavoured. Override
 * to '' for a global search, or to any country slug Open Beauty Facts knows
 * (e.g. 'japan', 'united-states').
 *
 * Tip: passing a barcode (e.g. '8809610706106') usually returns a single product;
 * use that for ingredient lookup of a specific item you have in hand.
 */

if (query == null || query === '') throw new Error('query required (search term or barcode)');

const sz = (pageSize == null || pageSize === '') ? 10 : Math.max(1, Math.min(50, parseInt(pageSize, 10)));
const cc = country == null ? 'south-korea' : String(country);
const fields = [
  'code','product_name','brands','countries_tags','ingredients_text',
  'image_front_url','image_front_small_url','packaging','allergens_tags',
  'categories_tags','url',
].join(',');

let url = 'https://world.openbeautyfacts.org/api/v2/search'
        + '?search_terms=' + encodeURIComponent(query)
        + '&page_size='   + sz
        + '&fields='      + encodeURIComponent(fields);
if (cc !== '') url += '&countries_tags_en=' + encodeURIComponent(cc);

const resp = await fetch(url, {
  headers: { 'Accept': 'application/json', 'User-Agent': 'spring-ai-playground' },
  maxLength: 3_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

const d = resp.json();

function lift(tag) {
  // Open Beauty Facts uses prefixed tags like "en:south-korea" or "en:fragrance".
  if (typeof tag !== 'string') return tag;
  const colon = tag.indexOf(':');
  return colon >= 0 ? tag.slice(colon + 1) : tag;
}

return {
  country:    cc,
  count:      d.count,
  page:       d.page,
  pageSize:   d.page_size,
  products: (d.products || []).map(p => ({
    code:         p.code,
    productName:  p.product_name,
    brands:       p.brands,
    countries:   (p.countries_tags  || []).map(lift),
    categories:  (p.categories_tags || []).map(lift),
    allergens:   (p.allergens_tags  || []).map(lift),
    ingredients:  p.ingredients_text,
    packaging:    p.packaging,
    imageUrl:     p.image_front_url || p.image_front_small_url,
    openBeautyFactsUrl: p.url,
  })),
};

```

</div>
</div>

<div class="tcg-card t-tour tcg-card--clickable" id="searchKoreaTour" data-tool-id="searchKoreaTour" data-tool-title="searchKoreaTour" markdown>
<div class="tcg-name"><span class="tcg-name__text">searchKoreaTour</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:material-bag-suitcase-outline:</div>
<div class="tcg-type">web · korea · search <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Korea Tourism Organization TourAPI 4.0 keyword search - tourist spots, cultural facilities, festivals, lodging, restaurants by Korean keyword (KR; data.go.kr key required, separate from the air-quality key). Issue the Korean tourism serviceKey at https://www.data.go.kr/data/15101578/openapi.do and set DATA_GO_KR_TOUR_KEY on the tool's staticVariables, or inject as env var. Filters: `areaCode` (province) + `sigunguCode` (city/county) + `contentTypeId` (content type). Examples: Jeonju = areaCode 37 + sigunguCode 12, Gyeongju = 35+2, Jeju City = 39+4, Seogwipo = 39+5. Metropolitan cities (Busan=6, Daegu=4, ...) do not require sigunguCode. Returns: { keyword, totalCount, pageNo, numOfRows, items:[{ contentId, contentTypeId, title, addr1, addr2, areaCode, sigunguCode, firstImage, mapX, mapY, tel, ... }] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `keyword` · `areaCode` · `sigunguCode` · `contentTypeId` · `pageNo` · `numOfRows`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `DATA_GO_KR_TOUR_KEY`</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `keyword` | `STRING` | ✓ | Korean string required - Korean-language tour keyword (e.g. '경복궁', '제주 흑돼지', '부산 해운대'). |
| `areaCode` | `STRING` |  | Optional. Province code (Seoul=1, Busan=6, Gyeongbuk=35, Jeonbuk=37, Jeju=39, ...) |
| `sigunguCode` | `STRING` |  | Optional. City/county code (e.g. Jeonju=12 in Jeonbuk, Gyeongju=2 in Gyeongbuk, Jeju City=4 in Jeju, Seogwipo=5) |
| `contentTypeId` | `STRING` |  | Optional. 12=tourist spot, 14=cultural facility, 15=festival, 25=travel course, 28=leisure sports, 32=lodging, 38=shopping, 39=restaurant |
| `pageNo` | `STRING` |  | Page number (default 1) |
| `numOfRows` | `STRING` |  | Results per page (1-100, default 10) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `apis.data.go.kr` (SSRF-guarded); no filesystem.

**JS source**

```javascript
/**
 * Korea TourAPI 4.0 - 한국관광공사 키워드 검색 (KR; requires data.go.kr key).
 *
 * GET http://apis.data.go.kr/B551011/KorService2/searchKeyword2
 *     ?serviceKey={KEY}&keyword=경복궁&areaCode=&sigunguCode=&contentTypeId=
 *     &MobileOS=ETC&MobileApp=spring-ai-playground&_type=json&pageNo=1&numOfRows=10
 *
 * Credential lives in staticVariables - `${DATA_GO_KR_TOUR_KEY}` by default.
 * Issue a serviceKey at https://www.data.go.kr/data/15101578/openapi.do.
 *
 * Optional filters:
 *   areaCode        서울=1, 인천=2, 대전=3, 대구=4, 광주=5, 부산=6, 울산=7, 세종=8,
 *                   경기=31, 강원=32, 충북=33, 충남=34, 경북=35, 경남=36, 전북=37,
 *                   전남=38, 제주=39.
 *   sigunguCode     시·군·구 코드. areaCode와 함께 사용. 대표 값:
 *                     전북(37) → 전주=12, 군산=11, 익산=14, 남원=15
 *                     경북(35) → 경주=2,  안동=1,  포항=23
 *                     제주(39) → 제주시=4, 서귀포시=5
 *                   (광역시는 sigungu 불필요)
 *   contentTypeId   관광지=12, 문화시설=14, 축제공연행사=15, 여행코스=25,
 *                   레포츠=28, 숙박=32, 쇼핑=38, 음식점=39.
 */

if (keyword == null || keyword === '') throw new Error('keyword required');

const rows = (numOfRows == null || numOfRows === '') ? 10 : Math.max(1, Math.min(100, parseInt(numOfRows, 10)));
const pg   = (pageNo    == null || pageNo    === '') ?  1 : Math.max(1, parseInt(pageNo, 10));

let url = 'http://apis.data.go.kr/B551011/KorService2/searchKeyword2'
        + '?serviceKey=' + encodeURIComponent(dataGoKrTourKey)
        + '&keyword='    + encodeURIComponent(keyword)
        + '&MobileOS=ETC&MobileApp=spring-ai-playground'
        + '&_type=json'
        + '&pageNo='     + pg
        + '&numOfRows='  + rows;

if (areaCode      != null && areaCode      !== '') url += '&areaCode='      + encodeURIComponent(String(areaCode));
if (sigunguCode   != null && sigunguCode   !== '') url += '&sigunguCode='   + encodeURIComponent(String(sigunguCode));
if (contentTypeId != null && contentTypeId !== '') url += '&contentTypeId=' + encodeURIComponent(String(contentTypeId));

const resp = await fetch(url, {
  headers: { 'Accept': 'application/json' },
  maxLength: 2_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

const d = resp.json();
if (d && d.OpenAPI_ServiceResponse) {
  const hdr = d.OpenAPI_ServiceResponse.cmmMsgHeader || {};
  return {
    success: false,
    status:  hdr.returnReasonCode || 'unknown',
    message: hdr.returnAuthMsg    || hdr.errMsg || 'TourAPI service error',
  };
}

const body = (d.response && d.response.body) || {};
let itemsRaw = (body.items && body.items.item) || [];
if (itemsRaw && !Array.isArray(itemsRaw) && typeof itemsRaw === 'object') itemsRaw = [itemsRaw];

const items = itemsRaw.map(s => ({
  contentId:     s.contentid,
  contentTypeId: s.contenttypeid,
  title:         s.title,
  addr1:         s.addr1,
  addr2:         s.addr2,
  areaCode:      s.areacode,
  sigunguCode:   s.sigungucode,
  cat1:          s.cat1,
  cat2:          s.cat2,
  cat3:          s.cat3,
  firstImage:    s.firstimage,
  firstImage2:   s.firstimage2,
  mapX:          s.mapx ? Number(s.mapx) : null,
  mapY:          s.mapy ? Number(s.mapy) : null,
  tel:           s.tel,
  modifiedTime:  s.modifiedtime,
}));

return {
  keyword:      keyword,
  totalCount:   body.totalCount,
  pageNo:       body.pageNo,
  numOfRows:    body.numOfRows,
  items:        items,
};

```

</div>
</div>

<div class="tcg-card t-seoul tcg-card--clickable" id="searchSeoulCulturalEvents" data-tool-id="searchSeoulCulturalEvents" data-tool-title="searchSeoulCulturalEvents" markdown>
<div class="tcg-name"><span class="tcg-name__text">searchSeoulCulturalEvents</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:material-theater:</div>
<div class="tcg-type">web · korea · search <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
Seoul Open Data Plaza (data.seoul.go.kr) cultural events search (KR; separate key required). This API is operated directly by the Seoul city government and is separate from the data.go.kr keychain. Issue a free key at https://data.seoul.go.kr/together/apikey.do (1,000 req/day) and set SEOUL_OPEN_API_KEY on the tool's staticVariables, or inject as env var. Optional filters: `codename` (category: musical / exhibition / Korean classical music / concert / ...), `titleSearch` (partial title match), `eventDate` ('YYYY-MM-DD'; events active on that date only). Returns: { totalCount, events:[{ category, gu, title, period, startDate, endDate, place, organizer, audience, fee, isFree, program, imageUrl, latitude, longitude, ... }] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `startIndex` · `endIndex` · `codename` · `titleSearch` · `eventDate`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `SEOUL_OPEN_API_KEY`</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `startIndex` | `STRING` |  | Start index (default 1) |
| `endIndex` | `STRING` |  | End index (max 1000 per call) |
| `codename` | `STRING` |  | Optional. Korean string required when used - category in Korean (e.g. '뮤지컬', '전시', '국악', '콘서트'). |
| `titleSearch` | `STRING` |  | Optional. Partial title match - Korean strings typical (e.g. '봄꽃'). |
| `eventDate` | `STRING` |  | Optional. 'YYYY-MM-DD' - only events active on that date |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `openapi.seoul.go.kr` (SSRF-guarded); no filesystem.

**JS source**

```javascript
/**
 * Seoul Open Data Plaza - 서울시 문화행사 정보 (KR; requires Seoul Open API key,
 * issued separately from data.go.kr).
 *
 * URL is positional (path-segment style):
 *   http://openapi.seoul.go.kr:8088/{KEY}/json/culturalEventInfo/{START}/{END}
 *      /{CODENAME}/{TITLE}/{DATE}
 *
 * - CODENAME: optional 분류 (예: '뮤지컬', '전시/미술', '국악', '콘서트')
 * - TITLE:    optional 제목 부분 일치
 * - DATE:     optional 'YYYY-MM-DD' - 해당 날짜에 진행되는 행사만
 *
 * Empty trailing params are simply omitted. Issue a key at
 * https://data.seoul.go.kr/together/apikey.do (free; 1,000 req/day).
 *
 * Top-level RESULT envelope carries auth/quota errors (CODE != 'INFO-000').
 */


const start = (startIndex == null || startIndex === '') ?  1 : Math.max(1, parseInt(startIndex, 10));
const end   = (endIndex   == null || endIndex   === '') ? 10 : Math.max(start, parseInt(endIndex, 10));
if (end - start + 1 > 1000) throw new Error('Seoul Open API allows max 1000 rows per call');

const segs = [encodeURIComponent(seoulOpenApiKey), 'json', 'culturalEventInfo', String(start), String(end)];
const hasDate     = (eventDate != null && eventDate !== '');
const hasTitle    = (titleSearch != null && titleSearch !== '');
const hasCodename = (codename != null && codename !== '');
if (hasCodename || hasTitle || hasDate)
  segs.push(hasCodename ? encodeURIComponent(codename)    : '%20');
if (hasTitle || hasDate)
  segs.push(hasTitle    ? encodeURIComponent(titleSearch) : '%20');
if (hasDate)
  segs.push(encodeURIComponent(eventDate));

const url = 'http://openapi.seoul.go.kr:8088/' + segs.join('/');

const resp = await fetch(url, {
  headers: { 'Accept': 'application/json', 'User-Agent': 'spring-ai-playground' },
  maxLength: 3_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

const d = resp.json();
// Top-level error envelope (auth failure, system error, etc.)
if (d && d.RESULT && d.RESULT.CODE && d.RESULT.CODE !== 'INFO-000')
  return { success: false, status: d.RESULT.CODE, message: d.RESULT.MESSAGE };
const svc = d.culturalEventInfo || {};
if (svc.RESULT && svc.RESULT.CODE && svc.RESULT.CODE !== 'INFO-000')
  return { success: false, status: svc.RESULT.CODE, message: svc.RESULT.MESSAGE };

const events = (svc.row || []).map(r => ({
  category:   r.CODENAME,
  gu:         r.GUNAME,
  title:      r.TITLE,
  period:     r.DATE,
  startDate:  r.STRTDATE,
  endDate:    r.END_DATE,
  place:      r.PLACE,
  organizer:  r.ORG_NAME,
  audience:   r.USE_TGT,
  fee:        r.USE_FEE,
  isFree:     r.IS_FREE === '무료',
  program:    r.PROGRAM,
  description:r.ETC_DESC,
  imageUrl:   r.MAIN_IMG,
  homepage:   r.HMPG_ADDR,
  link:       r.ORG_LINK,
  latitude:   r.LAT ? Number(r.LAT) : null,
  longitude:  r.LOT ? Number(r.LOT) : null,
}));

return {
  totalCount: svc.list_total_count,
  events:     events,
};

```

</div>
</div>

<div class="tcg-card t-kamis tcg-card--clickable" id="getKamisAgriPrice" data-tool-id="getKamisAgriPrice" data-tool-title="getKamisAgriPrice" markdown>
<div class="tcg-name"><span class="tcg-name__text">getKamisAgriPrice</span> <span class="cost">🔑 × 2</span></div>
<div class="tcg-art" markdown>:material-leaf:</div>
<div class="tcg-type">web · korea · finance <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
KAMIS agricultural product wholesale/retail prices - daily price data operated by aT (Korea Agro-Fisheries & Food Trade Corp). KR; cert_id + cert_key required, free. Issue credentials at https://www.kamis.or.kr/customer/reference/openapi_list.do and set KAMIS_CERT_ID + KAMIS_CERT_KEY on the tool's staticVariables, or inject as env vars. `productClsCode`: 01=retail, 02=wholesale (default). `itemCode` is the KAMIS product code (rice=111, apple=411, napa cabbage=211, pork belly=515, ...). Returns: { productClass, itemCode, startDay, endDay, count, rows:[{ itemName, kindName, county, market, year, date, price, unit }] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `itemCode` · `startDay` · `endDay` · `productClsCode` · `itemCategoryCode` · `kindCode`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `KAMIS_CERT_ID` · `KAMIS_CERT_KEY`</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `itemCode` | `STRING` | ✓ | KAMIS product code (e.g. rice=111, apple=411, napa cabbage=211) |
| `startDay` | `STRING` | ✓ | Query start date (YYYY-MM-DD or YYYYMMDD) |
| `endDay` | `STRING` | ✓ | Query end date (YYYY-MM-DD or YYYYMMDD) |
| `productClsCode` | `STRING` |  | 01=retail, 02=wholesale (default 02) |
| `itemCategoryCode` | `STRING` |  | Optional. 100=grain, 200=vegetable, 300=specialty, 400=fruit, 500=livestock, 600=fishery |
| `kindCode` | `STRING` |  | Optional. Variety code (omit for all) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `www.kamis.or.kr` (SSRF-guarded); no filesystem.

**JS source**

```javascript
/**
 * KAMIS - 한국농수산식품유통공사 농산물 도·소매 가격 조회 (KR; cert_id + cert_key 필요).
 *
 * GET http://www.kamis.or.kr/service/price/xml.do?action=periodProductList
 *     &p_cert_key={KEY}&p_cert_id={ID}&p_returntype=json
 *     &p_productclscode=02&p_itemcategorycode=400&p_itemcode=411
 *     &p_startday=2026-05-01&p_endday=2026-05-13
 *
 * Credentials live in staticVariables - `${KAMIS_CERT_ID}` and `${KAMIS_CERT_KEY}` by default.
 * Sign up at https://www.kamis.or.kr/customer/reference/openapi_list.do (free).
 *
 * Codes (a small subset):
 *   p_productclscode    01=소매, 02=도매
 *   p_itemcategorycode  100=식량작물, 200=채소류, 300=특용작물, 400=과일류,
 *                       500=축산물, 600=수산물
 *   p_itemcode          품목 코드. 자주 쓰는 예:
 *                         식량 111=쌀, 112=찹쌀, 113=현미
 *                         채소 211=배추, 215=상추, 226=양파, 233=양배추
 *                         과일 411=사과, 412=배, 418=포도, 422=감귤
 *                         축산 514=한우(등심), 515=돼지(삼겹)
 */

if (itemCode == null || itemCode === '') throw new Error('itemCode required (KAMIS 품목 코드)');

const cls = (productClsCode == null || productClsCode === '') ? '02' : String(productClsCode);
const cat = (itemCategoryCode == null || itemCategoryCode === '') ? '' : String(itemCategoryCode);

function fmt(d) {
  if (d == null || d === '') return '';
  const s = String(d);
  // Accept YYYYMMDD and reformat to YYYY-MM-DD.
  if (/^\d{8}$/.test(s)) return s.slice(0,4) + '-' + s.slice(4,6) + '-' + s.slice(6,8);
  return s;
}
const start = fmt(startDay);
const end   = fmt(endDay);
if (start === '') throw new Error('startDay required (YYYY-MM-DD or YYYYMMDD)');
if (end   === '') throw new Error('endDay required (YYYY-MM-DD or YYYYMMDD)');

let url = 'http://www.kamis.or.kr/service/price/xml.do?action=periodProductList'
        + '&p_cert_key=' + encodeURIComponent(kamisCertKey)
        + '&p_cert_id='  + encodeURIComponent(kamisCertId)
        + '&p_returntype=json'
        + '&p_productclscode='   + encodeURIComponent(cls)
        + '&p_itemcode='         + encodeURIComponent(String(itemCode))
        + '&p_startday=' + encodeURIComponent(start)
        + '&p_endday='   + encodeURIComponent(end);
if (cat !== '') url += '&p_itemcategorycode=' + encodeURIComponent(cat);
if (kindCode != null && kindCode !== '')
  url += '&p_kindcode=' + encodeURIComponent(String(kindCode));

const resp = await fetch(url, {
  headers: { 'Accept': 'application/json', 'User-Agent': 'spring-ai-playground' },
  maxLength: 3_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

const d = resp.json();
const data = d.data || {};
if (data.error_code && data.error_code !== '000')
  return { success: false, status: data.error_code, message: data.error_message || 'KAMIS error' };

const rows = (data.item || []).map(r => ({
  itemName:    r.itemname,
  kindName:    r.kindname,
  county:      r.countyname,
  market:      r.marketname,
  year:        r.yyyy,
  date:        r.regday,
  price:       r.price ? Number(String(r.price).replace(/,/g, '')) : null,
  unit:        r.unit,
  productClass: cls === '01' ? '소매' : '도매',
}));

return {
  productClass: cls === '01' ? '소매' : '도매',
  itemCode:     String(itemCode),
  startDay:     start,
  endDay:       end,
  count:        rows.length,
  rows:         rows,
};

```

</div>
</div>

<div class="tcg-card t-kofic tcg-card--clickable" id="getKoficBoxOffice" data-tool-id="getKoficBoxOffice" data-tool-title="getKoficBoxOffice" markdown>
<div class="tcg-name"><span class="tcg-name__text">getKoficBoxOffice</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:material-movie-outline:</div>
<div class="tcg-type">web · korea <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
KOFIC (Korean Film Council) daily box-office ranking (KR; single API key required, free instant issuance). Issue the key at https://www.kobis.or.kr/kobisopenapi/ and set KOFIC_API_KEY on the tool's staticVariables, or inject as env var. `targetDate` is typically yesterday's date (same-day totals are tallied after market close). Both YYYYMMDD and YYYY-MM-DD are accepted. Optional filters: `multiMovieYn` (Y=diversity films only / N=commercial films only), `repNationCd` (K=Korean / F=foreign), `wideAreaCd` (screening region). Returns: { type, showRange, count, movies:[{ rank, rankChange, isNew, movieCode, title, openDate, salesAmount, salesShare, salesAccumulated, audience, audienceAccumulated, screens, shows }] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `targetDate` · `multiMovieYn` · `repNationCd` · `wideAreaCd`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `KOFIC_API_KEY`</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `targetDate` | `STRING` | ✓ | Target date (YYYYMMDD or YYYY-MM-DD) |
| `multiMovieYn` | `STRING` |  | Optional. Y=diversity films only / N=commercial films only (omit for all) |
| `repNationCd` | `STRING` |  | Optional. K=Korean / F=foreign (omit for all) |
| `wideAreaCd` | `STRING` |  | Optional. Screening region code (omit for nationwide) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `www.kobis.or.kr` (SSRF-guarded); no filesystem.

**JS source**

```javascript
/**
 * KOFIC - 영화진흥위원회 일별 박스오피스 순위 (KR; single API key, lightweight signup).
 *
 * GET http://www.kobis.or.kr/kobisopenapi/webservice/rest/boxoffice/searchDailyBoxOfficeList.json
 *     ?key={KEY}&targetDt=20260512
 *
 * Optional filters:
 *   multiMovieYn  Y=다양성영화만 / N=상업영화만 (omit for both)
 *   repNationCd   K=한국영화 / F=외국영화 (omit for both)
 *   wideAreaCd    상영지역 코드 (omit for 전국)
 *
 * Issue a key at https://www.kobis.or.kr/kobisopenapi/ (free, immediate).
 */

if (targetDate == null || targetDate === '') throw new Error('targetDate required (YYYYMMDD)');
// Normalise YYYY-MM-DD → YYYYMMDD
const tgt = String(targetDate).replace(/-/g, '');
if (!/^\d{8}$/.test(tgt)) throw new Error('targetDate must be YYYYMMDD or YYYY-MM-DD');

let url = 'http://www.kobis.or.kr/kobisopenapi/webservice/rest/boxoffice/searchDailyBoxOfficeList.json'
        + '?key='      + encodeURIComponent(koficApiKey)
        + '&targetDt=' + tgt;
if (multiMovieYn != null && multiMovieYn !== '')
  url += '&multiMovieYn=' + encodeURIComponent(String(multiMovieYn).toUpperCase());
if (repNationCd != null && repNationCd !== '')
  url += '&repNationCd='  + encodeURIComponent(String(repNationCd).toUpperCase());
if (wideAreaCd != null && wideAreaCd !== '')
  url += '&wideAreaCd='   + encodeURIComponent(String(wideAreaCd));

const resp = await fetch(url, {
  headers: { 'Accept': 'application/json', 'User-Agent': 'spring-ai-playground' },
  maxLength: 1_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

const d = resp.json();
// KOFIC returns a `faultInfo` envelope on auth/quota errors (HTTP 200).
if (d.faultInfo)
  return { success: false, status: d.faultInfo.errorCode, message: d.faultInfo.message };

const r = d.boxOfficeResult || {};
const num = v => v == null || v === '' ? null : Number(String(v).replace(/,/g, ''));

const rows = (r.dailyBoxOfficeList || []).map(m => ({
  rank:          num(m.rank),
  rankChange:    num(m.rankInten),
  isNew:         m.rankOldAndNew === 'NEW',
  movieCode:     m.movieCd,
  title:         m.movieNm,
  openDate:      m.openDt,
  salesAmount:   num(m.salesAmt),
  salesShare:    num(m.salesShare),
  salesChange:   num(m.salesInten),
  salesAccumulated: num(m.salesAcc),
  audience:      num(m.audiCnt),
  audienceChange: num(m.audiInten),
  audienceAccumulated: num(m.audiAcc),
  screens:       num(m.scrnCnt),
  shows:         num(m.showCnt),
}));

return {
  type:        r.boxofficeType,
  showRange:   r.showRange,
  count:       rows.length,
  movies:      rows,
};

```

</div>
</div>

<div class="tcg-card t-krx tcg-card--clickable" id="getKrxStockPrice" data-tool-id="getKrxStockPrice" data-tool-title="getKrxStockPrice" markdown>
<div class="tcg-name"><span class="tcg-name__text">getKrxStockPrice</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:material-chart-line:</div>
<div class="tcg-type">web · korea · finance <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
KRX Korea Exchange daily stock quotes (data.go.kr) - KOSPI/KOSDAQ/KONEX daily open/close/change/volume/market cap. KR; data.go.kr serviceKey required, separate service application from other dgk keys. Register the `Financial Services Commission stock quote info` service at data.go.kr (https://www.data.go.kr/data/15094808/openapi.do), receive a serviceKey, and set DATA_GO_KR_STOCK_KEY on the tool's staticVariables, or inject as env var. Note: the KIS API (Korea Investment & Securities) is a two-step token → Bearer flow that is inefficient for stateless tools (token consumed per call). This tool uses the KRX-official channel that exposes the same data behind a single key. Filters: `basDt` (business day YYYYMMDD), `itmsNm` (exact stock name), `likeItmsNm` (partial name match), `srtnCd` (short code e.g. 005930), `mrktCls` (KOSPI/KOSDAQ/KONEX). Returns: { totalCount, pageNo, numOfRows, items:[{ baseDate, shortCode, isinCode, name, market, close, diff, changePct, open, high, low, volume, tradeValue, listedShares, marketCap }] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `basDt` · `itmsNm` · `likeItmsNm` · `srtnCd` · `mrktCls` · `numOfRows` · `pageNo`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `DATA_GO_KR_STOCK_KEY`</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `basDt` | `STRING` |  | Business day (YYYYMMDD or YYYY-MM-DD). Latest if omitted. |
| `itmsNm` | `STRING` |  | Korean string required - exact stock name (e.g. '삼성전자', '현대자동차', 'SK하이닉스'). |
| `likeItmsNm` | `STRING` |  | Korean string required when used - partial stock name match (e.g. '삼성', '현대'). |
| `srtnCd` | `STRING` |  | Short stock code (e.g. '005930' for Samsung Electronics) |
| `mrktCls` | `STRING` |  | Market segment: KOSPI / KOSDAQ / KONEX (omit for all) |
| `numOfRows` | `STRING` |  | Results per page (1-1000, default 10) |
| `pageNo` | `STRING` |  | Page (default 1) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `apis.data.go.kr` (SSRF-guarded); no filesystem.

**JS source**

```javascript
/**
 * KRX 주식시세정보 via data.go.kr (KR; data.go.kr serviceKey 필요).
 *
 * GET http://apis.data.go.kr/1160100/service/GetStockSecuritiesInfoService/getStockPriceInfo
 *     ?serviceKey={KEY}&numOfRows=10&pageNo=1&resultType=json
 *     &basDt=20260512&itmsNm=삼성전자
 *
 * Why this and not KIS API?
 *   KIS(한국투자증권 OpenAPI)는 OAuth 토큰 발급 후 Bearer 호출 방식이라 stateless 툴
 *   호출에는 비효율적입니다(매 호출 토큰 새로 발급 → 일일 한도 소모). data.go.kr KRX
 *   엔드포인트는 다른 data.go.kr 툴과 동일한 단일 serviceKey 패턴이고, 일·종목별
 *   시세를 깔끔하게 돌려줍니다.
 *
 * 필터: `basDt`(영업일, YYYYMMDD), `itmsNm`(종목명), `likeItmsNm`(부분일치),
 *       `srtnCd`(단축종목코드, 예: 005930), `mrktCls`(KOSPI/KOSDAQ/KONEX).
 *
 * data.go.kr에서 `금융위원회_주식시세정보` 서비스를 별도 신청 후 serviceKey를 받습니다
 * (https://www.data.go.kr/data/15094808/openapi.do).
 */


const rows = (numOfRows == null || numOfRows === '') ? 10 : Math.max(1, Math.min(1000, parseInt(numOfRows, 10)));
const pg   = (pageNo    == null || pageNo    === '') ?  1 : Math.max(1, parseInt(pageNo, 10));

let url = 'http://apis.data.go.kr/1160100/service/GetStockSecuritiesInfoService/getStockPriceInfo'
        + '?serviceKey=' + encodeURIComponent(dataGoKrStockKey)
        + '&numOfRows='  + rows
        + '&pageNo='     + pg
        + '&resultType=json';

if (basDt != null && basDt !== '') {
  const s = String(basDt).replace(/-/g, '');
  url += '&basDt=' + encodeURIComponent(s);
}
if (itmsNm     != null && itmsNm     !== '') url += '&itmsNm='     + encodeURIComponent(String(itmsNm));
if (likeItmsNm != null && likeItmsNm !== '') url += '&likeItmsNm=' + encodeURIComponent(String(likeItmsNm));
if (srtnCd     != null && srtnCd     !== '') url += '&srtnCd='     + encodeURIComponent(String(srtnCd));
if (mrktCls    != null && mrktCls    !== '') url += '&mrktCls='    + encodeURIComponent(String(mrktCls).toUpperCase());

const resp = await fetch(url, {
  headers: { 'Accept': 'application/json' },
  maxLength: 3_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

const d = resp.json();
if (d && d.OpenAPI_ServiceResponse) {
  const hdr = d.OpenAPI_ServiceResponse.cmmMsgHeader || {};
  return {
    success: false,
    status:  hdr.returnReasonCode || 'unknown',
    message: hdr.returnAuthMsg    || hdr.errMsg || 'KRX service error',
  };
}

const body = (d.response && d.response.body) || {};
let itemsRaw = (body.items && body.items.item) || [];
if (itemsRaw && !Array.isArray(itemsRaw) && typeof itemsRaw === 'object') itemsRaw = [itemsRaw];

const num = v => v == null || v === '' ? null : Number(v);

const items = itemsRaw.map(s => ({
  baseDate:        s.basDt,
  shortCode:       s.srtnCd,
  isinCode:        s.isinCd,
  name:            s.itmsNm,
  market:          s.mrktCtg,
  close:           num(s.clpr),
  diff:            num(s.vs),
  changePct:       num(s.fltRt),
  open:            num(s.mkp),
  high:            num(s.hipr),
  low:             num(s.lopr),
  volume:          num(s.trqu),
  tradeValue:      num(s.trPrc),
  listedShares:    num(s.lstgStCnt),
  marketCap:       num(s.mrktTotAmt),
}));

return {
  totalCount: num(body.totalCount),
  pageNo:     num(body.pageNo),
  numOfRows:  num(body.numOfRows),
  items:      items,
};

```

</div>
</div>

<div class="tcg-card t-datagokr tcg-card--clickable" id="callDataGoKrOpenApi" data-tool-id="callDataGoKrOpenApi" data-tool-title="callDataGoKrOpenApi" markdown>
<div class="tcg-name"><span class="tcg-name__text">callDataGoKrOpenApi</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:material-database:</div>
<div class="tcg-type">web · korea <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
data.go.kr generic dispatcher - calls arbitrary data.go.kr services not covered by dedicated tools in this catalog. High-frequency services (air quality / tourism / stocks / ...) have their own tools; use this dispatcher for the 7,000+ other services (real-estate transactions / postal codes / road-name addresses / drug-safety agency / national statistics / ...). Most responses are in Korean. Set the data.go.kr serviceKey on the tool's staticVariables as DATA_GO_KR_KEY, or inject as env var. NOTE: each data.go.kr dataset requires its own service registration (the key value can be the same, but each OpenAPI service is approved separately). Inputs: { servicePath: 'B551011/KorService2/...' (the path after apis.data.go.kr/), query: { pageNo:1, numOfRows:10, ... } (extra query parameters) }. On success: { ok:true, totalCount, pageNo, numOfRows, items, raw }. On failure: { ok:false, status, message } (HTTP error or OpenAPI_ServiceResponse.cmmMsgHeader error).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `servicePath` · `query`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `DATA_GO_KR_KEY`</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `servicePath` | `STRING` | ✓ | Path after apis.data.go.kr/ (e.g. '1741000/StanReginCd/getStanReginCdList' for the MOIS region-code service) |
| `query` | `OBJECT` |  | Extra query parameters as an object (the example payload - including any Korean region name - lives in testValue) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `apis.data.go.kr` (SSRF-guarded); no filesystem.

**JS source**

```javascript
/**
 * Generic data.go.kr OpenAPI dispatcher - call any service under apis.data.go.kr
 * that isn't covered by a purpose-built tool in this catalog.
 *
 * GET http://apis.data.go.kr/{servicePath}?serviceKey={KEY}&{...query}
 *
 * Inputs:
 *   - servicePath  : path under apis.data.go.kr (예: '1160100/service/.../getStockPriceInfo').
 *                    Must NOT include the scheme or host.
 *   - query        : object of extra query parameters
 *                    (예: { pageNo: 1, numOfRows: 10, basDt: '20260512', itmsNm: '삼성전자' })
 *   - serviceKey   : data.go.kr serviceKey (from staticVariable / env-backed `${DATA_GO_KR_KEY}`).
 *
 * Each service on data.go.kr requires its own approval - the same physical
 * serviceKey is reused, but the developer must have "신청" the dataset.
 *
 * Output envelope:
 *   - success: { ok:true, totalCount, pageNo, numOfRows, items, raw }
 *   - failure: { ok:false, status, message }   (HTTP non-2xx OR cmmMsgHeader error)
 */

if (servicePath == null || servicePath === '') throw new Error('servicePath required');

let path = String(servicePath).trim();
if (path.startsWith('http://') || path.startsWith('https://'))
  throw new Error('servicePath must be relative under apis.data.go.kr (no scheme/host)');
if (path.startsWith('/')) path = path.substring(1);

let url = 'http://apis.data.go.kr/' + path
        + '?serviceKey=' + encodeURIComponent(dataGoKrKey);

// Append additional query parameters.
const q = query || {};
let sawType = false;
for (const k of Object.keys(q)) {
  const v = q[k];
  if (v == null || v === '') continue;
  if (k === '_type' || k === 'resultType') sawType = true;
  url += '&' + encodeURIComponent(k) + '=' + encodeURIComponent(String(v));
}
// Default to JSON if the caller didn't specify a return type.
if (!sawType) url += '&_type=json&resultType=json';

const resp = await fetch(url, {
  headers: { 'Accept': 'application/json' },
  maxLength: 3_000_000,
});
if (!resp.ok) return { ok: false, status: resp.status, message: resp.text() };

const d = resp.json();

// data.go.kr surfaces auth/quota/system errors via this top-level envelope (HTTP 200).
if (d && d.OpenAPI_ServiceResponse) {
  const hdr = d.OpenAPI_ServiceResponse.cmmMsgHeader || {};
  return {
    ok:      false,
    status:  hdr.returnReasonCode || 'unknown',
    message: hdr.returnAuthMsg    || hdr.errMsg || 'data.go.kr service error',
  };
}

// Unwrap the typical response.body shape. Many data.go.kr services use this layout
// (TourAPI, AirKorea, KRX, etc.) - return items as a flat array when present.
const body = (d.response && d.response.body) || d.body || null;
let items = body && body.items;
if (items && items.item) items = items.item;
if (items && !Array.isArray(items) && typeof items === 'object') items = [items];

return {
  ok:         true,
  totalCount: body ? body.totalCount : null,
  pageNo:     body ? body.pageNo     : null,
  numOfRows:  body ? body.numOfRows  : null,
  items:      items || null,
  raw:        d,
};

```

</div>
</div>

<div class="tcg-card t-kma tcg-card--clickable" id="getKmaShortTermForecast" data-tool-id="getKmaShortTermForecast" data-tool-title="getKmaShortTermForecast" markdown>
<div class="tcg-name"><span class="tcg-name__text">getKmaShortTermForecast</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:material-weather-cloudy:</div>
<div class="tcg-type">web · korea · weather <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
KMA short-term weather forecast - hourly forecast for the next ~72 hours by lat/lon or KMA grid coordinates (nx,ny). KR; data.go.kr serviceKey required, separate KMA service registration. Register the `KMA short-term forecast service` at data.go.kr, receive a serviceKey, and set DATA_GO_KR_KMA_KEY on the tool's staticVariables, or inject as env var. Coordinates: pass either (latitude, longitude) or (nx, ny). Lat/lon are converted internally to KMA Lambert grid. baseDate/baseTime default to today's 0500 release (KMA releases at 02/05/08/11/14/17/20/23). Response is pivoted to 1-hour slots: { fcstDate, fcstTime, temp(℃), humidity(%), precipProbability(%), precipType, precipAmount, skyCondition, windSpeed(m/s), windDirection(deg) }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `latitude` · `longitude` · `nx` · `ny` · `baseDate` · `baseTime`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `DATA_GO_KR_KMA_KEY`</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `latitude` | `STRING` |  | Latitude (WGS84). Use instead of nx/ny. |
| `longitude` | `STRING` |  | Longitude (WGS84). Use instead of nx/ny. |
| `nx` | `STRING` |  | KMA grid X (Seoul=60, Busan=98, Jeju=52, ...) |
| `ny` | `STRING` |  | KMA grid Y (Seoul=127, Busan=76, Jeju=38, ...) |
| `baseDate` | `STRING` |  | Release date YYYYMMDD (today if omitted) |
| `baseTime` | `STRING` |  | Release time HHMM (one of 0200/0500/0800/1100/1400/1700/2000/2300, default 0500) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `apis.data.go.kr` (SSRF-guarded); no filesystem.

**JS source**

```javascript
/**
 * 기상청(KMA) 단기예보 (KR; requires data.go.kr serviceKey).
 *
 * GET http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst
 *     ?serviceKey={KEY}&pageNo=1&numOfRows=1000&dataType=JSON
 *     &base_date=20260513&base_time=0500&nx=60&ny=127
 *
 * 좌표 입력 방식 (둘 중 하나):
 *   (1) latitude + longitude  - 위경도 (WGS84) → 내부에서 KMA Lambert Conformal Conic
 *       격자 (nx, ny)로 변환
 *   (2) nx + ny               - KMA 격자 좌표 (예: 서울 60,127 / 부산 98,76 / 제주 52,38)
 *
 * base_date/base_time 미지정 시 오늘 0500 발표분. KMA는 02/05/08/11/14/17/20/23시
 * 발표하며 데이터는 발표 ~30분 후 가용.
 *
 * 응답은 (fcstDate, fcstTime) 단위로 pivot되어 시간슬롯별 한 행으로 정리됩니다.
 * 컬럼: temp(℃), humidity(%), precipProbability(%), precipType, precipAmount,
 *       skyCondition, windSpeed(m/s), windDirection(deg).
 */


// Convert lat/lon → KMA Lambert Conformal Conic grid (nx, ny).
function latLonToGrid(lat, lon) {
  const RE = 6371.00877, GRID = 5.0;
  const SLAT1 = 30.0, SLAT2 = 60.0;
  const OLON  = 126.0, OLAT  = 38.0;
  const XO = 43, YO = 136;
  const D = Math.PI / 180.0;
  const re = RE / GRID;
  const slat1 = SLAT1 * D, slat2 = SLAT2 * D;
  const olon  = OLON  * D, olat  = OLAT  * D;
  let sn = Math.log(Math.cos(slat1) / Math.cos(slat2))
         / Math.log(Math.tan(Math.PI*0.25 + slat2*0.5) / Math.tan(Math.PI*0.25 + slat1*0.5));
  const sf = Math.pow(Math.tan(Math.PI*0.25 + slat1*0.5), sn) * Math.cos(slat1) / sn;
  const ro = re * sf / Math.pow(Math.tan(Math.PI*0.25 + olat*0.5), sn);
  const ra = re * sf / Math.pow(Math.tan(Math.PI*0.25 + lat*D*0.5), sn);
  let theta = lon*D - olon;
  if (theta >  Math.PI) theta -= 2*Math.PI;
  if (theta < -Math.PI) theta += 2*Math.PI;
  theta *= sn;
  return {
    nx: Math.floor(ra * Math.sin(theta) + XO + 0.5),
    ny: Math.floor(ro - ra * Math.cos(theta) + YO + 0.5),
  };
}

let gx, gy;
if (nx != null && nx !== '' && ny != null && ny !== '') {
  gx = parseInt(nx, 10); gy = parseInt(ny, 10);
} else if (latitude != null && latitude !== '' && longitude != null && longitude !== '') {
  const g = latLonToGrid(Number(latitude), Number(longitude));
  gx = g.nx; gy = g.ny;
} else {
  throw new Error('Either (latitude, longitude) or (nx, ny) required');
}

function todayYmd() {
  const d = new Date();
  const pad = n => (n < 10 ? '0' + n : '' + n);
  return d.getUTCFullYear().toString() + pad(d.getUTCMonth() + 1) + pad(d.getUTCDate());
}
const bdate = (baseDate == null || baseDate === '') ? todayYmd() : String(baseDate).replace(/-/g, '');
const btime = (baseTime == null || baseTime === '') ? '0500'      : String(baseTime).padStart(4, '0');

const url = 'http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst'
          + '?serviceKey=' + encodeURIComponent(dataGoKrKmaKey)
          + '&pageNo=1&numOfRows=1000&dataType=JSON'
          + '&base_date=' + bdate + '&base_time=' + btime
          + '&nx=' + gx + '&ny=' + gy;

const resp = await fetch(url, {
  headers: { 'Accept': 'application/json' },
  maxLength: 3_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

const d = resp.json();
if (d && d.OpenAPI_ServiceResponse) {
  const hdr = d.OpenAPI_ServiceResponse.cmmMsgHeader || {};
  return {
    success: false, status: hdr.returnReasonCode || 'unknown',
    message: hdr.returnAuthMsg || hdr.errMsg || 'KMA service error',
  };
}

const body = (d.response && d.response.body) || {};
const items = (body.items && body.items.item) || [];

// Pivot long-format rows into one row per (fcstDate, fcstTime).
const SKY = { '1': '맑음', '3': '구름많음', '4': '흐림' };
const PTY = { '0': '없음',  '1': '비',      '2': '비/눈', '3': '눈', '4': '소나기' };
const slotsMap = new Map();
for (const row of items) {
  const key = row.fcstDate + 'T' + row.fcstTime;
  let slot = slotsMap.get(key);
  if (!slot) {
    slot = { fcstDate: row.fcstDate, fcstTime: row.fcstTime };
    slotsMap.set(key, slot);
  }
  const v = row.fcstValue;
  switch (row.category) {
    case 'TMP': slot.temp              = Number(v); break;
    case 'TMN': slot.minTemp           = Number(v); break;
    case 'TMX': slot.maxTemp           = Number(v); break;
    case 'REH': slot.humidity          = Number(v); break;
    case 'POP': slot.precipProbability = Number(v); break;
    case 'PCP': slot.precipAmount      = v;          break;
    case 'PTY': slot.precipType        = PTY[v] || v; break;
    case 'SKY': slot.skyCondition      = SKY[v] || v; break;
    case 'WSD': slot.windSpeed         = Number(v); break;
    case 'VEC': slot.windDirection     = Number(v); break;
  }
}
const forecasts = Array.from(slotsMap.values()).sort((a, b) =>
  (a.fcstDate + a.fcstTime).localeCompare(b.fcstDate + b.fcstTime));

return {
  baseDate:  bdate,
  baseTime:  btime,
  grid:      { nx: gx, ny: gy },
  count:     forecasts.length,
  forecasts: forecasts,
};

```

</div>
</div>

<div class="tcg-card t-molit tcg-card--clickable" id="getApartmentTradePrice" data-tool-id="getApartmentTradePrice" data-tool-title="getApartmentTradePrice" markdown>
<div class="tcg-name"><span class="tcg-name__text">getApartmentTradePrice</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:material-home-city-outline:</div>
<div class="tcg-type">web · korea · finance <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
MOLIT (Ministry of Land, Infrastructure & Transport) apartment sale transactions (KR; data.go.kr serviceKey required). Register the MOLIT apartment-trade data service at data.go.kr, receive a serviceKey, and set DATA_GO_KR_APT_KEY on the tool's staticVariables, or inject as env var. `lawdCode` is the 5-digit legal-dong city/county code (not the road-name address). Examples: Gangnam=11680, Seocho=11650, Songpa=11710, Haeundae=26350, Bundang(Seongnam)=41135, Jeju City=50110. `dealYmd` is the transaction month as YYYYMM. Returns: { lawdCode, dealYmd, totalCount, pageNo, numOfRows, items:[{ aptName, dealYear, dealMonth, dealDay, dealAmount(10K KRW), excluUseAr(sqm), floor, buildYear, umdNm(legal dong), jibun(lot), roadName, dealingType }] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `lawdCode` · `dealYmd` · `numOfRows` · `pageNo`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `DATA_GO_KR_APT_KEY`</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `lawdCode` | `STRING` | ✓ | 5-digit legal-dong city/county code (Gangnam=11680, Haeundae=26350, ...) |
| `dealYmd` | `STRING` | ✓ | Transaction month YYYYMM or YYYY-MM (e.g. 202604) |
| `numOfRows` | `STRING` |  | Results per page (1-1000, default 100) |
| `pageNo` | `STRING` |  | Page number (default 1) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `apis.data.go.kr` (SSRF-guarded); no filesystem.

**JS source**

```javascript
/**
 * 국토교통부 아파트 매매 실거래가 (KR; data.go.kr serviceKey 필요).
 *
 * GET http://apis.data.go.kr/1613000/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev
 *     ?serviceKey={KEY}&LAWD_CD=11680&DEAL_YMD=202604&pageNo=1&numOfRows=100
 *
 * LAWD_CD = 5자리 법정동 시군구 코드 (도로명 X). 자주 쓰는 값:
 *   강남구 11680, 서초구 11650, 송파구 11710, 마포구 11440, 성동구 11200,
 *   영등포구 11560, 용산구 11170, 종로구 11110, 중구 11140,
 *   해운대구(부산) 26350, 수영구(부산) 26410,
 *   분당구(성남) 41135, 일산동구(고양) 41281,
 *   제주시 50110, 서귀포시 50130.
 *
 * DEAL_YMD = 거래연월 YYYYMM (필수).
 *
 * data.go.kr 에서 `국토교통부_아파트매매 실거래자료` 신청 후 serviceKey 사용.
 */

if (lawdCode == null || lawdCode === '') throw new Error('lawdCode required (5자리 법정동 시군구 코드)');
if (dealYmd  == null || dealYmd  === '') throw new Error('dealYmd required (거래연월 YYYYMM)');

const ym = String(dealYmd).replace(/-/g, '').slice(0, 6);
if (!/^\d{6}$/.test(ym)) throw new Error('dealYmd must be YYYYMM');

const rows = (numOfRows == null || numOfRows === '') ? 100 : Math.max(1, Math.min(1000, parseInt(numOfRows, 10)));
const pg   = (pageNo    == null || pageNo    === '') ?   1 : Math.max(1, parseInt(pageNo, 10));

const url = 'http://apis.data.go.kr/1613000/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev'
          + '?serviceKey=' + encodeURIComponent(dataGoKrAptKey)
          + '&LAWD_CD='   + encodeURIComponent(String(lawdCode))
          + '&DEAL_YMD='  + ym
          + '&pageNo='    + pg
          + '&numOfRows=' + rows;

const resp = await fetch(url, {
  headers: { 'Accept': 'application/json' },
  maxLength: 3_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

const d = resp.json();
if (d && d.OpenAPI_ServiceResponse) {
  const hdr = d.OpenAPI_ServiceResponse.cmmMsgHeader || {};
  return {
    success: false, status: hdr.returnReasonCode || 'unknown',
    message: hdr.returnAuthMsg || hdr.errMsg || 'apt-trade service error',
  };
}

const body = (d.response && d.response.body) || {};
let itemsRaw = (body.items && body.items.item) || [];
if (itemsRaw && !Array.isArray(itemsRaw) && typeof itemsRaw === 'object') itemsRaw = [itemsRaw];

const num = v => v == null || v === '' ? null : Number(String(v).replace(/,/g, '').trim());

const items = itemsRaw.map(t => ({
  aptName:      (t.aptNm || t.아파트 || '').trim(),
  dealYear:     num(t.dealYear || t.년),
  dealMonth:    num(t.dealMonth || t.월),
  dealDay:      num(t.dealDay   || t.일),
  dealAmount:   num(t.dealAmount || t.거래금액),   // 단위: 만원
  excluUseAr:   num(t.excluUseAr || t.전용면적),   // 단위: ㎡
  floor:        num(t.floor      || t.층),
  buildYear:    num(t.buildYear  || t.건축년도),
  umdNm:        t.umdNm || t.법정동,
  jibun:        t.jibun || t.지번,
  roadName:     t.roadNm || t.도로명,
  dealingType:  t.dealingGbn || t.거래유형,        // 중개 / 직거래
}));

return {
  lawdCode:   String(lawdCode),
  dealYmd:    ym,
  totalCount: body.totalCount,
  pageNo:     body.pageNo,
  numOfRows:  body.numOfRows,
  items:      items,
};

```

</div>
</div>

<div class="tcg-card t-mfds tcg-card--clickable" id="searchKoreaDrugInfo" data-tool-id="searchKoreaDrugInfo" data-tool-title="searchKoreaDrugInfo" markdown>
<div class="tcg-name"><span class="tcg-name__text">searchKoreaDrugInfo</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:material-pill:</div>
<div class="tcg-type">web · korea · search <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
MFDS (Ministry of Food & Drug Safety) drug product approval search (KR; data.go.kr serviceKey required). Register the `MFDS drug product approval info` service at data.go.kr, receive a serviceKey, and set DATA_GO_KR_DRUG_KEY on the tool's staticVariables, or inject as env var. At least one of `itemName` (partial product name), `entpName` (company name), or `itemSeq` (product sequence) is required. Returns: { totalCount, pageNo, numOfRows, items:[{ itemSeq, itemName, entpName, itemPermitDate, className, storageMethod, packUnit, validTerm, cancelDate, cancelName, chart }] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `itemName` · `entpName` · `itemSeq` · `numOfRows` · `pageNo`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `DATA_GO_KR_DRUG_KEY`</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `itemName` | `STRING` |  | Korean string required - partial product name (e.g. '타이레놀', '아스피린'). |
| `entpName` | `STRING` |  | Optional. Company name in Korean (e.g. '한국얀센', '유한양행'). |
| `itemSeq` | `STRING` |  | Product sequence code (MFDS unique ID) |
| `numOfRows` | `STRING` |  | Results per page (1-100, default 10) |
| `pageNo` | `STRING` |  | Page number (default 1) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `apis.data.go.kr` (SSRF-guarded); no filesystem.

**JS source**

```javascript
/**
 * 식약처 의약품 품목허가 정보 검색 (KR; data.go.kr serviceKey 필요).
 *
 * GET http://apis.data.go.kr/1471000/MdcinPrductPrmsnInfoService02/getMdcinPrductItem02
 *     ?serviceKey={KEY}&type=json&pageNo=1&numOfRows=10
 *     &item_name=타이레놀&entp_name=&item_seq=
 *
 * 한 가지 이상의 검색 조건 필요: `itemName`(품목명, 부분일치),
 * `entpName`(업체명), `itemSeq`(품목 시퀀스코드).
 *
 * data.go.kr 에서 `식품의약품안전처_의약품 품목허가 정보` 신청 후 serviceKey 사용.
 */


const hasName = itemName != null && itemName !== '';
const hasEntp = entpName != null && entpName !== '';
const hasSeq  = itemSeq  != null && itemSeq  !== '';
if (!hasName && !hasEntp && !hasSeq)
  throw new Error('Provide at least one of: itemName / entpName / itemSeq');

const rows = (numOfRows == null || numOfRows === '') ? 10 : Math.max(1, Math.min(100, parseInt(numOfRows, 10)));
const pg   = (pageNo    == null || pageNo    === '') ?  1 : Math.max(1, parseInt(pageNo, 10));

let url = 'http://apis.data.go.kr/1471000/MdcinPrductPrmsnInfoService02/getMdcinPrductItem02'
        + '?serviceKey=' + encodeURIComponent(dataGoKrDrugKey)
        + '&type=json&pageNo=' + pg + '&numOfRows=' + rows;
if (hasName) url += '&item_name=' + encodeURIComponent(String(itemName));
if (hasEntp) url += '&entp_name=' + encodeURIComponent(String(entpName));
if (hasSeq ) url += '&item_seq='  + encodeURIComponent(String(itemSeq));

const resp = await fetch(url, {
  headers: { 'Accept': 'application/json' },
  maxLength: 3_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

const d = resp.json();
if (d && d.OpenAPI_ServiceResponse) {
  const hdr = d.OpenAPI_ServiceResponse.cmmMsgHeader || {};
  return {
    success: false, status: hdr.returnReasonCode || 'unknown',
    message: hdr.returnAuthMsg || hdr.errMsg || 'drug service error',
  };
}

const body = (d.body) || (d.response && d.response.body) || {};
let itemsRaw = body.items || [];
if (itemsRaw && itemsRaw.item) itemsRaw = itemsRaw.item;
if (itemsRaw && !Array.isArray(itemsRaw) && typeof itemsRaw === 'object') itemsRaw = [itemsRaw];

const items = (itemsRaw || []).map(t => ({
  itemSeq:        t.ITEM_SEQ        || t.item_seq,
  itemName:       t.ITEM_NAME       || t.item_name,
  entpName:       t.ENTP_NAME       || t.entp_name,
  itemPermitDate: t.ITEM_PERMIT_DATE|| t.item_permit_date,
  industryType:   t.INDUTY_TYPE     || t.induty_type,
  permittedType:  t.PRDLST_STDR_CODE|| t.prdlst_stdr_code,
  className:      t.CLASS_NAME      || t.class_name,
  storageMethod:  t.STORAGE_METHOD  || t.storage_method,
  packUnit:       t.PACK_UNIT       || t.pack_unit,
  validTerm:      t.VALID_TERM      || t.valid_term,
  cancelDate:     t.CANCEL_DATE     || t.cancel_date,
  cancelName:     t.CANCEL_NAME     || t.cancel_name,
  chart:          t.CHART           || t.chart,
}));

return {
  totalCount: body.totalCount || body.numOfRows,
  pageNo:     body.pageNo,
  numOfRows:  body.numOfRows,
  items:      items,
};

```

</div>
</div>

<div class="tcg-card t-mois tcg-card--clickable" id="getKoreaEmergencyAlerts" data-tool-id="getKoreaEmergencyAlerts" data-tool-title="getKoreaEmergencyAlerts" markdown>
<div class="tcg-name"><span class="tcg-name__text">getKoreaEmergencyAlerts</span> <span class="cost">🔑 × 1</span></div>
<div class="tcg-art" markdown>:material-alert-octagon-outline:</div>
<div class="tcg-type">web · korea <span class="risk risk-l3">L3</span></div>
<div class="tcg-body" markdown>
MOIS (Ministry of the Interior & Safety) emergency disaster-alert SMS history (KR; data.go.kr serviceKey required). Register the `MOIS emergency disaster alerts` service at data.go.kr, receive a serviceKey, and set DATA_GO_KR_DISASTER_KEY on the tool's staticVariables, or inject as env var. `area` matches the dispatch region name partially (Korean string). `fromDate`/`toDate` accept YYYYMMDD or YYYY-MM-DD. Returns: { totalCount, pageNo, numOfRows, items:[{ serialNo, createDate, message, emergencyStep, disasterType, location }] }.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Params** &nbsp; `area` · `fromDate` · `toDate` · `numOfRows` · `pageNo`</div>
<div class="tcg-stats__line" markdown>**Env** &nbsp; &nbsp; &nbsp; `DATA_GO_KR_DISASTER_KEY`</div>
</div>
<div class="tcg-cta">Click for full reference · params · sandbox · JS source</div>
<div class="tcg-detail-template" hidden markdown>

**Parameters**

| Param | Type | Req | Description |
|---|---|---|---|
| `area` | `STRING` |  | Korean string required when used - partial dispatch-region name (e.g. '서울특별시', '경기도', '부산광역시'). Omit for nationwide. |
| `fromDate` | `STRING` |  | Query start date YYYYMMDD or YYYY-MM-DD |
| `toDate` | `STRING` |  | Query end date YYYYMMDD or YYYY-MM-DD |
| `numOfRows` | `STRING` |  | Results per page (1-1000, default 20) |
| `pageNo` | `STRING` |  | Page number (default 1) |

**Sandbox** - **L3** (Scoped widening) - `fetch` allowlisted to `apis.data.go.kr` (SSRF-guarded); no filesystem.

**JS source**

```javascript
/**
 * 행정안전부 재난문자 발송 내역 (KR; data.go.kr serviceKey 필요).
 *
 * GET http://apis.data.go.kr/1741000/DisasterMsg3/getDisasterMsg1List
 *     ?serviceKey={KEY}&pageNo=1&numOfRows=20&type=json
 *     &fromTm=20260512&toTm=20260513&location_name=서울특별시
 *
 * data.go.kr 에서 `행정안전부_긴급재난문자` 서비스 신청 후 serviceKey 사용.
 * `area`(지역명 부분일치)와 `fromDate`/`toDate`(YYYYMMDD)는 모두 선택.
 */


const rows = (numOfRows == null || numOfRows === '') ? 20 : Math.max(1, Math.min(1000, parseInt(numOfRows, 10)));
const pg   = (pageNo    == null || pageNo    === '') ?  1 : Math.max(1, parseInt(pageNo, 10));

let url = 'http://apis.data.go.kr/1741000/DisasterMsg3/getDisasterMsg1List'
        + '?serviceKey=' + encodeURIComponent(dataGoKrDisasterKey)
        + '&pageNo=' + pg + '&numOfRows=' + rows + '&type=json';
if (fromDate != null && fromDate !== '') url += '&fromTm=' + encodeURIComponent(String(fromDate).replace(/-/g, ''));
if (toDate   != null && toDate   !== '') url += '&toTm='   + encodeURIComponent(String(toDate).replace(/-/g, ''));
if (area     != null && area     !== '') url += '&location_name=' + encodeURIComponent(String(area));

const resp = await fetch(url, {
  headers: { 'Accept': 'application/json' },
  maxLength: 3_000_000,
});
if (!resp.ok) return { success: false, status: resp.status, message: resp.text() };

const d = resp.json();
if (d && d.OpenAPI_ServiceResponse) {
  const hdr = d.OpenAPI_ServiceResponse.cmmMsgHeader || {};
  return {
    success: false, status: hdr.returnReasonCode || 'unknown',
    message: hdr.returnAuthMsg || hdr.errMsg || 'disaster-msg service error',
  };
}

const body = (d.body) || (d.response && d.response.body) || {};
let itemsRaw = body.items || [];
if (itemsRaw && itemsRaw.item) itemsRaw = itemsRaw.item;
if (itemsRaw && !Array.isArray(itemsRaw) && typeof itemsRaw === 'object') itemsRaw = [itemsRaw];

const items = (itemsRaw || []).map(t => ({
  serialNo:     t.SN          || t.sn        || t.serial_no,
  createDate:   t.CREATE_DATE || t.create_date || t.createDate,
  message:      t.MSG         || t.msg,
  emergencyStep:t.EMRG_STEP_NAME || t.emrgStepName,
  disasterType: t.DST_SE_NAME    || t.dstSeName,
  location:     t.LOCATION_NAME  || t.locationName || t.LOCATION_ID,
}));

return {
  totalCount: body.totalCount,
  pageNo:     body.pageNo,
  numOfRows:  body.numOfRows,
  items:      items,
};

```

</div>
</div>

</div>

## Composition patterns (KR-domain chains)

The Korea bundle is heavier on data sources, so most chains are *Korean source → analysis*:

- **Cross-exchange spread (kimchi premium)** - `getUpbitTicker(markets='KRW-BTC')` + `getBithumbTicker(symbol='BTC')` → `evalExpression(expression='(a-b)/b*100', variables={a, b})` for the live KRW spread between the two majors. No keys, no setup.
- **Local + weather** - `searchKakaoLocal(query)` for POI lat/lon → `getKmaShortTermForecast(latitude, longitude)` for the next 72h hourly forecast in the KMA grid - "Will it rain tomorrow at this Gyeongju temple?"
- **Tour + box office** - `searchKoreaTour(keyword)` for festivals in a region → `getKoficBoxOffice(targetDate)` to cross-reference what is also showing in cinemas that weekend.
- **Agri price tracker** - `getKamisAgriPrice(itemCode='211', startDay, endDay)` for napa cabbage (or any 4-digit KAMIS code) → `formatCsv` the rows → `writeTextFile` snapshot for a monthly price history.
- **Disaster → Slack** - `getKoreaEmergencyAlerts(area='경주', fromDate, toDate)` → conditional → `sendSlackMessage(text)` to fan out emergency-alert SMS history to a channel.

[Tutorial 8: Default Tool Recipes](../../tutorials/8-default-tool-recipes.md) walks the **cross-exchange spread** and **disaster → Slack** patterns end-to-end.

## Keys & secrets

Six of the 21 are no-key (Upbit, Bithumb, iTunes K-pop, Open Beauty Facts - see the cost badges on each card). The other fifteen pull from three keychains.

### Naver Open APIs

| Tool | Env var | Where to issue |
|---|---|---|
| `searchNaver` | `NAVER_CLIENT_ID` + `NAVER_CLIENT_SECRET` | Create an app at [developers.naver.com/apps/](https://developers.naver.com/apps/) → enable **Search API** → copy Client-Id / Secret |

### Kakao Developers

| Tool | Env var | Where to issue |
|---|---|---|
| `searchKakaoLocal` | `KAKAO_REST_API_KEY` | [developers.kakao.com](https://developers.kakao.com/) → My Application → REST API key |

### Seoul Open Data Plaza

| Tool | Env var | Where to issue |
|---|---|---|
| `searchSeoulCulturalEvents` | `SEOUL_OPEN_API_KEY` | [data.seoul.go.kr/together/apikey.do](https://data.seoul.go.kr/together/apikey.do) - 1 000 req/day free |

### KAMIS (aT Korea Agro-Fisheries)

| Tool | Env var | Where to issue |
|---|---|---|
| `getKamisAgriPrice` | `KAMIS_CERT_ID` + `KAMIS_CERT_KEY` | [kamis.or.kr/customer/reference/openapi_list.do](https://www.kamis.or.kr/customer/reference/openapi_list.do) - free credentials |

### KOFIC (Korean Film Council)

| Tool | Env var | Where to issue |
|---|---|---|
| `getKoficBoxOffice` | `KOFIC_API_KEY` | [kobis.or.kr/kobisopenapi/](https://www.kobis.or.kr/kobisopenapi/) - instant issuance |

### data.go.kr keychain (8 services)

`data.go.kr` issues one keystring per service registration even when the value happens to be the same across services. Each tool below needs its **own** service registration; the env var name distinguishes them:

| Tool | Env var | data.go.kr service |
|---|---|---|
| `getAirKoreaPm` | `DATA_GO_KR_AIR_KEY` | [data.go.kr/data/15073861/openapi.do](https://www.data.go.kr/data/15073861/openapi.do) - AirKorea air quality |
| `searchKoreaTour` | `DATA_GO_KR_TOUR_KEY` | [data.go.kr/data/15101578/openapi.do](https://www.data.go.kr/data/15101578/openapi.do) - KTO TourAPI 4.0 |
| `getKrxStockPrice` | `DATA_GO_KR_STOCK_KEY` | [data.go.kr/data/15094808/openapi.do](https://www.data.go.kr/data/15094808/openapi.do) - KRX stock quotes |
| `getKmaShortTermForecast` | `DATA_GO_KR_KMA_KEY` | [data.go.kr/data/15084084/openapi.do](https://www.data.go.kr/data/15084084/openapi.do) - KMA short-term forecast (VilageFcstInfoService_2.0) |
| `getApartmentTradePrice` | `DATA_GO_KR_APT_KEY` | [data.go.kr/data/15126468/openapi.do](https://www.data.go.kr/data/15126468/openapi.do) - MOLIT apartment-trade transactions (detailed) |
| `searchKoreaDrugInfo` | `DATA_GO_KR_DRUG_KEY` | [data.go.kr/data/15095677/openapi.do](https://www.data.go.kr/data/15095677/openapi.do) - MFDS drug-product approval |
| `getKoreaEmergencyAlerts` | `DATA_GO_KR_DISASTER_KEY` | [data.go.kr/data/15134001/openapi.do](https://www.data.go.kr/data/15134001/openapi.do) - MOIS emergency disaster alerts |
| `callDataGoKrOpenApi` | `DATA_GO_KR_KEY` | [data.go.kr/tcs/dss/selectDataSetList.do?dType=API](https://www.data.go.kr/tcs/dss/selectDataSetList.do?dType=API) - generic dispatcher; pick any other data.go.kr service from the API catalog |

The launcher's **Environment Variables** card is the recommended place to set the whole keychain at once. Each `${ENV_VAR}` placeholder on the tool's static variables resolves at runtime from the JVM environment; the resolved string is masked from `console.log` whenever it appears in the trace.

→ [Tool Studio: Static Variables](../tool-studio/index.md#key-tool-studio-capabilities) - how the masking works.
