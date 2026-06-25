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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.JsSandbox;
import org.springaicommunity.playground.service.tool.runtime.JsToolExecutor.JsExecutionParams;
import org.springaicommunity.playground.service.tool.runtime.JsToolExecutor.JsExecutionResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class JsToolBuiltinsTest {

    private static final Set<String> NEW_TOOL_NAMES = Set.of(
            "urlEncode", "regexExtract", "regexReplace", "diffText", "sortLines",
            "parseDate", "formatDate", "dateMath", "dateDiff",
            "stats", "uuid",
            "secretPatternDetect", "piiDetect", "base64", "hex", "jwtDecode",
            "hash", "hmac", "secureRandom", "passwordGenerate",
            "parseCsv", "formatCsv",
            "evalExpression", "timezoneConvert", "cronNext", "jwtVerify"
    );

    private static List<Map<String, Object>> specs;
    private static JsToolExecutor executor;

    @BeforeAll
    static void loadSpecs() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        specs = new java.util.ArrayList<>();
        for (String fname : List.of("/tool/default-tool-specs.json",
                "/tool/default-tool-specs-builtin.json",
                "/tool/default-tool-specs-builtin-helpers.json",
                "/tool/default-tool-specs-network.json",
                "/tool/default-tool-specs-kr.json")) {
            try (InputStream in = JsToolBuiltinsTest.class.getResourceAsStream(fname)) {
                if (in == null) continue;
                List<Map<String, Object>> batch = mapper.readValue(in,
                        new TypeReference<List<Map<String, Object>>>() {});
                specs.addAll(batch);
            }
        }
        executor = new JsToolExecutor(30L,
                new JsSandbox(true, false, false, false, 5_000_000L,
                        Set.of("java.lang.System", "java.lang.Runtime", "java.lang.ProcessBuilder",
                                "java.lang.Process", "java.lang.Class", "java.lang.invoke.*",
                                "java.lang.reflect.*"),
                        Set.of("java.lang.*", "java.math.*", "java.time.*", "java.util.*",
                                "java.text.*"),
                        Map.of()),
                Path.of(System.getProperty("java.io.tmpdir")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("toolArguments")
    void builtinToolExecutesWithTestValues(String name) {
        Map<String, Object> spec = specs.stream()
                .filter(s -> name.equals(s.get("name"))).findFirst().orElseThrow();
        JsExecutionResult result = runSpec(spec);
        assertThat(result.isOk())
                .as("tool %s — error: %s, debug: %s", name, result.error(), result.debugInfo())
                .isTrue();
    }

    static Stream<String> toolArguments() {
        return NEW_TOOL_NAMES.stream().sorted();
    }

    @Test
    void urlEncodePercentEncodesReserved() {
        String encoded = (String) runOk("urlEncode");
        assertThat(encoded).isEqualTo("hello%20world%20%26%20foo%3Dbar");
    }

    @Test
    void regexExtractReturnsMatches() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) runOk("regexExtract");
        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).get("match")).isEqualTo("#1234");
    }

    @Test
    void regexReplaceSwapsGroups() {
        String result = (String) runOk("regexReplace");
        assertThat(result).isEqualTo("world hello");
    }

    @Test
    void diffTextReportsAdditionsAndDeletions() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> diff = (List<Map<String, Object>>) runOk("diffText");
        String summary = diff.stream().map(d -> d.get("op") + " " + d.get("line"))
                .reduce((a, b) -> a + "\n" + b).orElse("");
        assertThat(summary).contains("= alpha");
        assertThat(summary).contains("- beta");
        assertThat(summary).contains("+ beta-2");
        assertThat(summary).contains("+ delta");
    }

    @Test
    void sortLinesCaseInsensitive() {
        String result = (String) runOk("sortLines");
        assertThat(result).startsWith("apple\nApple\nbanana");
    }

    @Test
    void parseDateExtractsComponents() {
        Map<?, ?> result = (Map<?, ?>) runOk("parseDate");
        assertThat(result.get("year")).isEqualTo(2026);
        assertThat(result.get("month")).isEqualTo(1);
        assertThat(result.get("day")).isEqualTo(15);
    }

    @Test
    void formatDateAppliesPattern() {
        String result = (String) runOk("formatDate");
        assertThat(result).matches("2026-01-15 \\d{2}:30:45");
    }

    @Test
    void dateMathAdds7Days() {
        String result = (String) runOk("dateMath");
        assertThat(result).startsWith("2026-01-22");
    }

    @Test
    void dateDiffReportsDays() {
        Object result = runOk("dateDiff");
        assertThat(((Number) result).doubleValue()).isEqualTo(5.5);
    }

    @Test
    void statsReturnsSummary() {
        Map<?, ?> result = (Map<?, ?>) runOk("stats");
        assertThat(((Number) result.get("count")).intValue()).isEqualTo(10);
        assertThat(((Number) result.get("sum")).intValue()).isEqualTo(55);
        assertThat(((Number) result.get("mean")).doubleValue()).isEqualTo(5.5);
        assertThat(((Number) result.get("median")).doubleValue()).isEqualTo(5.5);
    }

    @Test
    void uuidLooksLikeV4() {
        String result = (String) runOk("uuid");
        assertThat(result).matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void secretPatternDetectFindsAwsAndGithub() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> findings = (List<Map<String, Object>>) runOk("secretPatternDetect");
        assertThat(findings).extracting(f -> f.get("type"))
                .contains("aws-access-key-id", "github-token");
    }

    @Test
    void piiDetectFindsEmailAndCreditCard() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> findings = (List<Map<String, Object>>) runOk("piiDetect");
        assertThat(findings).extracting(f -> f.get("type"))
                .contains("email", "credit-card", "us-ssn");
    }

    @Test
    void base64EncodeDefault() {
        String result = (String) runOk("base64");
        assertThat(result).isEqualTo("aGVsbG8gd29ybGQ=");
    }

    @Test
    void hexEncodeDefault() {
        String result = (String) runOk("hex");
        assertThat(result).isEqualTo("48656c6c6f");
    }

    @Test
    void jwtDecodeReturnsHeaderAndPayload() {
        Map<?, ?> result = (Map<?, ?>) runOk("jwtDecode");
        assertThat(((Map<?, ?>) result.get("header")).get("alg")).isEqualTo("HS256");
        assertThat(((Map<?, ?>) result.get("payload")).get("sub")).isEqualTo("1234567890");
        assertThat(result.get("signaturePresent")).isEqualTo(true);
    }

    @Test
    void hashSha256OfHelloWorld() {
        String result = (String) runOk("hash");
        assertThat(result).isEqualTo("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
    }

    @Test
    void hmacKnownVector() {
        String result = (String) runOk("hmac");
        assertThat(result).matches("[0-9a-f]{64}");
    }

    @Test
    void secureRandomProducesHex() {
        String result = (String) runOk("secureRandom");
        assertThat(result).matches("[0-9a-f]{32}");
    }

    @Test
    void passwordGenerateHasRequestedLength() {
        String result = (String) runOk("passwordGenerate");
        assertThat(result).hasSize(20);
    }

    @Test
    void parseCsvWithHeader() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) runOk("parseCsv");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("name")).isEqualTo("Alice");
        assertThat(rows.get(0).get("age")).isEqualTo("30");
    }

    @Test
    void formatCsvSerialisesRows() {
        String result = (String) runOk("formatCsv");
        assertThat(result).contains("name,age").contains("Alice,30").contains("Bob,25");
    }

    @Test
    void evalExpressionComputesArithmetic() {
        Object result = runOk("evalExpression");
        assertThat(((Number) result).doubleValue()).isEqualTo(28.0);
    }

    @Test
    void timezoneConvertProducesOffsetIso() {
        String result = (String) runOk("timezoneConvert");
        assertThat(result).startsWith("2026-01-15T19:30:00").endsWith("+09:00");
    }

    @Test
    void cronNextProducesScheduledRuns() {
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) runOk("cronNext");
        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isEqualTo("2026-01-16T09:00:00.000Z");
    }

    @Test
    void jwtVerifyValidatesSignature() {
        Map<?, ?> result = (Map<?, ?>) runOk("jwtVerify");
        assertThat(result.get("valid")).isEqualTo(true);
        assertThat(result.get("algorithm")).isEqualTo("HS256");
    }

    @Test
    void getCurrentTimeReturnsIsoStringWithOffset() {
        Map<String, Object> spec = specs.stream()
                .filter(s -> "getCurrentTime".equals(s.get("name"))).findFirst().orElseThrow();
        JsExecutionResult r = executor.execute(new JsExecutionParams(
                Map.of("timeZone", "Asia/Seoul"), (String) spec.get("code")));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        String iso = (String) r.result();
        assertThat(iso).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*\\+09:00");
    }

    @Test
    void sendEmailProducesActionBlock() {
        Map<String, Object> spec = specs.stream()
                .filter(s -> "sendEmail".equals(s.get("name"))).findFirst().orElseThrow();
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("subject", "Sync");
        args.put("body", "Hi Alice");
        args.put("to", "alice@example.com");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args, (String) spec.get("code"), paramNames(spec)));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        String block = (String) r.result();
        assertThat(block).contains("```saip-action-return-direct");
        assertThat(block).contains("\"type\":\"email\"");
        assertThat(block).contains("\"subject\":\"Sync\"");
        assertThat(block).contains("\"to\":\"alice@example.com\"");
        assertThat(block).doesNotContain("\"cc\"");
    }

    @Test
    void addToCalendarProducesActionBlock() {
        Map<String, Object> spec = specs.stream()
                .filter(s -> "addToCalendar".equals(s.get("name"))).findFirst().orElseThrow();
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("title", "Sync");
        args.put("start", "2026-05-13T10:00:00+09:00");
        args.put("end", "2026-05-13T11:00:00+09:00");
        args.put("location", "Zoom");
        JsExecutionResult r = executor.execute(
                new JsExecutionParams(args, (String) spec.get("code"), paramNames(spec)));
        assertThat(r.isOk()).as("%s", r.error()).isTrue();
        String block = (String) r.result();
        assertThat(block).contains("```saip-action-return-direct");
        assertThat(block).contains("\"type\":\"calendar\"");
        assertThat(block).contains("\"title\":\"Sync\"");
        assertThat(block).contains("\"start\":\"2026-05-13T01:00:00.000Z\"");
        assertThat(block).contains("\"location\":\"Zoom\"");
        assertThat(block).doesNotContain("\"description\"");
    }

    @Test
    void sendEmailRejectsMissingRequired() {
        Map<String, Object> spec = builtinSpec("sendEmail");
        JsExecutionResult noSubject = run(spec, Map.of("body", "Hi"));
        assertThat(noSubject.isOk()).isFalse();
        assertThat(noSubject.error()).contains("subject required");
        JsExecutionResult noBody = run(spec, Map.of("subject", "Sync"));
        assertThat(noBody.isOk()).isFalse();
        assertThat(noBody.error()).contains("body required");
    }

    @Test
    void sendEmailIncludesCcAndToWhenSupplied() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("subject", "Sync");
        args.put("body", "Hi");
        args.put("to", "a@example.com");
        args.put("cc", "b@example.com");
        String block = (String) run(builtinSpec("sendEmail"), args).result();
        assertThat(block).contains("\"to\":\"a@example.com\"").contains("\"cc\":\"b@example.com\"");
    }

    @Test
    void addToCalendarRejectsMissingTitle() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("start", "2026-05-13T10:00:00+09:00");
        args.put("end", "2026-05-13T11:00:00+09:00");
        JsExecutionResult r = run(builtinSpec("addToCalendar"), args);
        assertThat(r.isOk()).isFalse();
        assertThat(r.error()).contains("title required");
    }

    @Test
    void addToCalendarRejectsEndNotAfterStart() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("title", "Sync");
        args.put("start", "2026-05-13T11:00:00+09:00");
        args.put("end", "2026-05-13T10:00:00+09:00");
        JsExecutionResult r = run(builtinSpec("addToCalendar"), args);
        assertThat(r.isOk()).isFalse();
        assertThat(r.error()).contains("end must be after start");
    }

    @Test
    void addToCalendarRejectsUnparseableDate() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("title", "Sync");
        args.put("start", "not-a-date");
        args.put("end", "2026-05-13T11:00:00+09:00");
        JsExecutionResult r = run(builtinSpec("addToCalendar"), args);
        assertThat(r.isOk()).isFalse();
        assertThat(r.error()).contains("invalid start");
    }

    @Test
    void showLocationProducesActionBlock() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", "Gyeongbokgung Palace, Seoul");
        String block = (String) run(builtinSpec("showLocation"), args).result();
        assertThat(block).contains("```saip-action").doesNotContain("saip-action-return-direct");
        assertThat(block).contains("\"type\":\"map\"");
        assertThat(block).contains("\"query\":\"Gyeongbokgung Palace, Seoul\"");
    }

    @Test
    void showLocationRejectsMissingQuery() {
        JsExecutionResult r = run(builtinSpec("showLocation"), Map.of());
        assertThat(r.isOk()).isFalse();
        assertThat(r.error()).contains("query required");
    }

    @Test
    void addToCalendarIncludesDescriptionWhenSupplied() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("title", "Sync");
        args.put("start", "2026-05-13T10:00:00+09:00");
        args.put("end", "2026-05-13T11:00:00+09:00");
        args.put("description", "Quarterly planning");
        String block = (String) run(builtinSpec("addToCalendar"), args).result();
        assertThat(block).contains("\"description\":\"Quarterly planning\"");
    }

    @Test
    void addToCalendarRejectsMissingStartAndEnd() {
        Map<String, Object> noStart = new LinkedHashMap<>();
        noStart.put("title", "Sync");
        noStart.put("end", "2026-05-13T11:00:00+09:00");
        JsExecutionResult r1 = run(builtinSpec("addToCalendar"), noStart);
        assertThat(r1.isOk()).isFalse();
        assertThat(r1.error()).contains("start required");
        Map<String, Object> noEnd = new LinkedHashMap<>();
        noEnd.put("title", "Sync");
        noEnd.put("start", "2026-05-13T10:00:00+09:00");
        JsExecutionResult r2 = run(builtinSpec("addToCalendar"), noEnd);
        assertThat(r2.isOk()).isFalse();
        assertThat(r2.error()).contains("end required");
    }

    @Test
    void showLocationIncludesLabelWhenSupplied() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", "Seoul");
        args.put("label", "Capital of Korea");
        String block = (String) run(builtinSpec("showLocation"), args).result();
        assertThat(block).contains("\"label\":\"Capital of Korea\"");
    }

    private Map<String, Object> builtinSpec(String name) {
        return specs.stream().filter(s -> name.equals(s.get("name"))).findFirst().orElseThrow();
    }

    private JsExecutionResult run(Map<String, Object> spec, Map<String, Object> args) {
        return executor.execute(new JsExecutionParams(args, (String) spec.get("code"), paramNames(spec)));
    }

    @SuppressWarnings("unchecked")
    private static List<String> paramNames(Map<String, Object> spec) {
        List<Map<String, Object>> params = (List<Map<String, Object>>) spec.getOrDefault("params", List.of());
        return params.stream().map(p -> (String) p.get("name")).toList();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("networkTools")
    void networkToolHasFetchAndSandbox(String name) {
        Map<String, Object> spec = specs.stream()
                .filter(s -> name.equals(s.get("name"))).findFirst().orElseThrow();
        String code = (String) spec.get("code");
        assertThat(code).as("%s code must use the host fetch helper", name)
                .contains("fetch(");
        assertThat(code).as("%s code must NOT use raw java.net.http.HttpClient", name)
                .doesNotContain("java.net.http.HttpClient");

        @SuppressWarnings("unchecked")
        Map<String, Object> overrides = (Map<String, Object>) spec.get("sandboxOverrides");
        assertThat(overrides).as("%s must declare sandboxOverrides", name).isNotNull();
        String mode = (String) overrides.get("networkMode");
        assertThat(mode).as("%s networkMode must be allowlist or strict (not blocked/open)", name)
                .isIn("allowlist", "strict");
        @SuppressWarnings("unchecked")
        List<String> hostsAllow = (List<String>) overrides.get("hostsAllow");
        if ("allowlist".equals(mode)) {
            assertThat(hostsAllow).as("%s allowlist mode must list at least one allowed host", name).isNotEmpty();
        }

    }

    static Stream<String> networkTools() {
        return Stream.of("extractPageContent", "getWeather", "googlePseSearch",
                "openaiResponseGenerator", "sendSlackMessage",
                "getGithubRepo", "getGithubUser", "listGithubRepoIssues",
                "listGithubRepoReleases", "getGithubLatestRelease",
                "getGithubFileContent", "searchGithubRepos", "listGithubRepoContributors",
                "searchWikipedia", "searchHackerNews", "searchStackOverflow",
                "getCryptoPrice", "convertCurrency",
                "getIpInfo", "getCountryInfo", "searchArxiv", "getPublicHolidays",
                "getOpenMeteoForecast", "geocodeAddress",
                "getSunriseSunset", "getRecentEarthquakes",
                "getUpbitTicker", "getUpbitOrderbook", "getUpbitCandles", "listUpbitMarkets",
                "getBithumbTicker", "getBithumbOrderbook",
                "searchNaver", "searchKakaoLocal", "getAirKoreaPm",
                "searchKoreaTour", "searchKpopOnItunes", "searchKBeautyProducts",
                "searchSeoulCulturalEvents",
                "getKamisAgriPrice", "getKoficBoxOffice", "getKrxStockPrice",
                "callDataGoKrOpenApi",
                "getKmaShortTermForecast", "getApartmentTradePrice",
                "searchKoreaDrugInfo", "getKoreaEmergencyAlerts");
    }

    @Test
    void userFacingErrorMessageStripsJavaFqnFromHostException() {
        JsHelperException inner = new JsHelperException(
                JsHelperException.Kind.SECURITY, "fetch", "host-not-in-allowlist",
                "[fetch] denied: host not in allowlist: spring.io");
        ExecutionException wrapped = new ExecutionException(inner);
        String msg = JsToolExecutor.userFacingErrorMessage(wrapped);
        assertThat(msg).isEqualTo("[fetch] denied: host not in allowlist: spring.io");
        assertThat(msg).doesNotContain("SafeHttpFetch");
        assertThat(msg).doesNotContain("org.springaicommunity");
        assertThat(msg).doesNotContain("ExecutionException");
    }

    @Test
    void originalGuideToolsCarryExampleTag() {
        Set<String> originals = Set.of(
                "extractPageContent", "getCurrentTime",
                "getWeather", "googlePseSearch", "openaiResponseGenerator", "sendSlackMessage");
        for (Map<String, Object> spec : specs) {
            if (originals.contains((String) spec.get("name"))) {
                @SuppressWarnings("unchecked")
                List<String> tags = (List<String>) spec.get("tags");
                assertThat(tags)
                        .as("original guide tool %s must carry the 'example' tag", spec.get("name"))
                        .contains("example");
            }
        }
    }

    @Test
    void newKeylessNetworkToolsUseAllowlistOrStrictMode() {
        Set<String> newKeyless = Set.of(
                "getGithubRepo", "searchWikipedia", "searchHackerNews", "searchStackOverflow");
        for (Map<String, Object> spec : specs) {
            if (newKeyless.contains((String) spec.get("name"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> overrides = (Map<String, Object>) spec.get("sandboxOverrides");
                String mode = overrides == null ? null : (String) overrides.get("networkMode");
                assertThat(mode)
                        .as("%s must use 'allowlist' or 'strict' network mode", spec.get("name"))
                        .isIn("allowlist", "strict");
            }
        }
    }

    @Test
    void everyBuiltinCarriesToolSafetyManifest() {
        Set<String> builtins = Set.of(
                "urlEncode", "regexExtract", "regexReplace", "diffText", "sortLines",
                "parseDate", "formatDate", "dateMath", "dateDiff", "timezoneConvert", "cronNext",
                "stats", "evalExpression", "secretPatternDetect", "piiDetect", "formatCsv",
                "base64", "hex", "uuid", "hash", "hmac", "secureRandom", "passwordGenerate",
                "jwtDecode", "jwtVerify", "parseCsv");
        for (Map<String, Object> spec : specs) {
            if (!builtins.contains((String) spec.get("name"))) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> ts = (Map<String, Object>) spec.get("toolSafety");
            assertThat(ts).as("%s missing toolSafety manifest", spec.get("name")).isNotNull();
            assertThat(ts.get("version")).isEqualTo("1.0");
            @SuppressWarnings("unchecked")
            Map<String, Object> runtime = (Map<String, Object>) ts.get("runtime");
            assertThat(runtime.get("id")).isEqualTo("spring-ai-playground/polyglot-js");
            assertThat(runtime.get("ecmaVersion")).isEqualTo("2024");
            assertThat(runtime.get("javaInterop")).isEqualTo(false);
        }
    }

    private static Object runOk(String name) {
        Map<String, Object> spec = specs.stream()
                .filter(s -> name.equals(s.get("name"))).findFirst().orElseThrow();
        JsExecutionResult result = runSpec(spec);
        assertThat(result.isOk())
                .as("tool %s — error: %s, debug: %s", name, result.error(), result.debugInfo())
                .isTrue();
        return result.result();
    }

    private static JsExecutionResult runSpec(Map<String, Object> spec) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> params = (List<Map<String, Object>>) spec.getOrDefault("params", List.of());
        Map<String, Object> args = new LinkedHashMap<>();
        for (Map<String, Object> param : params) {
            String testValue = (String) param.get("testValue");
            String type = (String) param.get("type");
            args.put((String) param.get("name"), convertTestValue(testValue, type));
        }
        return executor.execute(new JsExecutionParams(args, (String) spec.get("code")));
    }

    private static Object convertTestValue(String raw, String type) {
        boolean blank = raw == null || raw.isBlank();
        try {
            return switch (type.toUpperCase(Locale.ROOT)) {
                case "STRING" -> raw == null ? "" : raw;
                case "NUMBER" -> blank ? null : Double.parseDouble(raw);
                case "INTEGER" -> blank ? null : Long.parseLong(raw);
                case "BOOLEAN" -> blank ? null : Boolean.parseBoolean(raw);
                case "ARRAY" -> blank ? List.of()
                        : new ObjectMapper().readValue(raw, new TypeReference<List<Object>>() {});
                case "OBJECT" -> blank ? Map.of()
                        : new ObjectMapper().readValue(raw, new TypeReference<Map<String, Object>>() {});
                default -> raw;
            };
        } catch (Exception e) {
            throw new RuntimeException("failed to coerce testValue=" + raw + " to type=" + type, e);
        }
    }
}
