/*
 * Copyright © 2025 Jemin Huh (hjm1980@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springaicommunity.playground.service.tool.runtime;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.JsSandbox;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.NetworkPolicy;
import org.springaicommunity.playground.service.tool.policy.EffectivePolicyResolver.EffectivePolicy;
import org.springaicommunity.playground.service.tool.runtime.JsToolExecutor.JsExecutionParams;
import org.springaicommunity.playground.service.tool.runtime.JsToolExecutor.JsExecutionResult;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JsToolNetworkExamplesTest {

    private static HttpServer server;
    private static String baseUrl;
    private static List<Map<String, Object>> specs;
    private static JsToolExecutor executor;

    @BeforeAll
    static void boot() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/wttr/", JsToolNetworkExamplesTest::wttrHandler);
        server.createContext("/customsearch/v1", JsToolNetworkExamplesTest::pseHandler);
        server.createContext("/v1/responses", JsToolNetworkExamplesTest::openaiHandler);
        server.createContext("/slack/", JsToolNetworkExamplesTest::slackHandler);
        server.createContext("/repos/", JsToolNetworkExamplesTest::githubHandler);
        server.createContext("/users/", JsToolNetworkExamplesTest::githubUserHandler);
        server.createContext("/search/repositories", JsToolNetworkExamplesTest::githubSearchReposHandler);
        server.createContext("/api/v3/simple/price", JsToolNetworkExamplesTest::coingeckoHandler);
        server.createContext("/v6/latest/", JsToolNetworkExamplesTest::erApiHandler);
        server.createContext("/v1/ticker", JsToolNetworkExamplesTest::upbitHandler);
        server.createContext("/v1/orderbook", JsToolNetworkExamplesTest::upbitOrderbookHandler);
        server.createContext("/v1/candles/", JsToolNetworkExamplesTest::upbitCandlesHandler);
        server.createContext("/v1/market/all", JsToolNetworkExamplesTest::upbitMarketsHandler);
        server.createContext("/public/ticker/", JsToolNetworkExamplesTest::bithumbHandler);
        server.createContext("/public/orderbook/", JsToolNetworkExamplesTest::bithumbOrderbookHandler);
        server.createContext("/8.8.8.8/json/", JsToolNetworkExamplesTest::ipapiHandler);
        server.createContext("/gh/mledoze/", JsToolNetworkExamplesTest::countriesHandler);
        server.createContext("/api/query", JsToolNetworkExamplesTest::arxivHandler);
        server.createContext("/api/v3/PublicHolidays/", JsToolNetworkExamplesTest::nagerHandler);
        server.createContext("/r/", JsToolNetworkExamplesTest::redditHandler);
        server.createContext("/v1/forecast", JsToolNetworkExamplesTest::openMeteoHandler);
        server.createContext("/search", JsToolNetworkExamplesTest::nominatimHandler);
        server.createContext("/json", JsToolNetworkExamplesTest::sunHandler);
        server.createContext("/fdsnws/event/1/query", JsToolNetworkExamplesTest::usgsHandler);
        server.createContext("/page/", JsToolNetworkExamplesTest::pageHandler);
        server.createContext("/api/rest_v1/page/summary/", JsToolNetworkExamplesTest::wikipediaHandler);
        server.createContext("/api/v1/search", JsToolNetworkExamplesTest::hackerNewsHandler);
        server.createContext("/2.3/search/advanced", JsToolNetworkExamplesTest::stackOverflowHandler);
        server.createContext("/v1/search/", JsToolNetworkExamplesTest::naverHandler);
        server.createContext("/v2/local/search/keyword.json", JsToolNetworkExamplesTest::kakaoLocalHandler);
        server.createContext("/B552584/ArpltnInforInqireSvc/getCtprvnRltmMesureDnsty",
                JsToolNetworkExamplesTest::airKoreaHandler);
        server.createContext("/B551011/KorService2/searchKeyword2",
                JsToolNetworkExamplesTest::tourApiHandler);
        server.createContext("/api/v2/search",
                JsToolNetworkExamplesTest::openBeautyFactsHandler);
        server.createContext("/fake-seoul-key/json/culturalEventInfo/",
                JsToolNetworkExamplesTest::seoulOpenDataHandler);
        server.createContext("/BAD/json/culturalEventInfo/",
                JsToolNetworkExamplesTest::seoulOpenDataBadKeyHandler);
        server.createContext("/service/price/xml.do",
                JsToolNetworkExamplesTest::kamisHandler);
        server.createContext("/kobisopenapi/webservice/rest/boxoffice/searchDailyBoxOfficeList.json",
                JsToolNetworkExamplesTest::koficHandler);
        server.createContext("/1160100/service/GetStockSecuritiesInfoService/getStockPriceInfo",
                JsToolNetworkExamplesTest::krxStockHandler);
        server.createContext("/1741000/StanReginCd/getStanReginCdList",
                JsToolNetworkExamplesTest::dataGoKrGenericHandler);
        server.createContext("/B999999/will/explode",
                JsToolNetworkExamplesTest::dataGoKrGenericErrorHandler);
        server.createContext("/1360000/VilageFcstInfoService_2.0/getVilageFcst",
                JsToolNetworkExamplesTest::kmaForecastHandler);
        server.createContext("/1613000/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev",
                JsToolNetworkExamplesTest::aptTradeHandler);
        server.createContext("/1471000/MdcinPrductPrmsnInfoService02/getMdcinPrductItem02",
                JsToolNetworkExamplesTest::drugInfoHandler);
        server.createContext("/1741000/DisasterMsg3/getDisasterMsg1List",
                JsToolNetworkExamplesTest::disasterMsgHandler);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        ObjectMapper mapper = new ObjectMapper();
        specs = new ArrayList<>();
        for (String fname : List.of("/tool/default-tool-specs.json",
                "/tool/default-tool-specs-network.json",
                "/tool/default-tool-specs-kr.json")) {
            try (InputStream in = JsToolNetworkExamplesTest.class.getResourceAsStream(fname)) {
                if (in == null) continue;
                specs.addAll(mapper.readValue(in, new TypeReference<List<Map<String, Object>>>() {}));
            }
        }
        executor = new JsToolExecutor(30L,
                new JsSandbox(true, false, false, false, 5_000_000L, Set.of(),
                        Set.of("java.lang.*", "java.util.*", "org.jsoup.*"), Map.of()),
                Path.of(System.getProperty("java.io.tmpdir")));
    }

    @AfterAll
    static void shutdown() {
        if (server != null) server.stop(0);
    }

    @SuppressWarnings("SameParameterValue")
    private static EffectivePolicy allowlist(String host) {
        NetworkPolicy net = new NetworkPolicy("allowlist", Set.of(host), Set.of());
        return new EffectivePolicy(
                Set.of("java.lang.*", "java.util.*", "org.jsoup.*"),
                Set.of(), true, false, false, false, false, 5_000_000L, 30L, true, net, null, null);
    }

    private static String code(String name) {
        return specs.stream().filter(s -> name.equals(s.get("name"))).findFirst()
                .map(s -> (String) s.get("code")).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<String> declaredNamesFor(String name) {
        return specs.stream().filter(s -> name.equals(s.get("name"))).findFirst()
                .map(s -> (List<Map<String, Object>>) s.get("params"))
                .orElse(List.of()).stream()
                .map(param -> (String) param.get("name"))
                .filter(paramName -> paramName != null && !paramName.isBlank())
                .toList();
    }

    @Test
    void extractPageContentParsesHtmlViaHostFetch() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of("pageUrl", baseUrl + "/page/demo"),
                        code("extractPageContent"), declaredNamesFor("extractPageContent")),
                allowlist("127.0.0.1"));
        assertThat(result.isOk()).as("%s", result.error()).isTrue();
        String payload = (String) result.result();
        assertThat(payload).contains("Hello title");
        assertThat(payload).contains("\"links\":");
    }

    @Test
    void getWeatherReadsWttrFields() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of("location", "Seoul"),
                        code("getWeather").replace("https://wttr.in/", baseUrl + "/wttr/"), declaredNamesFor("getWeather")),
                allowlist("127.0.0.1"));
        assertThat(result.isOk()).as("%s", result.error()).isTrue();
        Map<?, ?> r = (Map<?, ?>) result.result();
        assertThat(r.get("location")).isEqualTo("MockTown");
        assertThat(r.get("tempC")).isEqualTo(21);
        assertThat(r.get("humidity")).isEqualTo(55);
        assertThat(r.get("windSpeed")).isEqualTo("12 km/h");
        assertThat(r.get("windDirection")).isEqualTo("SE");
    }

    @Test
    void googlePseSearchReturnsItems() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(
                        Map.of("query", "spring ai", "resultNum", 3L, "startPage", 1L,
                               "googleApiKey", "fake-key", "pseId", "fake-id"),
                        code("googlePseSearch").replace("https://www.googleapis.com/customsearch/v1",
                                baseUrl + "/customsearch/v1"), declaredNamesFor("googlePseSearch")),
                allowlist("127.0.0.1"));
        assertThat(result.isOk()).as("%s", result.error()).isTrue();
        Map<?, ?> r = (Map<?, ?>) result.result();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) r.get("items");
        assertThat(items).hasSize(2);
        assertThat(items.getFirst().get("title")).isEqualTo("Result 1");
    }

    @Test
    void openaiResponseGeneratorMapsOutputMessage() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(
                        Map.of("prompt", "hello", "model", "gpt-test",
                               "openaiApiKey", "sk-fake"),
                        code("openaiResponseGenerator").replace("https://api.openai.com/v1/responses",
                                baseUrl + "/v1/responses"), declaredNamesFor("openaiResponseGenerator")),
                allowlist("127.0.0.1"));
        assertThat(result.isOk()).as("%s", result.error()).isTrue();
        Map<?, ?> r = (Map<?, ?>) result.result();
        assertThat(r.get("content")).isEqualTo("OpenAI mock reply");
        assertThat(r.get("reasoning")).isEqualTo("Mock reasoning");
    }

    @Test
    void sendSlackMessagePostsBodyAndReturnsOk() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(
                        Map.of("text", "hi", "slackWebhookUrl", baseUrl + "/slack/webhook"),
                        code("sendSlackMessage"), declaredNamesFor("sendSlackMessage")),
                allowlist("127.0.0.1"));
        assertThat(result.isOk()).as("%s", result.error()).isTrue();
        Map<?, ?> r = (Map<?, ?>) result.result();
        assertThat(r.get("status")).isEqualTo("ok");
    }

    @Test
    void getGithubRepoMapsAllRelevantFields() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(
                        Map.of("owner", "spring-projects", "repo", "spring-ai"),
                        code("getGithubRepo").replace("https://api.github.com",
                                baseUrl), declaredNamesFor("getGithubRepo")),
                allowlist("127.0.0.1"));
        assertThat(result.isOk()).as("%s", result.error()).isTrue();
        Map<?, ?> r = (Map<?, ?>) result.result();
        assertThat(r.get("fullName")).isEqualTo("spring-projects/spring-ai");
        assertThat(r.get("stars")).isEqualTo(1234);
        assertThat(r.get("forks")).isEqualTo(56);
        assertThat(r.get("language")).isEqualTo("Java");
        assertThat(r.get("license")).isEqualTo("Apache-2.0");
        @SuppressWarnings("unchecked")
        List<String> topics = (List<String>) r.get("topics");
        assertThat(topics).contains("ai", "spring");
    }

    @Test
    void searchWikipediaReturnsSummary() {
        String patched = code("searchWikipedia").replace(
                "'https://' + language + '.wikipedia.org/api/rest_v1/page/summary/'",
                "'" + baseUrl + "/api/rest_v1/page/summary/'");
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(
                        Map.of("title", "Spring Framework", "lang", "en"),
                        patched),
                allowlist("127.0.0.1"));
        assertThat(result.isOk()).as("%s", result.error()).isTrue();
        Map<?, ?> r = (Map<?, ?>) result.result();
        assertThat(r.get("title")).isEqualTo("Spring Framework");
        assertThat(r.get("extract")).isEqualTo("Mock summary of Spring Framework.");
    }

    @Test
    void searchHackerNewsReturnsHits() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(
                        Map.of("query", "spring ai", "hits", 3L, "tag", "story"),
                        code("searchHackerNews").replace("https://hn.algolia.com",
                                baseUrl), declaredNamesFor("searchHackerNews")),
                allowlist("127.0.0.1"));
        assertThat(result.isOk()).as("%s", result.error()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hits = (List<Map<String, Object>>) result.result();
        assertThat(hits).hasSize(2);
        assertThat(hits.getFirst().get("title")).isEqualTo("HN Story 1");
        assertThat(hits.getFirst().get("hnLink").toString()).contains("news.ycombinator.com");
    }

    @Test
    void searchStackOverflowReturnsItems() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(
                        Map.of("query", "spring ai", "pageSize", 3L,
                               "sort", "relevance", "tags", "spring;java"),
                        code("searchStackOverflow").replace("https://api.stackexchange.com",
                                baseUrl), declaredNamesFor("searchStackOverflow")),
                allowlist("127.0.0.1"));
        assertThat(result.isOk()).as("%s", result.error()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.result();
        assertThat(items).hasSize(2);
        assertThat(items.getFirst().get("title")).isEqualTo("SO Question 1");
        assertThat(items.getFirst().get("isAnswered")).isEqualTo(true);
    }

    @Test
    void getGithubUserReturnsProfile() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of("login", "spring-projects"),
                        code("getGithubUser").replace("https://api.github.com", baseUrl), declaredNamesFor("getGithubUser")),
                allowlist("127.0.0.1"));
        assertThat(result.isOk()).as("%s", result.error()).isTrue();
        Map<?, ?> r = (Map<?, ?>) result.result();
        assertThat(r.get("login")).isEqualTo("spring-projects");
        assertThat(r.get("type")).isEqualTo("Organization");
        assertThat(((Number) r.get("publicRepos")).intValue()).isEqualTo(120);
    }

    @Test
    void listGithubRepoIssuesFiltersOutPullRequests() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(
                        Map.of("owner", "spring-projects", "repo", "spring-ai",
                               "state", "open", "perPage", 10L, "page", 1L),
                        code("listGithubRepoIssues").replace("https://api.github.com", baseUrl), declaredNamesFor("listGithubRepoIssues")),
                allowlist("127.0.0.1"));
        assertThat(result.isOk()).as("%s", result.error()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> issues = (List<Map<String, Object>>) result.result();
        assertThat(issues).hasSize(2);
        assertThat(issues.getFirst().get("title")).isEqualTo("First issue");
    }

    @Test
    void listGithubRepoReleasesReturnsTagAndAssets() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(
                        Map.of("owner", "spring-projects", "repo", "spring-ai", "perPage", 5L),
                        code("listGithubRepoReleases").replace("https://api.github.com", baseUrl), declaredNamesFor("listGithubRepoReleases")),
                allowlist("127.0.0.1"));
        assertThat(result.isOk()).as("%s", result.error()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rels = (List<Map<String, Object>>) result.result();
        assertThat(rels).hasSize(2);
        assertThat(rels.getFirst().get("tag")).isEqualTo("v1.0.0");
    }

    @Test
    void getGithubLatestReleaseReturnsAssetsList() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(
                        Map.of("owner", "spring-projects", "repo", "spring-ai"),
                        code("getGithubLatestRelease").replace("https://api.github.com", baseUrl), declaredNamesFor("getGithubLatestRelease")),
                allowlist("127.0.0.1"));
        assertThat(result.isOk()).as("%s", result.error()).isTrue();
        Map<?, ?> r = (Map<?, ?>) result.result();
        assertThat(r.get("tag")).isEqualTo("v1.0.0");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assets = (List<Map<String, Object>>) r.get("assets");
        assertThat(assets).hasSize(1);
        assertThat(assets.getFirst().get("name")).isEqualTo("dist.zip");
    }

    @Test
    void getGithubFileContentDecodesBase64() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(
                        Map.of("owner", "spring-projects", "repo", "spring-ai",
                               "path", "README.adoc", "ref", "main"),
                        code("getGithubFileContent").replace("https://api.github.com", baseUrl), declaredNamesFor("getGithubFileContent")),
                allowlist("127.0.0.1"));
        assertThat(result.isOk()).as("%s", result.error()).isTrue();
        Map<?, ?> r = (Map<?, ?>) result.result();
        assertThat(r.get("name")).isEqualTo("README.adoc");
        assertThat(r.get("content")).isEqualTo("hello readme\n");
    }

    @Test
    void searchGithubReposReturnsHits() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(
                        Map.of("query", "spring-ai", "sort", "stars", "perPage", 5L),
                        code("searchGithubRepos").replace("https://api.github.com", baseUrl), declaredNamesFor("searchGithubRepos")),
                allowlist("127.0.0.1"));
        assertThat(result.isOk()).as("%s", result.error()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.result();
        assertThat(items).hasSize(2);
        assertThat(items.getFirst().get("fullName")).isEqualTo("spring-projects/spring-ai");
    }

    @Test
    void listGithubRepoContributorsReturnsSortedByCommits() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(
                        Map.of("owner", "spring-projects", "repo", "spring-ai", "perPage", 10L),
                        code("listGithubRepoContributors").replace("https://api.github.com", baseUrl), declaredNamesFor("listGithubRepoContributors")),
                allowlist("127.0.0.1"));
        assertThat(result.isOk()).as("%s", result.error()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contribs = (List<Map<String, Object>>) result.result();
        assertThat(contribs).hasSize(2);
        assertThat(contribs.getFirst().get("login")).isEqualTo("alice");
        assertThat(((Number) contribs.getFirst().get("contributions")).intValue()).isEqualTo(150);
    }

    @Test
    void getCryptoPriceReturnsMap() {
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(Map.of("ids", "bitcoin,ethereum", "currencies", "usd,krw"),
                        code("getCryptoPrice").replace("https://api.coingecko.com", baseUrl), declaredNamesFor("getCryptoPrice")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        @SuppressWarnings("unchecked")
        Map<String, Object> btc = (Map<String, Object>) m.get("bitcoin");
        assertThat(((Number) btc.get("usd")).intValue()).isEqualTo(62000);
        assertThat(((Number) btc.get("krw")).longValue()).isEqualTo(84_000_000L);
    }

    @Test
    void convertCurrencyReturnsRateAndResult() {
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(Map.of("from", "USD", "to", "KRW", "amount", 100.0),
                        code("convertCurrency").replace("https://open.er-api.com", baseUrl), declaredNamesFor("convertCurrency")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(m.get("from")).isEqualTo("USD");
        assertThat(m.get("to")).isEqualTo("KRW");
        assertThat(((Number) m.get("rate")).doubleValue()).isEqualTo(1300.0);
        assertThat(((Number) m.get("result")).doubleValue()).isEqualTo(130000.0);
    }

    @Test
    void getUpbitTickerNormalises() {
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(Map.of("markets", "KRW-BTC,KRW-ETH"),
                        code("getUpbitTicker").replace("https://api.upbit.com", baseUrl), declaredNamesFor("getUpbitTicker")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tickers = (List<Map<String, Object>>) r.result();
        assertThat(tickers).hasSize(2);
        assertThat(tickers.getFirst().get("market")).isEqualTo("KRW-BTC");
        assertThat(((Number) tickers.getFirst().get("tradePrice")).longValue()).isEqualTo(85_000_000L);
    }

    @Test
    void getBithumbTickerSurfacesError() {
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(Map.of("symbol", "BTC"),
                        code("getBithumbTicker").replace("https://api.bithumb.com", baseUrl), declaredNamesFor("getBithumbTicker")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(m.get("symbol")).isEqualTo("BTC");
        assertThat(((Number) m.get("closingPrice")).intValue()).isEqualTo(86_000_000);
    }

    @Test
    void getUpbitOrderbookProjectsBidsAndAsks() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("markets", "KRW-BTC");
        args.put("level", "");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("getUpbitOrderbook").replace("https://api.upbit.com", baseUrl), declaredNamesFor("getUpbitOrderbook")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> books = (List<Map<String, Object>>) r.result();
        assertThat(books).hasSize(1);
        Map<String, Object> book = books.getFirst();
        assertThat(book.get("market")).isEqualTo("KRW-BTC");
        assertThat(((Number) book.get("totalAskSize")).doubleValue()).isEqualTo(1.5);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> units = (List<Map<String, Object>>) book.get("units");
        assertThat(units).hasSize(2);
        assertThat(((Number) units.getFirst().get("askPrice")).longValue()).isEqualTo(85_100_000L);
    }

    @Test
    void getUpbitCandlesReturnsOhlcv() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("market", "KRW-BTC");
        args.put("interval", "days");
        args.put("count", "5");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("getUpbitCandles").replace("https://api.upbit.com", baseUrl), declaredNamesFor("getUpbitCandles")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candles = (List<Map<String, Object>>) r.result();
        assertThat(candles).hasSize(2);
        Map<String, Object> first = candles.getFirst();
        assertThat(first.get("market")).isEqualTo("KRW-BTC");
        assertThat(((Number) first.get("openingPrice")).longValue()).isEqualTo(84_000_000L);
        assertThat(((Number) first.get("highPrice")).longValue()).isEqualTo(86_000_000L);
        assertThat(((Number) first.get("tradePrice")).longValue()).isEqualTo(85_000_000L);
    }

    @Test
    void getUpbitCandlesAcceptsMinuteInterval() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("market", "KRW-BTC");
        args.put("interval", "minutes/60");
        args.put("count", "3");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("getUpbitCandles").replace("https://api.upbit.com", baseUrl), declaredNamesFor("getUpbitCandles")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candles = (List<Map<String, Object>>) r.result();
        assertThat(candles).hasSize(2);
        assertThat(candles.getFirst().get("market")).isEqualTo("KRW-BTC");
    }

    @Test
    void listUpbitMarketsFiltersByQuote() {
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(Map.of("quote", "KRW"),
                        code("listUpbitMarkets").replace("https://api.upbit.com", baseUrl), declaredNamesFor("listUpbitMarkets")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(((Number) m.get("count")).intValue()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> markets = (List<Map<String, Object>>) m.get("markets");
        assertThat(markets).extracting(row -> row.get("market"))
                .containsExactly("KRW-BTC", "KRW-ETH");
    }

    @Test
    void listUpbitMarketsWithoutFilterReturnsAll() {
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(Map.of("quote", ""),
                        code("listUpbitMarkets").replace("https://api.upbit.com", baseUrl), declaredNamesFor("listUpbitMarkets")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(((Number) m.get("count")).intValue()).isEqualTo(3);
    }

    @Test
    void searchNaverStripsHighlightTags() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", "스프링 AI");
        args.put("type", "blog");
        args.put("display", "5");
        args.put("start", "1");
        args.put("naverClientId", "fake-id");
        args.put("naverClientSecret", "fake-secret");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("searchNaver").replace("https://openapi.naver.com", baseUrl), declaredNamesFor("searchNaver")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(m.get("type")).isEqualTo("blog");
        assertThat(((Number) m.get("total")).intValue()).isEqualTo(42);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) m.get("items");
        assertThat(items).hasSize(2);
        assertThat(items.getFirst().get("title")).isEqualTo("스프링 AI 입문");
        assertThat(items.getFirst().get("description")).isEqualTo("Spring AI 첫 글");
    }

    @Test
    void searchKakaoLocalReturnsPoisWithCoordinates() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", "강남역 스타벅스");
        args.put("size", "5");
        args.put("page", "1");
        args.put("longitude", "");
        args.put("latitude", "");
        args.put("radius", "");
        args.put("kakaoRestApiKey", "fake-kakao-key");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("searchKakaoLocal").replace("https://dapi.kakao.com", baseUrl), declaredNamesFor("searchKakaoLocal")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(((Number) m.get("totalCount")).intValue()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> places = (List<Map<String, Object>>) m.get("places");
        assertThat(places).hasSize(2);
        assertThat(places.getFirst().get("name")).isEqualTo("스타벅스 강남역점");
        assertThat(((Number) places.getFirst().get("latitude")).doubleValue()).isEqualTo(37.4979);
        assertThat(((Number) places.getFirst().get("longitude")).doubleValue()).isEqualTo(127.0276);
    }

    @Test
    void getAirKoreaPmCoercesDashToNull() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("sidoName", "서울");
        args.put("numOfRows", "20");
        args.put("dataGoKrAirKey", "fake-datagokr-key");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("getAirKoreaPm").replace("http://apis.data.go.kr", baseUrl), declaredNamesFor("getAirKoreaPm")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(m.get("sidoName")).isEqualTo("서울");
        assertThat(((Number) m.get("totalCount")).intValue()).isEqualTo(3);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stations = (List<Map<String, Object>>) m.get("stations");
        assertThat(stations).hasSize(3);
        assertThat(((Number) stations.getFirst().get("pm10")).intValue()).isEqualTo(35);
        assertThat(((Number) stations.getFirst().get("pm25")).intValue()).isEqualTo(18);
        assertThat(stations.getFirst().get("khaiGrade")).isEqualTo("2");
        assertThat(stations.get(1).get("pm10")).isNull();
    }

    @Test
    void searchNaverSurfaces401AsStructuredError() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", "스프링 AI");
        args.put("type", "blog");
        args.put("display", "5");
        args.put("start", "1");
        args.put("naverClientId", "BAD");
        args.put("naverClientSecret", "BAD");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("searchNaver").replace("https://openapi.naver.com", baseUrl), declaredNamesFor("searchNaver")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(m.get("success")).isEqualTo(false);
        assertThat(((Number) m.get("status")).intValue()).isEqualTo(401);
        assertThat(m.get("message").toString()).contains("Authentication failed");
    }

    @Test
    void searchNaverRejectsUnresolvedPlaceholder() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", "x");
        args.put("type", "blog");
        args.put("display", "");
        args.put("start", "");
        args.put("naverClientId", "${NAVER_CLIENT_ID}");
        args.put("naverClientSecret", "${NAVER_CLIENT_SECRET}");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("searchNaver").replace("https://openapi.naver.com", baseUrl), declaredNamesFor("searchNaver")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).isFalse();
        assertThat(r.error()).contains("naverClientId env var not set");
    }

    @Test
    void searchKakaoLocalSurfaces401AsStructuredError() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", "강남역");
        args.put("size", "5");
        args.put("page", "1");
        args.put("longitude", "");
        args.put("latitude", "");
        args.put("radius", "");
        args.put("kakaoRestApiKey", "BAD");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("searchKakaoLocal").replace("https://dapi.kakao.com", baseUrl), declaredNamesFor("searchKakaoLocal")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(m.get("success")).isEqualTo(false);
        assertThat(((Number) m.get("status")).intValue()).isEqualTo(401);
        assertThat(m.get("message").toString()).contains("AppKey is invalid");
    }

    @Test
    void getAirKoreaPmDetectsBadServiceKeyEnvelope() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("sidoName", "서울");
        args.put("numOfRows", "20");
        args.put("dataGoKrAirKey", "BAD");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("getAirKoreaPm").replace("http://apis.data.go.kr", baseUrl), declaredNamesFor("getAirKoreaPm")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(m.get("success")).isEqualTo(false);
        assertThat(m.get("status")).isEqualTo("30");
        assertThat(m.get("message").toString()).contains("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
    }

    @Test
    void searchKoreaTourReturnsAttractions() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("keyword", "경복궁");
        args.put("areaCode", "");
        args.put("sigunguCode", "");
        args.put("contentTypeId", "");
        args.put("pageNo", "1");
        args.put("numOfRows", "5");
        args.put("dataGoKrTourKey", "fake-tour-key");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("searchKoreaTour").replace("http://apis.data.go.kr", baseUrl), declaredNamesFor("searchKoreaTour")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(m.get("keyword")).isEqualTo("경복궁");
        assertThat(((Number) m.get("totalCount")).intValue()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) m.get("items");
        assertThat(items).hasSize(2);
        assertThat(items.getFirst().get("title")).isEqualTo("경복궁");
        assertThat(items.getFirst().get("contentTypeId")).isEqualTo("12");
        assertThat(((Number) items.getFirst().get("mapX")).doubleValue()).isEqualTo(126.977);
        assertThat(((Number) items.getFirst().get("mapY")).doubleValue()).isEqualTo(37.5796);
    }

    @Test
    void searchKoreaTourSendsSigunguCodeForCityLevelFilter() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("keyword", "불국사");
        args.put("areaCode", "35");
        args.put("sigunguCode", "2");
        args.put("contentTypeId", "12");
        args.put("pageNo", "1");
        args.put("numOfRows", "5");
        args.put("dataGoKrTourKey", "fake-tour-key");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("searchKoreaTour").replace("http://apis.data.go.kr", baseUrl), declaredNamesFor("searchKoreaTour")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(m.get("keyword")).isEqualTo("불국사");
    }

    @Test
    void searchKoreaTourDetectsBadServiceKeyEnvelope() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("keyword", "경복궁");
        args.put("areaCode", "");
        args.put("sigunguCode", "");
        args.put("contentTypeId", "");
        args.put("pageNo", "1");
        args.put("numOfRows", "5");
        args.put("dataGoKrTourKey", "BAD");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("searchKoreaTour").replace("http://apis.data.go.kr", baseUrl), declaredNamesFor("searchKoreaTour")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(m.get("success")).isEqualTo(false);
        assertThat(m.get("status")).isEqualTo("30");
    }

    @Test
    void searchSeoulCulturalEventsProjectsRows() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("startIndex", "1");
        args.put("endIndex", "10");
        args.put("codename", "");
        args.put("titleSearch", "");
        args.put("eventDate", "");
        args.put("seoulOpenApiKey", "fake-seoul-key");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("searchSeoulCulturalEvents").replace(
                                "http://openapi.seoul.go.kr:8088", baseUrl), declaredNamesFor("searchSeoulCulturalEvents")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(((Number) m.get("totalCount")).intValue()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) m.get("events");
        assertThat(events).hasSize(2);
        assertThat(events.getFirst().get("title")).isEqualTo("서울 봄꽃 페스티벌");
        assertThat(events.getFirst().get("gu")).isEqualTo("중구");
        assertThat(events.getFirst().get("isFree")).isEqualTo(true);
        assertThat(((Number) events.getFirst().get("latitude")).doubleValue()).isEqualTo(37.5663);
        assertThat(((Number) events.getFirst().get("longitude")).doubleValue()).isEqualTo(127.0091);
    }

    @Test
    void searchSeoulCulturalEventsDetectsBadKeyEnvelope() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("startIndex", "1");
        args.put("endIndex", "10");
        args.put("codename", "");
        args.put("titleSearch", "");
        args.put("eventDate", "");
        args.put("seoulOpenApiKey", "BAD");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("searchSeoulCulturalEvents").replace(
                                "http://openapi.seoul.go.kr:8088", baseUrl), declaredNamesFor("searchSeoulCulturalEvents")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(m.get("success")).isEqualTo(false);
        assertThat(m.get("status")).isEqualTo("INFO-100");
        assertThat(m.get("message").toString()).contains("인증키");
    }

    @Test
    void getKamisAgriPriceParsesItems() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("itemCode", "411");
        args.put("startDay", "2026-05-01");
        args.put("endDay", "2026-05-13");
        args.put("productClsCode", "02");
        args.put("itemCategoryCode", "400");
        args.put("kindCode", "");
        args.put("kamisCertId", "fake-id");
        args.put("kamisCertKey", "fake-key");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("getKamisAgriPrice").replace("http://www.kamis.or.kr", baseUrl), declaredNamesFor("getKamisAgriPrice")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(m.get("productClass")).isEqualTo("도매");
        assertThat(((Number) m.get("count")).intValue()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) m.get("rows");
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().get("itemName")).isEqualTo("사과");
        assertThat(((Number) rows.getFirst().get("price")).intValue()).isEqualTo(30000);
    }

    @Test
    void getKoficBoxOfficeProjectsRanking() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("targetDate", "20260512");
        args.put("multiMovieYn", "");
        args.put("repNationCd", "");
        args.put("wideAreaCd", "");
        args.put("koficApiKey", "fake-kofic-key");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("getKoficBoxOffice").replace("http://www.kobis.or.kr", baseUrl), declaredNamesFor("getKoficBoxOffice")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(m.get("type")).isEqualTo("일별 박스오피스");
        assertThat(((Number) m.get("count")).intValue()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> movies = (List<Map<String, Object>>) m.get("movies");
        assertThat(movies).hasSize(2);
        assertThat(((Number) movies.getFirst().get("rank")).intValue()).isEqualTo(1);
        assertThat(movies.getFirst().get("title")).isEqualTo("범죄도시 5");
        assertThat(((Number) movies.getFirst().get("salesAmount")).longValue()).isEqualTo(1_000_000_000L);
        assertThat(movies.getFirst().get("isNew")).isEqualTo(false);
        assertThat(movies.get(1).get("isNew")).isEqualTo(true);
    }

    @Test
    void getKoficBoxOfficeDetectsFaultInfo() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("targetDate", "20260512");
        args.put("multiMovieYn", "");
        args.put("repNationCd", "");
        args.put("wideAreaCd", "");
        args.put("koficApiKey", "BAD");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("getKoficBoxOffice").replace("http://www.kobis.or.kr", baseUrl), declaredNamesFor("getKoficBoxOffice")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(m.get("success")).isEqualTo(false);
        assertThat(m.get("status")).isEqualTo("100");
        assertThat(m.get("message").toString()).contains("인증키");
    }

    @Test
    void getKrxStockPriceProjects() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("basDt", "20260512");
        args.put("itmsNm", "삼성전자");
        args.put("likeItmsNm", "");
        args.put("srtnCd", "");
        args.put("mrktCls", "");
        args.put("numOfRows", "10");
        args.put("pageNo", "1");
        args.put("dataGoKrStockKey", "fake-stock-key");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("getKrxStockPrice").replace("http://apis.data.go.kr", baseUrl), declaredNamesFor("getKrxStockPrice")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(((Number) m.get("totalCount")).intValue()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) m.get("items");
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().get("name")).isEqualTo("삼성전자");
        assertThat(items.getFirst().get("shortCode")).isEqualTo("005930");
        assertThat(((Number) items.getFirst().get("close")).intValue()).isEqualTo(70000);
        assertThat(((Number) items.getFirst().get("changePct")).doubleValue()).isEqualTo(0.72);
        assertThat(items.getFirst().get("market")).isEqualTo("KOSPI");
    }

    @Test
    void getKmaShortTermForecastConvertsLatLonAndPivots() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("latitude", "37.5665");
        args.put("longitude", "126.9780");
        args.put("nx", "");
        args.put("ny", "");
        args.put("baseDate", "20260513");
        args.put("baseTime", "0500");
        args.put("dataGoKrKmaKey", "fake-kma-key");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("getKmaShortTermForecast").replace("http://apis.data.go.kr", baseUrl), declaredNamesFor("getKmaShortTermForecast")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        @SuppressWarnings("unchecked")
        Map<String, Object> grid = (Map<String, Object>) m.get("grid");
        assertThat(((Number) grid.get("nx")).intValue()).isBetween(59, 61);
        assertThat(((Number) grid.get("ny")).intValue()).isBetween(126, 128);
        assertThat(((Number) m.get("count")).intValue()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> forecasts = (List<Map<String, Object>>) m.get("forecasts");
        assertThat(forecasts).hasSize(2);
        Map<String, Object> first = forecasts.getFirst();
        assertThat(first.get("fcstDate")).isEqualTo("20260513");
        assertThat(first.get("fcstTime")).isEqualTo("0600");
        assertThat(((Number) first.get("temp")).intValue()).isEqualTo(18);
        assertThat(((Number) first.get("humidity")).intValue()).isEqualTo(65);
        assertThat(first.get("skyCondition")).isEqualTo("맑음");
        assertThat(((Number) first.get("precipProbability")).intValue()).isEqualTo(10);
    }

    @Test
    void getKmaShortTermForecastAcceptsExplicitGrid() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("latitude", "");
        args.put("longitude", "");
        args.put("nx", "60");
        args.put("ny", "127");
        args.put("baseDate", "20260513");
        args.put("baseTime", "0500");
        args.put("dataGoKrKmaKey", "fake-kma-key");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("getKmaShortTermForecast").replace("http://apis.data.go.kr", baseUrl), declaredNamesFor("getKmaShortTermForecast")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        @SuppressWarnings("unchecked")
        Map<String, Object> grid = (Map<String, Object>) m.get("grid");
        assertThat(((Number) grid.get("nx")).intValue()).isEqualTo(60);
        assertThat(((Number) grid.get("ny")).intValue()).isEqualTo(127);
    }

    @Test
    void getApartmentTradePriceProjectsItems() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("lawdCode", "11680");
        args.put("dealYmd", "202604");
        args.put("numOfRows", "20");
        args.put("pageNo", "1");
        args.put("dataGoKrAptKey", "fake-apt-key");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("getApartmentTradePrice").replace("http://apis.data.go.kr", baseUrl), declaredNamesFor("getApartmentTradePrice")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(m.get("lawdCode")).isEqualTo("11680");
        assertThat(m.get("dealYmd")).isEqualTo("202604");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) m.get("items");
        assertThat(items).hasSize(2);
        assertThat(items.getFirst().get("aptName")).isEqualTo("래미안 강남");
        assertThat(((Number) items.getFirst().get("dealAmount")).intValue()).isEqualTo(200_000);
        assertThat(((Number) items.getFirst().get("excluUseAr")).doubleValue()).isEqualTo(84.97);
        assertThat(((Number) items.getFirst().get("floor")).intValue()).isEqualTo(12);
    }

    @Test
    void searchKoreaDrugInfoRejectsEmptyFilters() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("itemName", "");
        args.put("entpName", "");
        args.put("itemSeq", "");
        args.put("numOfRows", "5");
        args.put("pageNo", "1");
        args.put("dataGoKrDrugKey", "fake-drug-key");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("searchKoreaDrugInfo").replace("http://apis.data.go.kr", baseUrl), declaredNamesFor("searchKoreaDrugInfo")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).isFalse();
        assertThat(r.error()).contains("at least one of");
    }

    @Test
    void searchKoreaDrugInfoReturnsItems() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("itemName", "타이레놀");
        args.put("entpName", "");
        args.put("itemSeq", "");
        args.put("numOfRows", "5");
        args.put("pageNo", "1");
        args.put("dataGoKrDrugKey", "fake-drug-key");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("searchKoreaDrugInfo").replace("http://apis.data.go.kr", baseUrl), declaredNamesFor("searchKoreaDrugInfo")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) m.get("items");
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().get("itemName").toString()).contains("타이레놀");
        assertThat(items.getFirst().get("entpName")).isEqualTo("한국얀센");
    }

    @Test
    void getKoreaEmergencyAlertsReturnsRecentMessages() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("area", "");
        args.put("fromDate", "20260512");
        args.put("toDate", "20260513");
        args.put("numOfRows", "10");
        args.put("pageNo", "1");
        args.put("dataGoKrDisasterKey", "fake-disaster-key");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("getKoreaEmergencyAlerts").replace("http://apis.data.go.kr", baseUrl), declaredNamesFor("getKoreaEmergencyAlerts")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) m.get("items");
        assertThat(items).hasSize(2);
        assertThat(items.getFirst().get("message").toString()).contains("호우");
        assertThat(items.getFirst().get("location")).isEqualTo("서울특별시");
    }

    @Test
    void callDataGoKrOpenApiUnwrapsResponseBody() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("servicePath", "1741000/StanReginCd/getStanReginCdList");
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("pageNo", 1);
        q.put("numOfRows", 5);
        q.put("locatadd_nm", "서울특별시");
        args.put("query", q);
        args.put("dataGoKrKey", "fake-generic-key");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("callDataGoKrOpenApi").replace("http://apis.data.go.kr", baseUrl), declaredNamesFor("callDataGoKrOpenApi")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(m.get("ok")).isEqualTo(true);
        assertThat(((Number) m.get("totalCount")).intValue()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) m.get("items");
        assertThat(items).hasSize(2);
        assertThat(items.getFirst().get("locatadd_nm")).isEqualTo("서울특별시");
    }

    @Test
    void callDataGoKrOpenApiDetectsErrorEnvelope() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("servicePath", "B999999/will/explode");
        args.put("query", Map.of("pageNo", 1));
        args.put("dataGoKrKey", "fake-generic-key");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("callDataGoKrOpenApi").replace("http://apis.data.go.kr", baseUrl), declaredNamesFor("callDataGoKrOpenApi")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(m.get("ok")).isEqualTo(false);
        assertThat(m.get("status")).isEqualTo("30");
        assertThat(m.get("message").toString()).contains("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
    }

    @Test
    void callDataGoKrOpenApiRejectsAbsoluteUrl() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("servicePath", "http://other.example.com/api/foo");
        args.put("query", Map.of());
        args.put("dataGoKrKey", "fake-generic-key");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("callDataGoKrOpenApi").replace("http://apis.data.go.kr", baseUrl), declaredNamesFor("callDataGoKrOpenApi")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).isFalse();
        assertThat(r.error()).contains("relative");
    }

    @Test
    void callDataGoKrOpenApiRejectsUnresolvedPlaceholder() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("servicePath", "1741000/StanReginCd/getStanReginCdList");
        args.put("query", Map.of());
        args.put("dataGoKrKey", "${DATA_GO_KR_KEY}");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("callDataGoKrOpenApi").replace("http://apis.data.go.kr", baseUrl), declaredNamesFor("callDataGoKrOpenApi")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).isFalse();
        assertThat(r.error()).contains("dataGoKrKey env var not set");
    }

    @Test
    void searchKBeautyProductsLiftsTagPrefixesAndProjects() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", "snail mucin essence");
        args.put("country", "south-korea");
        args.put("pageSize", "5");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("searchKBeautyProducts").replace(
                                "https://world.openbeautyfacts.org", baseUrl), declaredNamesFor("searchKBeautyProducts")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(m.get("country")).isEqualTo("south-korea");
        assertThat(((Number) m.get("count")).intValue()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> products = (List<Map<String, Object>>) m.get("products");
        assertThat(products).hasSize(2);
        Map<String, Object> first = products.getFirst();
        assertThat(first.get("productName")).isEqualTo("Advanced Snail 96 Mucin Power Essence");
        assertThat(first.get("brands")).isEqualTo("COSRX");
        @SuppressWarnings("unchecked")
        List<String> countries = (List<String>) first.get("countries");
        assertThat(countries).contains("south-korea");
        @SuppressWarnings("unchecked")
        List<String> categories = (List<String>) first.get("categories");
        assertThat(categories).contains("essences", "skin-care");
        assertThat(first.get("imageUrl").toString()).contains("cosrx-snail");
    }

    @Test
    void searchKpopOnItunesReturnsKoreanStoreResults() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("term", "NewJeans");
        args.put("entity", "song");
        args.put("country", "kr");
        args.put("limit", "5");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("searchKpopOnItunes").replace("https://itunes.apple.com", baseUrl), declaredNamesFor("searchKpopOnItunes")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(m.get("country")).isEqualTo("kr");
        assertThat(m.get("entity")).isEqualTo("song");
        assertThat(((Number) m.get("resultCount")).intValue()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) m.get("results");
        assertThat(results).hasSize(2);
        assertThat(results.getFirst().get("artistName")).isEqualTo("NewJeans");
        assertThat(results.getFirst().get("trackName")).isEqualTo("Super Shy");
        assertThat(results.getFirst().get("primaryGenre")).isEqualTo("K-Pop");
        assertThat(results.getFirst().get("previewUrl").toString()).contains("itunes.apple.com/preview");
    }

    @Test
    void getBithumbOrderbookProjectsBidsAndAsks() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("symbol", "BTC");
        args.put("count", "5");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args,
                        code("getBithumbOrderbook").replace("https://api.bithumb.com", baseUrl), declaredNamesFor("getBithumbOrderbook")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(m.get("symbol")).isEqualTo("BTC");
        assertThat(m.get("paymentCurrency")).isEqualTo("KRW");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bids = (List<Map<String, Object>>) m.get("bids");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> asks = (List<Map<String, Object>>) m.get("asks");
        assertThat(bids).hasSize(2);
        assertThat(asks).hasSize(2);
        assertThat(((Number) bids.getFirst().get("price")).longValue()).isEqualTo(85_000_000L);
        assertThat(((Number) asks.getFirst().get("price")).longValue()).isEqualTo(85_100_000L);
    }

    @Test
    void getIpInfoExpandsFields() {
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(Map.of("ip", "8.8.8.8"),
                        code("getIpInfo").replace("https://ipapi.co", baseUrl), declaredNamesFor("getIpInfo")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(m.get("ip")).isEqualTo("8.8.8.8");
        assertThat(m.get("country")).isEqualTo("US");
        assertThat(m.get("city")).isEqualTo("Mountain View");
    }

    @Test
    void getCountryInfoProjectsToCompactFields() {
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(Map.of("name", "korea"),
                        code("getCountryInfo").replace("https://cdn.jsdelivr.net", baseUrl), declaredNamesFor("getCountryInfo")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) r.result();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("name")).isEqualTo("South Korea");
        @SuppressWarnings("unchecked")
        List<String> langs = (List<String>) rows.getFirst().get("languages");
        assertThat(langs).contains("Korean");
    }

    @Test
    void searchArxivParsesAtomFeed() {
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(Map.of("query", "retrieval augmented generation", "max", 5L, "sortBy", "relevance"),
                        code("searchArxiv").replace("http://export.arxiv.org", baseUrl), declaredNamesFor("searchArxiv")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) r.result();
        assertThat(entries).hasSize(2);
        assertThat(entries.getFirst().get("title")).isEqualTo("Paper One");
        @SuppressWarnings("unchecked")
        List<String> authors = (List<String>) entries.getFirst().get("authors");
        assertThat(authors).contains("Alice", "Bob");
    }

    @Test
    void getPublicHolidaysReturnsKoreanHolidays() {
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(Map.of("year", 2026L, "countryCode", "KR"),
                        code("getPublicHolidays").replace("https://date.nager.at", baseUrl), declaredNamesFor("getPublicHolidays")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> holidays = (List<Map<String, Object>>) r.result();
        assertThat(holidays).hasSize(2);
        assertThat(holidays.getFirst().get("date")).isEqualTo("2026-01-01");
        assertThat(holidays.getFirst().get("localName")).isEqualTo("새해 첫날");
    }

    @Test
    void getOpenMeteoForecastTrimsHourly() {
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(
                        Map.of("latitude", 37.5665, "longitude", 126.978, "days", 3L, "timezone", "Asia/Seoul"),
                        code("getOpenMeteoForecast").replace("https://api.open-meteo.com", baseUrl), declaredNamesFor("getOpenMeteoForecast")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(m.get("timezone")).isEqualTo("Asia/Seoul");
        @SuppressWarnings("unchecked")
        Map<String, Object> daily = (Map<String, Object>) m.get("daily");
        @SuppressWarnings("unchecked")
        List<Object> tmax = (List<Object>) daily.get("temperatureMax");
        assertThat(tmax).hasSize(3);
        @SuppressWarnings("unchecked")
        Map<String, Object> hourly = (Map<String, Object>) m.get("hourly");
        @SuppressWarnings("unchecked")
        List<Object> hourlyT = (List<Object>) hourly.get("temperature");
        assertThat(hourlyT).hasSize(24);
    }

    @Test
    void geocodeAddressProjectsLatLon() {
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(Map.of("address", "Seoul, South Korea", "limit", 3L),
                        code("geocodeAddress").replace("https://nominatim.openstreetmap.org", baseUrl), declaredNamesFor("geocodeAddress")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) r.result();
        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.getFirst().get("latitude")).doubleValue()).isEqualTo(37.5665);
        assertThat(rows.getFirst().get("country")).isEqualTo("South Korea");
    }

    @Test
    void getSunriseSunsetConvertsToTimezone() {
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(
                        Map.of("latitude", 37.5665, "longitude", 126.978,
                               "date", "2026-05-13", "timezone", "Asia/Seoul"),
                        code("getSunriseSunset").replace("https://api.sunrise-sunset.org", baseUrl), declaredNamesFor("getSunriseSunset")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(m.get("timezone")).isEqualTo("Asia/Seoul");
        assertThat(m.get("sunrise").toString()).startsWith("2026-05-13T");
    }

    @Test
    void getRecentEarthquakesProjects() {
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(
                        Map.of("minMagnitude", 4.5, "lookbackHours", 24L, "limit", 20L),
                        code("getRecentEarthquakes").replace("https://earthquake.usgs.gov", baseUrl), declaredNamesFor("getRecentEarthquakes")),
                allowlist("127.0.0.1"));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> evs = (List<Map<String, Object>>) r.result();
        assertThat(evs).hasSize(2);
        assertThat(((Number) evs.getFirst().get("magnitude")).doubleValue()).isEqualTo(5.2);
        assertThat(evs.getFirst().get("place")).isEqualTo("near somewhere");
    }

    @Test
    void getGithubRepoNotFoundReturnsFlag() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(
                        Map.of("owner", "nope", "repo", "missing"),
                        code("getGithubRepo").replace("https://api.github.com",
                                baseUrl), declaredNamesFor("getGithubRepo")),
                allowlist("127.0.0.1"));
        assertThat(result.isOk()).as("%s", result.error()).isTrue();
        Map<?, ?> r = (Map<?, ?>) result.result();
        assertThat(r.get("found")).isEqualTo(false);
    }


    private static void wttrHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"nearest_area\":[{\"areaName\":[{\"value\":\"MockTown\"}]}]," +
                "\"current_condition\":[{\"temp_C\":\"21\",\"humidity\":\"55\"," +
                "\"windspeedKmph\":\"12\",\"winddir16Point\":\"SE\"}]}";
        respondJson(x, 200, body);
    }

    private static void pseHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"items\":[{\"title\":\"Result 1\",\"link\":\"https://r1\"}," +
                "{\"title\":\"Result 2\",\"link\":\"https://r2\"}]}";
        respondJson(x, 200, body);
    }

    private static void openaiHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"output\":[" +
                "{\"type\":\"reasoning\",\"summary\":[{\"text\":\"Mock reasoning\"}]}," +
                "{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"OpenAI mock reply\"}]}" +
                "]}";
        respondJson(x, 200, body);
    }

    private static void slackHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        respondJson(x, 200, "ok");
    }

    private static void githubHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String path = x.getRequestURI().getPath();
        if (path.endsWith("/repos/spring-projects/spring-ai")) {
            String body = "{\"full_name\":\"spring-projects/spring-ai\"," +
                    "\"description\":\"AI for Spring\"," +
                    "\"stargazers_count\":1234,\"forks_count\":56,\"open_issues_count\":7," +
                    "\"language\":\"Java\"," +
                    "\"license\":{\"spdx_id\":\"Apache-2.0\"}," +
                    "\"default_branch\":\"main\"," +
                    "\"pushed_at\":\"2026-05-12T18:00:00Z\"," +
                    "\"topics\":[\"ai\",\"spring\"]," +
                    "\"homepage\":\"https://spring.io\"," +
                    "\"html_url\":\"https://github.com/spring-projects/spring-ai\"}";
            respondJson(x, 200, body);
        } else if (path.endsWith("/issues")) {
            String body = "[" +
                    "{\"number\":101,\"title\":\"First issue\",\"state\":\"open\",\"user\":{\"login\":\"alice\"}," +
                    "\"labels\":[\"bug\"],\"comments\":3,\"created_at\":\"2026-01-01T00:00:00Z\"," +
                    "\"updated_at\":\"2026-01-02T00:00:00Z\",\"html_url\":\"https://x1\"}," +
                    "{\"number\":102,\"title\":\"Second issue\",\"state\":\"open\",\"user\":{\"login\":\"bob\"}," +
                    "\"labels\":[{\"name\":\"enhancement\"}],\"comments\":0,\"created_at\":\"2026-01-03T00:00:00Z\"," +
                    "\"updated_at\":\"2026-01-04T00:00:00Z\",\"html_url\":\"https://x2\"}," +
                    "{\"number\":103,\"title\":\"PR — should be filtered\",\"state\":\"open\"," +
                    "\"pull_request\":{\"url\":\"https://pr\"},\"user\":{\"login\":\"carol\"}," +
                    "\"labels\":[],\"comments\":1,\"created_at\":\"2026-01-05T00:00:00Z\"," +
                    "\"updated_at\":\"2026-01-05T00:00:00Z\",\"html_url\":\"https://x3\"}" +
                    "]";
            respondJson(x, 200, body);
        } else if (path.endsWith("/releases/latest")) {
            String body = "{\"tag_name\":\"v1.0.0\",\"name\":\"1.0.0\"," +
                    "\"published_at\":\"2026-04-01T00:00:00Z\",\"html_url\":\"https://r1\"," +
                    "\"body\":\"changelog\"," +
                    "\"assets\":[{\"name\":\"dist.zip\",\"browser_download_url\":\"https://dl/dist.zip\",\"size\":1024}]}";
            respondJson(x, 200, body);
        } else if (path.endsWith("/releases")) {
            String body = "[" +
                    "{\"tag_name\":\"v1.0.0\",\"name\":\"1.0.0\",\"draft\":false,\"prerelease\":false," +
                    "\"published_at\":\"2026-04-01T00:00:00Z\",\"html_url\":\"https://r1\",\"body\":\"latest\"}," +
                    "{\"tag_name\":\"v0.9.0\",\"name\":\"0.9.0\",\"draft\":false,\"prerelease\":true," +
                    "\"published_at\":\"2026-03-01T00:00:00Z\",\"html_url\":\"https://r2\",\"body\":\"prerelease\"}" +
                    "]";
            respondJson(x, 200, body);
        } else if (path.endsWith("/contributors")) {
            String body = "[" +
                    "{\"login\":\"alice\",\"contributions\":150," +
                    "\"html_url\":\"https://a\",\"avatar_url\":\"https://av/a\"}," +
                    "{\"login\":\"bob\",\"contributions\":42," +
                    "\"html_url\":\"https://b\",\"avatar_url\":\"https://av/b\"}" +
                    "]";
            respondJson(x, 200, body);
        } else if (path.contains("/contents/")) {
            String body = "{\"name\":\"README.adoc\",\"path\":\"README.adoc\",\"size\":13,\"sha\":\"abc\"," +
                    "\"type\":\"file\",\"encoding\":\"base64\",\"content\":\"aGVsbG8gcmVhZG1lCg==\"}";
            respondJson(x, 200, body);
        } else {
            respondJson(x, 404, "{\"message\":\"Not Found\"}");
        }
    }

    private static void githubUserHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"login\":\"spring-projects\",\"type\":\"Organization\"," +
                "\"name\":\"Spring Projects\",\"company\":null,\"blog\":\"https://spring.io\"," +
                "\"location\":\"Earth\",\"bio\":null," +
                "\"public_repos\":120,\"public_gists\":2,\"followers\":4567,\"following\":0," +
                "\"created_at\":\"2010-01-01T00:00:00Z\"," +
                "\"html_url\":\"https://github.com/spring-projects\"," +
                "\"avatar_url\":\"https://av\"}";
        respondJson(x, 200, body);
    }

    private static void coingeckoHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"bitcoin\":{\"usd\":62000,\"krw\":84000000}," +
                "\"ethereum\":{\"usd\":3500,\"krw\":4700000}}";
        respondJson(x, 200, body);
    }

    private static void erApiHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"result\":\"success\"," +
                "\"time_last_update_utc\":\"Fri, 12 Jun 2026 00:02:31 +0000\"," +
                "\"rates\":{\"USD\":1,\"KRW\":1300}}";
        respondJson(x, 200, body);
    }

    private static void upbitHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "[" +
                "{\"market\":\"KRW-BTC\",\"trade_price\":85000000,\"opening_price\":84000000," +
                "\"high_price\":86000000,\"low_price\":83500000,\"change\":\"RISE\",\"change_rate\":0.0119," +
                "\"acc_trade_volume_24h\":1234.56,\"timestamp\":1715000000000}," +
                "{\"market\":\"KRW-ETH\",\"trade_price\":4700000,\"opening_price\":4650000," +
                "\"high_price\":4750000,\"low_price\":4640000,\"change\":\"RISE\",\"change_rate\":0.0108," +
                "\"acc_trade_volume_24h\":9876.5,\"timestamp\":1715000000000}" +
                "]";
        respondJson(x, 200, body);
    }

    private static void bithumbHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"status\":\"0000\",\"data\":{" +
                "\"opening_price\":\"84000000\",\"closing_price\":\"86000000\"," +
                "\"min_price\":\"83000000\",\"max_price\":\"87000000\"," +
                "\"units_traded_24H\":\"1234.5\",\"acc_trade_value_24H\":\"100000000000\"," +
                "\"fluctate_24H\":\"2000000\",\"fluctate_rate_24H\":\"2.38\"}}";
        respondJson(x, 200, body);
    }

    private static void upbitOrderbookHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "[{\"market\":\"KRW-BTC\",\"timestamp\":1715000000000," +
                "\"total_ask_size\":1.5,\"total_bid_size\":2.5,\"orderbook_units\":[" +
                "{\"ask_price\":85100000,\"bid_price\":84900000,\"ask_size\":0.5,\"bid_size\":0.6}," +
                "{\"ask_price\":85200000,\"bid_price\":84800000,\"ask_size\":1.0,\"bid_size\":1.9}" +
                "]}]";
        respondJson(x, 200, body);
    }

    private static void upbitCandlesHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "[" +
                "{\"market\":\"KRW-BTC\"," +
                "\"candle_date_time_kst\":\"2026-05-13T00:00:00\"," +
                "\"candle_date_time_utc\":\"2026-05-12T15:00:00\"," +
                "\"opening_price\":84000000,\"high_price\":86000000," +
                "\"low_price\":83500000,\"trade_price\":85000000," +
                "\"candle_acc_trade_volume\":1234.56,\"candle_acc_trade_price\":1.04e14}," +
                "{\"market\":\"KRW-BTC\"," +
                "\"candle_date_time_kst\":\"2026-05-12T00:00:00\"," +
                "\"candle_date_time_utc\":\"2026-05-11T15:00:00\"," +
                "\"opening_price\":83000000,\"high_price\":84500000," +
                "\"low_price\":82500000,\"trade_price\":84000000," +
                "\"candle_acc_trade_volume\":1100.0,\"candle_acc_trade_price\":9.2e13}" +
                "]";
        respondJson(x, 200, body);
    }

    private static void upbitMarketsHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "[" +
                "{\"market\":\"KRW-BTC\",\"korean_name\":\"비트코인\",\"english_name\":\"Bitcoin\"}," +
                "{\"market\":\"KRW-ETH\",\"korean_name\":\"이더리움\",\"english_name\":\"Ethereum\"}," +
                "{\"market\":\"BTC-XRP\",\"korean_name\":\"리플\",\"english_name\":\"Ripple\"}" +
                "]";
        respondJson(x, 200, body);
    }

    private static void naverHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String clientId = x.getRequestHeaders().getFirst("X-Naver-Client-Id");
        if ("BAD".equals(clientId)) {
            respondJson(x, 401,
                    "{\"errorMessage\":\"Authentication failed (인증 실패)\",\"errorCode\":\"024\"}");
            return;
        }
        String body = "{\"lastBuildDate\":\"Wed, 13 May 2026 12:00:00 +0900\"," +
                "\"total\":42,\"start\":1,\"display\":2,\"items\":[" +
                "{\"title\":\"<b>스프링</b> AI 입문\",\"link\":\"https://blog/1\"," +
                "\"description\":\"<b>Spring</b> AI 첫 글\"," +
                "\"bloggername\":\"개발자A\",\"bloggerlink\":\"https://b/a\"," +
                "\"postdate\":\"20260512\"}," +
                "{\"title\":\"AI 챗봇 만들기\",\"link\":\"https://blog/2\"," +
                "\"description\":\"Spring AI로 챗봇 구현\"," +
                "\"bloggername\":\"개발자B\",\"bloggerlink\":\"https://b/b\"," +
                "\"postdate\":\"20260511\"}" +
                "]}";
        respondJson(x, 200, body);
    }

    private static void kakaoLocalHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String auth = x.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.contains("BAD")) {
            respondJson(x, 401,
                    "{\"msg\":\"AppKey is invalid\",\"code\":-401}");
            return;
        }
        String body = "{\"meta\":{\"total_count\":2,\"pageable_count\":2,\"is_end\":true}," +
                "\"documents\":[" +
                "{\"place_name\":\"스타벅스 강남역점\"," +
                "\"category_name\":\"음식점 > 카페 > 스타벅스\"," +
                "\"category_group_name\":\"카페\",\"category_group_code\":\"CE7\"," +
                "\"phone\":\"02-555-0001\"," +
                "\"address_name\":\"서울 강남구 역삼동 123\"," +
                "\"road_address_name\":\"서울 강남구 강남대로 1\"," +
                "\"x\":\"127.0276\",\"y\":\"37.4979\"," +
                "\"place_url\":\"http://place.map.kakao.com/1\"," +
                "\"distance\":\"\"}," +
                "{\"place_name\":\"스타벅스 강남역2점\"," +
                "\"category_name\":\"음식점 > 카페 > 스타벅스\"," +
                "\"category_group_name\":\"카페\",\"category_group_code\":\"CE7\"," +
                "\"phone\":\"02-555-0002\"," +
                "\"address_name\":\"서울 강남구 역삼동 456\"," +
                "\"road_address_name\":\"서울 강남구 강남대로 2\"," +
                "\"x\":\"127.0285\",\"y\":\"37.4988\"," +
                "\"place_url\":\"http://place.map.kakao.com/2\"," +
                "\"distance\":\"\"}" +
                "]}";
        respondJson(x, 200, body);
    }

    private static void airKoreaHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String q = x.getRequestURI().getRawQuery() == null ? "" : x.getRequestURI().getRawQuery();
        if (q.contains("serviceKey=BAD")) {
            String err = "{\"OpenAPI_ServiceResponse\":{\"cmmMsgHeader\":{" +
                    "\"errMsg\":\"SERVICE ERROR\"," +
                    "\"returnAuthMsg\":\"SERVICE_KEY_IS_NOT_REGISTERED_ERROR\"," +
                    "\"returnReasonCode\":\"30\"}}}";
            respondJson(x, 200, err);
            return;
        }
        String body = "{\"response\":{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"NORMAL_CODE\"}," +
                "\"body\":{\"totalCount\":3,\"pageNo\":1,\"numOfRows\":20,\"items\":[" +
                "{\"stationName\":\"중구\",\"sidoName\":\"서울\",\"dataTime\":\"2026-05-13 12:00\"," +
                "\"pm10Value\":\"35\",\"pm25Value\":\"18\",\"o3Value\":\"0.032\"," +
                "\"no2Value\":\"0.018\",\"coValue\":\"0.5\",\"so2Value\":\"0.003\"," +
                "\"khaiValue\":\"75\",\"khaiGrade\":\"2\"}," +
                "{\"stationName\":\"강남구\",\"sidoName\":\"서울\",\"dataTime\":\"2026-05-13 12:00\"," +
                "\"pm10Value\":\"-\",\"pm25Value\":\"22\",\"o3Value\":\"0.030\"," +
                "\"no2Value\":\"0.019\",\"coValue\":\"0.5\",\"so2Value\":\"0.003\"," +
                "\"khaiValue\":\"80\",\"khaiGrade\":\"2\"}," +
                "{\"stationName\":\"송파구\",\"sidoName\":\"서울\",\"dataTime\":\"2026-05-13 12:00\"," +
                "\"pm10Value\":\"40\",\"pm25Value\":\"25\",\"o3Value\":\"0.028\"," +
                "\"no2Value\":\"0.020\",\"coValue\":\"0.6\",\"so2Value\":\"0.004\"," +
                "\"khaiValue\":\"82\",\"khaiGrade\":\"2\"}" +
                "]}}}";
        respondJson(x, 200, body);
    }

    private static void bithumbOrderbookHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"status\":\"0000\",\"data\":{" +
                "\"timestamp\":\"1715000000000\"," +
                "\"payment_currency\":\"KRW\",\"order_currency\":\"BTC\"," +
                "\"bids\":[" +
                "{\"price\":\"85000000\",\"quantity\":\"0.5\"}," +
                "{\"price\":\"84900000\",\"quantity\":\"1.2\"}" +
                "]," +
                "\"asks\":[" +
                "{\"price\":\"85100000\",\"quantity\":\"0.4\"}," +
                "{\"price\":\"85200000\",\"quantity\":\"0.9\"}" +
                "]}}";
        respondJson(x, 200, body);
    }

    private static void ipapiHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"ip\":\"8.8.8.8\",\"city\":\"Mountain View\",\"region\":\"California\"," +
                "\"country_code\":\"US\",\"country_name\":\"United States\"," +
                "\"latitude\":37.4056,\"longitude\":-122.0775," +
                "\"timezone\":\"America/Los_Angeles\",\"asn\":\"AS15169\",\"org\":\"GOOGLE\"}";
        respondJson(x, 200, body);
    }

    // Full-dataset shape (mledoze/countries): the tool downloads every country and filters locally,
    // so the stub carries a non-matching entry to prove the name filter works.
    private static void countriesHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "[" +
                "{\"name\":{\"common\":\"South Korea\",\"official\":\"Republic of Korea\"}," +
                "\"capital\":[\"Seoul\"],\"region\":\"Asia\",\"subregion\":\"Eastern Asia\"," +
                "\"area\":100210," +
                "\"languages\":{\"kor\":\"Korean\"}," +
                "\"currencies\":{\"KRW\":{\"name\":\"South Korean won\",\"symbol\":\"₩\"}}," +
                "\"idd\":{\"root\":\"+8\",\"suffixes\":[\"2\"]}," +
                "\"flag\":\"🇰🇷\",\"latlng\":[37.0,127.5]}," +
                "{\"name\":{\"common\":\"Japan\",\"official\":\"Japan\"}," +
                "\"capital\":[\"Tokyo\"],\"region\":\"Asia\",\"subregion\":\"Eastern Asia\"," +
                "\"area\":377930," +
                "\"languages\":{\"jpn\":\"Japanese\"}," +
                "\"currencies\":{\"JPY\":{\"name\":\"Japanese yen\",\"symbol\":\"¥\"}}," +
                "\"idd\":{\"root\":\"+8\",\"suffixes\":[\"1\"]}," +
                "\"flag\":\"🇯🇵\",\"latlng\":[36.0,138.0]}" +
                "]";
        respondJson(x, 200, body);
    }

    private static void arxivHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<feed xmlns=\"http://www.w3.org/2005/Atom\">" +
                "  <entry>" +
                "    <id>http://arxiv.org/abs/2401.0001</id>" +
                "    <title>Paper One</title>" +
                "    <summary>Summary of paper one.</summary>" +
                "    <published>2024-01-01T00:00:00Z</published>" +
                "    <updated>2024-01-02T00:00:00Z</updated>" +
                "    <author><name>Alice</name></author>" +
                "    <author><name>Bob</name></author>" +
                "    <primary_category term=\"cs.AI\"/>" +
                "    <link rel=\"alternate\" href=\"http://arxiv.org/abs/2401.0001\"/>" +
                "    <link title=\"pdf\" href=\"http://arxiv.org/pdf/2401.0001\"/>" +
                "  </entry>" +
                "  <entry>" +
                "    <id>http://arxiv.org/abs/2401.0002</id>" +
                "    <title>Paper Two</title>" +
                "    <summary>Summary of paper two.</summary>" +
                "    <published>2024-02-01T00:00:00Z</published>" +
                "    <updated>2024-02-02T00:00:00Z</updated>" +
                "    <author><name>Carol</name></author>" +
                "    <primary_category term=\"cs.CL\"/>" +
                "    <link rel=\"alternate\" href=\"http://arxiv.org/abs/2401.0002\"/>" +
                "  </entry>" +
                "</feed>";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().add("Content-Type", "application/atom+xml; charset=utf-8");
        x.sendResponseHeaders(200, bytes.length);
        x.getResponseBody().write(bytes);
        x.close();
    }

    private static void nagerHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "[" +
                "{\"date\":\"2026-01-01\",\"localName\":\"새해 첫날\",\"name\":\"New Year's Day\"," +
                "\"fixed\":true,\"global\":true,\"types\":[\"Public\"]}," +
                "{\"date\":\"2026-03-01\",\"localName\":\"삼일절\",\"name\":\"Independence Movement Day\"," +
                "\"fixed\":true,\"global\":true,\"types\":[\"Public\"]}" +
                "]";
        respondJson(x, 200, body);
    }

    private static void redditHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"data\":{\"children\":[" +
                "{\"data\":{\"title\":\"Spring AI thread\",\"author\":\"alice\",\"score\":42," +
                "\"num_comments\":7,\"created_utc\":1715000000," +
                "\"subreddit\":\"programming\",\"permalink\":\"/r/programming/comments/1/\"," +
                "\"url\":\"https://x1\",\"selftext\":\"...\"}}," +
                "{\"data\":{\"title\":\"AI in Spring Boot\",\"author\":\"bob\",\"score\":12," +
                "\"num_comments\":3,\"created_utc\":1715001000," +
                "\"subreddit\":\"programming\",\"permalink\":\"/r/programming/comments/2/\"," +
                "\"url\":\"https://x2\",\"selftext\":\"text\"}}" +
                "]}}";
        respondJson(x, 200, body);
    }

    private static void openMeteoHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        StringBuilder hourlyTimes = new StringBuilder("[");
        StringBuilder hourlyTemps = new StringBuilder("[");
        StringBuilder hourlyProbs = new StringBuilder("[");
        for (int i = 0; i < 48; i++) {
            if (i > 0) {
                hourlyTimes.append(',');
                hourlyTemps.append(',');
                hourlyProbs.append(',');
            }
            hourlyTimes.append("\"2026-05-13T").append(String.format("%02d", i % 24)).append(":00\"");
            hourlyTemps.append(20 + (i % 10));
            hourlyProbs.append(i % 100);
        }
        hourlyTimes.append(']');
        hourlyTemps.append(']');
        hourlyProbs.append(']');

        String body = "{\"latitude\":37.5665,\"longitude\":126.978,\"timezone\":\"Asia/Seoul\"," +
                "\"daily\":{" +
                "\"time\":[\"2026-05-13\",\"2026-05-14\",\"2026-05-15\"]," +
                "\"temperature_2m_max\":[24.5,26.0,23.0]," +
                "\"temperature_2m_min\":[16.0,17.0,15.0]," +
                "\"precipitation_sum\":[0.0,2.5,1.0]," +
                "\"wind_speed_10m_max\":[12.0,15.0,10.0]" +
                "}," +
                "\"hourly\":{" +
                "\"time\":" + hourlyTimes +
                ",\"temperature_2m\":" + hourlyTemps +
                ",\"precipitation_probability\":" + hourlyProbs +
                "}}";
        respondJson(x, 200, body);
    }

    private static void nominatimHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String q = x.getRequestURI().getRawQuery() == null ? "" : x.getRequestURI().getRawQuery();
        if (q.contains("term=") && q.contains("media=music")) {
            itunesHandler(x);
            return;
        }
        String body = "[{\"display_name\":\"Seoul, South Korea\"," +
                "\"lat\":\"37.5665\",\"lon\":\"126.978\"," +
                "\"address\":{\"country\":\"South Korea\",\"city\":\"Seoul\"}," +
                "\"type\":\"administrative\",\"importance\":0.95}]";
        respondJson(x, 200, body);
    }

    private static void itunesHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"resultCount\":2,\"results\":[" +
                "{\"wrapperType\":\"track\",\"kind\":\"song\"," +
                "\"artistName\":\"NewJeans\",\"trackName\":\"Super Shy\"," +
                "\"collectionName\":\"Get Up\",\"releaseDate\":\"2023-07-21T00:00:00Z\"," +
                "\"primaryGenreName\":\"K-Pop\"," +
                "\"previewUrl\":\"https://audio.itunes.apple.com/preview/1.m4a\"," +
                "\"trackViewUrl\":\"https://music.apple.com/kr/song/1\"," +
                "\"artistViewUrl\":\"https://music.apple.com/kr/artist/1\"," +
                "\"artworkUrl100\":\"https://art/100x100.jpg\"," +
                "\"trackTimeMillis\":154000,\"country\":\"KOR\"}," +
                "{\"wrapperType\":\"track\",\"kind\":\"song\"," +
                "\"artistName\":\"NewJeans\",\"trackName\":\"OMG\"," +
                "\"collectionName\":\"OMG - Single\",\"releaseDate\":\"2023-01-02T00:00:00Z\"," +
                "\"primaryGenreName\":\"K-Pop\"," +
                "\"previewUrl\":\"https://audio.itunes.apple.com/preview/2.m4a\"," +
                "\"trackViewUrl\":\"https://music.apple.com/kr/song/2\"," +
                "\"artistViewUrl\":\"https://music.apple.com/kr/artist/1\"," +
                "\"artworkUrl100\":\"https://art/100x100-omg.jpg\"," +
                "\"trackTimeMillis\":215000,\"country\":\"KOR\"}" +
                "]}";
        respondJson(x, 200, body);
    }

    private static void seoulOpenDataHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"culturalEventInfo\":{\"list_total_count\":2," +
                "\"RESULT\":{\"CODE\":\"INFO-000\",\"MESSAGE\":\"정상 처리되었습니다\"}," +
                "\"row\":[" +
                "{\"CODENAME\":\"축제\",\"GUNAME\":\"중구\"," +
                "\"TITLE\":\"서울 봄꽃 페스티벌\"," +
                "\"DATE\":\"2026-05-01~2026-05-15\"," +
                "\"PLACE\":\"서울광장\",\"ORG_NAME\":\"서울시\"," +
                "\"USE_TGT\":\"전체관람가\",\"USE_FEE\":\"무료\",\"IS_FREE\":\"무료\"," +
                "\"PROGRAM\":\"봄꽃 전시 및 야외 공연\",\"ETC_DESC\":\"우천 시 취소\"," +
                "\"MAIN_IMG\":\"https://img/spring.jpg\"," +
                "\"HMPG_ADDR\":\"https://seoul.go.kr/spring\"," +
                "\"ORG_LINK\":\"https://seoul.go.kr/spring\"," +
                "\"STRTDATE\":\"2026-05-01\",\"END_DATE\":\"2026-05-15\"," +
                "\"LAT\":\"37.5663\",\"LOT\":\"127.0091\"}," +
                "{\"CODENAME\":\"전시/미술\",\"GUNAME\":\"종로구\"," +
                "\"TITLE\":\"근현대 한국 미술전\"," +
                "\"DATE\":\"2026-04-15~2026-06-30\"," +
                "\"PLACE\":\"국립현대미술관\",\"ORG_NAME\":\"국립현대미술관\"," +
                "\"USE_TGT\":\"전체관람가\",\"USE_FEE\":\"4,000\",\"IS_FREE\":\"유료\"," +
                "\"PROGRAM\":\"-\",\"ETC_DESC\":\"-\"," +
                "\"MAIN_IMG\":\"https://img/art.jpg\"," +
                "\"HMPG_ADDR\":\"https://mmca.go.kr/\"," +
                "\"ORG_LINK\":\"https://mmca.go.kr/\"," +
                "\"STRTDATE\":\"2026-04-15\",\"END_DATE\":\"2026-06-30\"," +
                "\"LAT\":\"37.5786\",\"LOT\":\"126.9802\"}" +
                "]}}";
        respondJson(x, 200, body);
    }

    private static void kmaForecastHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"response\":{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"NORMAL_SERVICE\"}," +
                "\"body\":{\"dataType\":\"JSON\",\"pageNo\":1,\"numOfRows\":1000,\"totalCount\":8," +
                "\"items\":{\"item\":[" +
                "{\"baseDate\":\"20260513\",\"baseTime\":\"0500\",\"category\":\"TMP\"," +
                "\"fcstDate\":\"20260513\",\"fcstTime\":\"0600\",\"fcstValue\":\"18\",\"nx\":60,\"ny\":127}," +
                "{\"baseDate\":\"20260513\",\"baseTime\":\"0500\",\"category\":\"REH\"," +
                "\"fcstDate\":\"20260513\",\"fcstTime\":\"0600\",\"fcstValue\":\"65\",\"nx\":60,\"ny\":127}," +
                "{\"baseDate\":\"20260513\",\"baseTime\":\"0500\",\"category\":\"SKY\"," +
                "\"fcstDate\":\"20260513\",\"fcstTime\":\"0600\",\"fcstValue\":\"1\",\"nx\":60,\"ny\":127}," +
                "{\"baseDate\":\"20260513\",\"baseTime\":\"0500\",\"category\":\"POP\"," +
                "\"fcstDate\":\"20260513\",\"fcstTime\":\"0600\",\"fcstValue\":\"10\",\"nx\":60,\"ny\":127}," +
                "{\"baseDate\":\"20260513\",\"baseTime\":\"0500\",\"category\":\"TMP\"," +
                "\"fcstDate\":\"20260513\",\"fcstTime\":\"1200\",\"fcstValue\":\"24\",\"nx\":60,\"ny\":127}," +
                "{\"baseDate\":\"20260513\",\"baseTime\":\"0500\",\"category\":\"REH\"," +
                "\"fcstDate\":\"20260513\",\"fcstTime\":\"1200\",\"fcstValue\":\"45\",\"nx\":60,\"ny\":127}," +
                "{\"baseDate\":\"20260513\",\"baseTime\":\"0500\",\"category\":\"SKY\"," +
                "\"fcstDate\":\"20260513\",\"fcstTime\":\"1200\",\"fcstValue\":\"3\",\"nx\":60,\"ny\":127}," +
                "{\"baseDate\":\"20260513\",\"baseTime\":\"0500\",\"category\":\"POP\"," +
                "\"fcstDate\":\"20260513\",\"fcstTime\":\"1200\",\"fcstValue\":\"30\",\"nx\":60,\"ny\":127}" +
                "]}}}}";
        respondJson(x, 200, body);
    }

    private static void aptTradeHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"response\":{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"OK\"}," +
                "\"body\":{\"totalCount\":2,\"pageNo\":1,\"numOfRows\":20," +
                "\"items\":{\"item\":[" +
                "{\"aptNm\":\"래미안 강남\",\"dealYear\":2026,\"dealMonth\":4,\"dealDay\":15," +
                "\"dealAmount\":\"200,000\",\"excluUseAr\":84.97,\"floor\":12,\"buildYear\":2018," +
                "\"umdNm\":\"역삼동\",\"jibun\":\"123-4\",\"roadNm\":\"테헤란로\",\"dealingGbn\":\"중개거래\"}," +
                "{\"aptNm\":\"타워 강남\",\"dealYear\":2026,\"dealMonth\":4,\"dealDay\":20," +
                "\"dealAmount\":\"175,000\",\"excluUseAr\":59.92,\"floor\":8,\"buildYear\":2015," +
                "\"umdNm\":\"역삼동\",\"jibun\":\"567-8\",\"roadNm\":\"강남대로\",\"dealingGbn\":\"직거래\"}" +
                "]}}}}";
        respondJson(x, 200, body);
    }

    private static void drugInfoHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"NORMAL SERVICE\"}," +
                "\"body\":{\"pageNo\":1,\"totalCount\":1,\"numOfRows\":5," +
                "\"items\":[" +
                "{\"ITEM_SEQ\":\"196500001\",\"ITEM_NAME\":\"타이레놀정500밀리그람(아세트아미노펜)\"," +
                "\"ENTP_NAME\":\"한국얀센\",\"ITEM_PERMIT_DATE\":\"19650113\"," +
                "\"INDUTY_TYPE\":\"의약품\",\"CLASS_NAME\":\"해열, 진통, 소염제\"," +
                "\"STORAGE_METHOD\":\"실온보관\",\"PACK_UNIT\":\"30정,100정\"," +
                "\"VALID_TERM\":\"제조일로부터 36개월\",\"CHART\":\"백색의 원형 정제\"}" +
                "]}}";
        respondJson(x, 200, body);
    }

    private static void disasterMsgHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"OK\"}," +
                "\"body\":{\"totalCount\":2,\"pageNo\":1,\"numOfRows\":10," +
                "\"items\":[" +
                "{\"SN\":\"123456\",\"CREATE_DATE\":\"2026-05-12 14:30:00\"," +
                "\"MSG\":\"[서울특별시] 호우경보 발효. 침수 우려지역 외출 자제 바랍니다.\"," +
                "\"EMRG_STEP_NAME\":\"경고\",\"DST_SE_NAME\":\"호우\"," +
                "\"LOCATION_NAME\":\"서울특별시\"}," +
                "{\"SN\":\"123457\",\"CREATE_DATE\":\"2026-05-12 16:00:00\"," +
                "\"MSG\":\"[경기도] 미세먼지 비상저감조치 발령.\"," +
                "\"EMRG_STEP_NAME\":\"주의\",\"DST_SE_NAME\":\"미세먼지\"," +
                "\"LOCATION_NAME\":\"경기도\"}" +
                "]}}";
        respondJson(x, 200, body);
    }

    private static void dataGoKrGenericHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"response\":{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"NORMAL_CODE\"}," +
                "\"body\":{\"totalCount\":2,\"pageNo\":1,\"numOfRows\":5," +
                "\"items\":{\"item\":[" +
                "{\"region_cd\":\"1100000000\",\"sido_cd\":\"11\",\"locatadd_nm\":\"서울특별시\"," +
                "\"locathigh_cd\":\"\",\"locatjumin_cd\":\"\",\"locatjijuk_cd\":\"\"}," +
                "{\"region_cd\":\"1111000000\",\"sido_cd\":\"11\",\"locatadd_nm\":\"서울특별시 종로구\"," +
                "\"locathigh_cd\":\"1100000000\",\"locatjumin_cd\":\"\",\"locatjijuk_cd\":\"\"}" +
                "]}}}}";
        respondJson(x, 200, body);
    }

    private static void dataGoKrGenericErrorHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"OpenAPI_ServiceResponse\":{\"cmmMsgHeader\":{" +
                "\"errMsg\":\"SERVICE ERROR\"," +
                "\"returnAuthMsg\":\"SERVICE_KEY_IS_NOT_REGISTERED_ERROR\"," +
                "\"returnReasonCode\":\"30\"}}}";
        respondJson(x, 200, body);
    }

    private static void seoulOpenDataBadKeyHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        respondJson(x, 200,
                "{\"RESULT\":{\"CODE\":\"INFO-100\",\"MESSAGE\":\"인증키 정보가 올바르지 않습니다.\"}}");
    }

    private static void kamisHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"condition\":[]," +
                "\"data\":{\"error_code\":\"000\",\"item\":[" +
                "{\"itemname\":\"사과\",\"kindname\":\"후지\",\"countyname\":\"전체\"," +
                "\"marketname\":\"전국\",\"yyyy\":\"2026\",\"regday\":\"05/12\"," +
                "\"price\":\"30,000\",\"unit\":\"10kg\"}," +
                "{\"itemname\":\"사과\",\"kindname\":\"홍로\",\"countyname\":\"전체\"," +
                "\"marketname\":\"전국\",\"yyyy\":\"2026\",\"regday\":\"05/13\"," +
                "\"price\":\"28,500\",\"unit\":\"10kg\"}" +
                "]}}";
        respondJson(x, 200, body);
    }

    private static void koficHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String q = x.getRequestURI().getRawQuery() == null ? "" : x.getRequestURI().getRawQuery();
        if (q.contains("key=BAD")) {
            respondJson(x, 200,
                    "{\"faultInfo\":{\"errorCode\":\"100\",\"message\":\"인증키가 유효하지 않습니다\"}}");
            return;
        }
        String body = "{\"boxOfficeResult\":{" +
                "\"boxofficeType\":\"일별 박스오피스\"," +
                "\"showRange\":\"20260512~20260512\"," +
                "\"dailyBoxOfficeList\":[" +
                "{\"rnum\":\"1\",\"rank\":\"1\",\"rankInten\":\"0\",\"rankOldAndNew\":\"OLD\"," +
                "\"movieCd\":\"20239999\",\"movieNm\":\"범죄도시 5\",\"openDt\":\"2026-04-24\"," +
                "\"salesAmt\":\"1,000,000,000\",\"salesShare\":\"45.6\"," +
                "\"salesInten\":\"100,000,000\",\"salesChange\":\"11.1\"," +
                "\"salesAcc\":\"10,000,000,000\",\"audiCnt\":\"100,000\",\"audiInten\":\"10,000\"," +
                "\"audiChange\":\"11.1\",\"audiAcc\":\"1,500,000\"," +
                "\"scrnCnt\":\"1,500\",\"showCnt\":\"5,000\"}," +
                "{\"rnum\":\"2\",\"rank\":\"2\",\"rankInten\":\"3\",\"rankOldAndNew\":\"NEW\"," +
                "\"movieCd\":\"20240001\",\"movieNm\":\"한국 SF 영화\",\"openDt\":\"2026-05-10\"," +
                "\"salesAmt\":\"800,000,000\",\"salesShare\":\"36.4\"," +
                "\"salesInten\":\"800,000,000\",\"salesChange\":\"100.0\"," +
                "\"salesAcc\":\"2,000,000,000\",\"audiCnt\":\"80,000\",\"audiInten\":\"80,000\"," +
                "\"audiChange\":\"100.0\",\"audiAcc\":\"200,000\"," +
                "\"scrnCnt\":\"1,000\",\"showCnt\":\"3,500\"}" +
                "]}}";
        respondJson(x, 200, body);
    }

    private static void krxStockHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"response\":{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"NORMAL SERVICE.\"}," +
                "\"body\":{\"numOfRows\":\"10\",\"pageNo\":\"1\",\"totalCount\":\"1\"," +
                "\"items\":{\"item\":[" +
                "{\"basDt\":\"20260512\",\"srtnCd\":\"005930\",\"isinCd\":\"KR7005930003\"," +
                "\"itmsNm\":\"삼성전자\",\"mrktCtg\":\"KOSPI\"," +
                "\"clpr\":\"70000\",\"vs\":\"500\",\"fltRt\":\"0.72\"," +
                "\"mkp\":\"69800\",\"hipr\":\"70200\",\"lopr\":\"69500\"," +
                "\"trqu\":\"12345678\",\"trPrc\":\"863200000000\"," +
                "\"lstgStCnt\":\"5969782550\",\"mrktTotAmt\":\"417884779850000\"}" +
                "]}}}}";
        respondJson(x, 200, body);
    }

    private static void openBeautyFactsHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"count\":2,\"page\":1,\"page_count\":1,\"page_size\":5," +
                "\"products\":[" +
                "{\"code\":\"8809610706106\"," +
                "\"product_name\":\"Advanced Snail 96 Mucin Power Essence\"," +
                "\"brands\":\"COSRX\"," +
                "\"countries_tags\":[\"en:south-korea\",\"en:united-states\"]," +
                "\"categories_tags\":[\"en:essences\",\"en:skin-care\"]," +
                "\"allergens_tags\":[]," +
                "\"ingredients_text\":\"Snail Secretion Filtrate, Sodium Hyaluronate, Allantoin\"," +
                "\"packaging\":\"Plastic bottle\"," +
                "\"image_front_url\":\"https://obf/cosrx-snail.jpg\"," +
                "\"image_front_small_url\":\"https://obf/cosrx-snail-sm.jpg\"," +
                "\"url\":\"https://world.openbeautyfacts.org/product/8809610706106\"}," +
                "{\"code\":\"8809378550101\"," +
                "\"product_name\":\"Cica Sleeping Mask\"," +
                "\"brands\":\"Laneige\"," +
                "\"countries_tags\":[\"en:south-korea\"]," +
                "\"categories_tags\":[\"en:masks\",\"en:skin-care\"]," +
                "\"allergens_tags\":[\"en:fragrance\"]," +
                "\"ingredients_text\":\"Water, Glycerin, Centella Asiatica Extract\"," +
                "\"packaging\":\"Glass jar\"," +
                "\"image_front_url\":\"https://obf/laneige-cica.jpg\"," +
                "\"url\":\"https://world.openbeautyfacts.org/product/8809378550101\"}" +
                "]}";
        respondJson(x, 200, body);
    }

    private static void tourApiHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String q = x.getRequestURI().getRawQuery() == null ? "" : x.getRequestURI().getRawQuery();
        if (q.contains("serviceKey=BAD")) {
            String err = "{\"OpenAPI_ServiceResponse\":{\"cmmMsgHeader\":{" +
                    "\"errMsg\":\"SERVICE ERROR\"," +
                    "\"returnAuthMsg\":\"SERVICE_KEY_IS_NOT_REGISTERED_ERROR\"," +
                    "\"returnReasonCode\":\"30\"}}}";
            respondJson(x, 200, err);
            return;
        }
        String body = "{\"response\":{\"header\":{\"resultCode\":\"0000\",\"resultMsg\":\"OK\"}," +
                "\"body\":{\"numOfRows\":5,\"pageNo\":1,\"totalCount\":2,\"items\":{\"item\":[" +
                "{\"contentid\":\"264337\",\"contenttypeid\":\"12\"," +
                "\"title\":\"경복궁\",\"addr1\":\"서울특별시 종로구 사직로 161\",\"addr2\":\"\"," +
                "\"areacode\":\"1\",\"sigungucode\":\"24\"," +
                "\"cat1\":\"A02\",\"cat2\":\"A0201\",\"cat3\":\"A02010100\"," +
                "\"firstimage\":\"https://img/gyeongbok-1.jpg\"," +
                "\"firstimage2\":\"https://img/gyeongbok-1-thumb.jpg\"," +
                "\"mapx\":\"126.9770\",\"mapy\":\"37.5796\"," +
                "\"tel\":\"02-3700-3900\",\"modifiedtime\":\"20260101120000\"}," +
                "{\"contentid\":\"126508\",\"contenttypeid\":\"12\"," +
                "\"title\":\"경복궁 향원정\",\"addr1\":\"서울특별시 종로구\",\"addr2\":\"\"," +
                "\"areacode\":\"1\",\"sigungucode\":\"24\"," +
                "\"cat1\":\"A02\",\"cat2\":\"A0201\",\"cat3\":\"A02010100\"," +
                "\"firstimage\":\"https://img/hyangwonjeong.jpg\"," +
                "\"firstimage2\":\"\"," +
                "\"mapx\":\"126.9785\",\"mapy\":\"37.5810\"," +
                "\"tel\":\"\",\"modifiedtime\":\"20260101120000\"}" +
                "]}}}}";
        respondJson(x, 200, body);
    }

    private static void sunHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"status\":\"OK\",\"results\":{" +
                "\"sunrise\":\"2026-05-13T05:25:00+00:00\"," +
                "\"sunset\":\"2026-05-13T10:35:00+00:00\"," +
                "\"solar_noon\":\"2026-05-13T08:00:00+00:00\"," +
                "\"day_length\":\"05:10:00\"," +
                "\"civil_twilight_begin\":\"2026-05-13T04:55:00+00:00\"," +
                "\"civil_twilight_end\":\"2026-05-13T11:05:00+00:00\"}}";
        respondJson(x, 200, body);
    }

    private static void usgsHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"type\":\"FeatureCollection\",\"features\":[" +
                "{\"properties\":{\"time\":1715000000000,\"place\":\"near somewhere\"," +
                "\"mag\":5.2,\"magType\":\"mb\",\"url\":\"https://q1\",\"tsunami\":0}," +
                "\"geometry\":{\"coordinates\":[127.0,37.0,10.0]}}," +
                "{\"properties\":{\"time\":1714900000000,\"place\":\"elsewhere\"," +
                "\"mag\":4.8,\"magType\":\"mb\",\"url\":\"https://q2\",\"tsunami\":0}," +
                "\"geometry\":{\"coordinates\":[129.0,36.0,15.0]}}" +
                "]}";
        respondJson(x, 200, body);
    }

    private static void githubSearchReposHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"items\":[" +
                "{\"full_name\":\"spring-projects/spring-ai\",\"description\":\"AI for Spring\"," +
                "\"stargazers_count\":1234,\"forks_count\":56,\"language\":\"Java\"," +
                "\"html_url\":\"https://h1\",\"updated_at\":\"2026-05-12T18:00:00Z\"}," +
                "{\"full_name\":\"alice/spring-ai-demo\",\"description\":\"demo\"," +
                "\"stargazers_count\":10,\"forks_count\":1,\"language\":\"Java\"," +
                "\"html_url\":\"https://h2\",\"updated_at\":\"2026-05-11T00:00:00Z\"}" +
                "]}";
        respondJson(x, 200, body);
    }

    private static void wikipediaHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"title\":\"Spring Framework\"," +
                "\"description\":\"Application framework\"," +
                "\"extract\":\"Mock summary of Spring Framework.\"," +
                "\"thumbnail\":{\"source\":\"https://thumb\"}," +
                "\"content_urls\":{\"desktop\":{\"page\":\"https://en.wikipedia.org/wiki/Spring_Framework\"}}}";
        respondJson(x, 200, body);
    }

    private static void hackerNewsHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"hits\":[" +
                "{\"objectID\":\"1\",\"title\":\"HN Story 1\",\"url\":\"https://x1\",\"points\":42,\"author\":\"a\",\"num_comments\":3,\"created_at\":\"2026-01-01T00:00:00Z\"}," +
                "{\"objectID\":\"2\",\"story_title\":\"HN Story 2\",\"story_url\":\"https://x2\",\"points\":7,\"author\":\"b\",\"num_comments\":0,\"created_at\":\"2026-01-02T00:00:00Z\"}" +
                "]}";
        respondJson(x, 200, body);
    }

    private static void stackOverflowHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String body = "{\"items\":[" +
                "{\"question_id\":1,\"title\":\"SO Question 1\",\"score\":12,\"answer_count\":2,\"is_answered\":true,\"tags\":[\"java\",\"spring\"],\"link\":\"https://q1\",\"creation_date\":1700000000,\"owner\":{\"display_name\":\"alice\"}}," +
                "{\"question_id\":2,\"title\":\"SO Question 2\",\"score\":3,\"answer_count\":0,\"is_answered\":false,\"tags\":[\"spring\"],\"link\":\"https://q2\",\"creation_date\":1700001000,\"owner\":{\"display_name\":\"bob\"}}" +
                "]}";
        respondJson(x, 200, body);
    }

    private static void pageHandler(com.sun.net.httpserver.HttpExchange x) throws IOException {
        String html = "<html><head><title>Hello title</title></head>" +
                "<body><article><h1>Welcome</h1><p>Lorem ipsum.</p>" +
                "<a href=\"https://example.com\" title=\"ex\">example</a></article></body></html>";
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        x.sendResponseHeaders(200, bytes.length);
        x.getResponseBody().write(bytes);
        x.close();
    }

    private static void respondJson(com.sun.net.httpserver.HttpExchange x, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().add("Content-Type", "application/json");
        x.sendResponseHeaders(status, bytes.length);
        x.getResponseBody().write(bytes);
        x.close();
    }
}
