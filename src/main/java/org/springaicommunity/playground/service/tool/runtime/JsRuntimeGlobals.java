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

import tools.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyInstantiable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.jsoup.Jsoup;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.NetworkPolicy;
import org.springaicommunity.playground.service.tool.policy.EffectivePolicyResolver.EffectivePolicy;
import org.springaicommunity.playground.service.tool.runtime.SafeHttpFetch.FetchResponse;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.yaml.snakeyaml.Yaml;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class JsRuntimeGlobals {

    public static final Set<String> INJECTED_NAMES =
            Set.of("console", "fetch", "URL", "URLSearchParams", "atob", "btoa", "crypto", "safety");

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final Set<String> SUPPORTED_HASHES =
            Set.of("SHA-256", "SHA-384", "SHA-512");
    private static final int MAX_RANDOM_BYTES = 65536;
    private static final NetworkPolicy STRICT_FALLBACK =
            new NetworkPolicy("strict", Set.of(), Set.of());

    private JsRuntimeGlobals() {}

    public static void installAll(Value bindings, EffectivePolicy policy, SafeFs.FsScope fsScope) {
        installFetch(bindings, policy);
        installUrl(bindings);
        installBase64(bindings);
        installCrypto(bindings);
        installSafety(bindings, policy, fsScope);
    }

    public static void installSafety(Value bindings, EffectivePolicy policy, SafeFs.FsScope fsScope) {
        Map<String, Object> parser = new LinkedHashMap<>();
        parser.put("html", (ProxyExecutable) JsRuntimeGlobals::parseHtml);
        parser.put("yaml", (ProxyExecutable) JsRuntimeGlobals::parseYaml);
        parser.put("csv", (ProxyExecutable) JsRuntimeGlobals::parseCsv);
        parser.put("xml", (ProxyExecutable) JsRuntimeGlobals::parseXml);

        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("parser", ProxyObject.fromMap(parser));
        if (policy != null && (policy.fileRead() || policy.fileWrite()) && fsScope != null) {
            safety.put("fs", fsProxy(policy, fsScope));
        }
        bindings.putMember("safety", ProxyObject.fromMap(safety));
    }

    private static ProxyObject fsProxy(EffectivePolicy policy, SafeFs.FsScope scope) {
        Map<String, Object> fs = new LinkedHashMap<>();
        fs.put("workspace", (ProxyExecutable) args -> scope.workspace().toString());
        fs.put("readRoots", (ProxyExecutable) args -> ProxyArray.fromArray(
                (Object[]) scope.readRoots().stream().map(Path::toString).toArray(String[]::new)));
        if (policy.fileRead()) {
            fs.put("readText", (ProxyExecutable) args -> {
                try {
                    return SafeFs.readText(scope, args.length > 0 ? args[0].asString() : null);
                } catch (java.io.IOException e) {
                    throw new JsHelperException(JsHelperException.Kind.HELPER_RUNTIME, "safety.fs.readText",
                            "io", "safety.fs.readText: " + e.getMessage(), e);
                }
            });
            fs.put("list", (ProxyExecutable) args -> {
                try {
                    String dir = args.length > 0 && !args[0].isNull() ? args[0].asString() : ".";
                    return ProxyArray.fromArray((Object[]) SafeFs.list(scope, dir).toArray(new String[0]));
                } catch (java.io.IOException e) {
                    throw new JsHelperException(JsHelperException.Kind.HELPER_RUNTIME, "safety.fs.list",
                            "io", "safety.fs.list: " + e.getMessage(), e);
                }
            });
            fs.put("exists", (ProxyExecutable) args ->
                    SafeFs.exists(scope, args.length > 0 ? args[0].asString() : null));
            fs.put("stat", (ProxyExecutable) args -> {
                try {
                    SafeFs.FileStat s = SafeFs.stat(scope, args.length > 0 ? args[0].asString() : null);
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("size", s.size());
                    out.put("mtime", s.mtime());
                    out.put("directory", s.directory());
                    return ProxyObject.fromMap(out);
                } catch (java.io.IOException e) {
                    throw new JsHelperException(JsHelperException.Kind.HELPER_RUNTIME, "safety.fs.stat",
                            "io", "safety.fs.stat: " + e.getMessage(), e);
                }
            });
            fs.put("grep", (ProxyExecutable) args -> {
                if (args.length < 2)
                    throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "safety.fs.grep",
                            "missing-args", "safety.fs.grep: requires pattern and path");
                String pattern = args[0].asString();
                String path = args[1].asString();
                Value opts = args.length > 2 && !args[2].isNull() ? args[2] : null;
                boolean caseInsensitive = opts != null && opts.hasMember("caseInsensitive")
                        && opts.getMember("caseInsensitive").asBoolean();
                int limit = opts != null && opts.hasMember("limit") && !opts.getMember("limit").isNull()
                        ? opts.getMember("limit").asInt() : 0;
                boolean numbered = opts != null && opts.hasMember("numbered")
                        && opts.getMember("numbered").asBoolean();
                try {
                    return ProxyArray.fromArray((Object[]) SafeFs.grep(scope, path, pattern, caseInsensitive,
                            limit, numbered).toArray(new String[0]));
                } catch (java.io.IOException e) {
                    throw new JsHelperException(JsHelperException.Kind.HELPER_RUNTIME, "safety.fs.grep",
                            "io", "safety.fs.grep: " + e.getMessage(), e);
                }
            });
            fs.put("lineCount", (ProxyExecutable) args -> {
                try {
                    return SafeFs.lineCount(scope, args.length > 0 ? args[0].asString() : null);
                } catch (java.io.IOException e) {
                    throw new JsHelperException(JsHelperException.Kind.HELPER_RUNTIME, "safety.fs.lineCount",
                            "io", "safety.fs.lineCount: " + e.getMessage(), e);
                }
            });
            fs.put("slice", (ProxyExecutable) args -> {
                if (args.length < 1)
                    throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "safety.fs.slice",
                            "missing-args", "safety.fs.slice: requires path");
                String path = args[0].asString();
                Integer start = args.length > 1 && !args[1].isNull() ? args[1].asInt() : null;
                Integer end = args.length > 2 && !args[2].isNull() ? args[2].asInt() : null;
                try {
                    return ProxyArray.fromArray((Object[]) SafeFs.slice(scope, path, start, end)
                            .toArray(new String[0]));
                } catch (java.io.IOException e) {
                    throw new JsHelperException(JsHelperException.Kind.HELPER_RUNTIME, "safety.fs.slice",
                            "io", "safety.fs.slice: " + e.getMessage(), e);
                }
            });
            fs.put("cut", (ProxyExecutable) args -> {
                if (args.length < 2)
                    throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "safety.fs.cut",
                            "missing-args", "safety.fs.cut: requires path and opts");
                String path = args[0].asString();
                Value opts = args[1];
                if (opts.isNull() || !opts.hasMember("fields")) {
                    throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "safety.fs.cut",
                            "fields-required", "safety.fs.cut: opts.fields required");
                }
                Value fieldsVal = opts.getMember("fields");
                if (!fieldsVal.hasArrayElements()) {
                    throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "safety.fs.cut",
                            "fields-not-array", "safety.fs.cut: opts.fields must be an array");
                }
                java.util.List<Integer> fields = new java.util.ArrayList<>();
                for (long i = 0; i < fieldsVal.getArraySize(); i++) {
                    fields.add(fieldsVal.getArrayElement(i).asInt());
                }
                String delimiter = opts.hasMember("delimiter") && !opts.getMember("delimiter").isNull()
                        ? opts.getMember("delimiter").asString() : null;
                boolean regex = opts.hasMember("regex") && opts.getMember("regex").asBoolean();
                try {
                    return ProxyArray.fromArray((Object[]) SafeFs.cut(scope, path, delimiter, fields, regex)
                            .toArray(new String[0]));
                } catch (java.io.IOException e) {
                    throw new JsHelperException(JsHelperException.Kind.HELPER_RUNTIME, "safety.fs.cut",
                            "io", "safety.fs.cut: " + e.getMessage(), e);
                }
            });
            fs.put("sort", (ProxyExecutable) args -> {
                if (args.length < 1)
                    throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "safety.fs.sort",
                            "missing-args", "safety.fs.sort: requires path");
                String path = args[0].asString();
                Value opts = args.length > 1 && !args[1].isNull() ? args[1] : null;
                boolean reverse = opts != null && opts.hasMember("reverse") && opts.getMember("reverse").asBoolean();
                boolean numeric = opts != null && opts.hasMember("numeric") && opts.getMember("numeric").asBoolean();
                boolean caseInsensitive = opts != null && opts.hasMember("caseInsensitive")
                        && opts.getMember("caseInsensitive").asBoolean();
                boolean unique = opts != null && opts.hasMember("unique") && opts.getMember("unique").asBoolean();
                try {
                    return ProxyArray.fromArray((Object[]) SafeFs.sort(scope, path, reverse, numeric,
                            caseInsensitive, unique).toArray(new String[0]));
                } catch (java.io.IOException e) {
                    throw new JsHelperException(JsHelperException.Kind.HELPER_RUNTIME, "safety.fs.sort",
                            "io", "safety.fs.sort: " + e.getMessage(), e);
                }
            });
            fs.put("find", (ProxyExecutable) args -> {
                String dir = args.length > 0 && !args[0].isNull() ? args[0].asString() : ".";
                String glob = args.length > 1 && !args[1].isNull() ? args[1].asString() : "*";
                Value opts = args.length > 2 && !args[2].isNull() ? args[2] : null;
                int maxDepth = opts != null && opts.hasMember("maxDepth") && !opts.getMember("maxDepth").isNull()
                        ? opts.getMember("maxDepth").asInt() : 0;
                String type = opts != null && opts.hasMember("type") && !opts.getMember("type").isNull()
                        ? opts.getMember("type").asString() : null;
                try {
                    return ProxyArray.fromArray((Object[]) SafeFs.find(scope, dir, glob, maxDepth, type)
                            .toArray(new String[0]));
                } catch (java.io.IOException e) {
                    throw new JsHelperException(JsHelperException.Kind.HELPER_RUNTIME, "safety.fs.find",
                            "io", "safety.fs.find: " + e.getMessage(), e);
                }
            });
        }
        if (policy.fileWrite()) {
            fs.put("writeText", (ProxyExecutable) args -> {
                try {
                    return SafeFs.writeText(scope,
                            args.length > 0 ? args[0].asString() : null,
                            args.length > 1 && !args[1].isNull() ? args[1].asString() : "").toString();
                } catch (java.io.IOException e) {
                    throw new JsHelperException(JsHelperException.Kind.HELPER_RUNTIME, "safety.fs.writeText",
                            "io", "safety.fs.writeText: " + e.getMessage(), e);
                }
            });
        }
        return ProxyObject.fromMap(fs);
    }

    private static Object parseHtml(Value[] args) {
        if (args.length == 0 || args[0] == null || args[0].isNull()) {
            throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "safety.parser.html",
                    "missing-input", "safety.parser.html: missing input");
        }
        return Jsoup.parse(args[0].asString());
    }

    private static Object parseYaml(Value[] args) {
        if (args.length == 0 || args[0] == null || args[0].isNull()) {
            throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "safety.parser.yaml",
                    "missing-input", "safety.parser.yaml: missing input");
        }
        try {
            return jsonToProxy(new Yaml().load(args[0].asString()));
        } catch (Exception e) {
            throw new JsHelperException(JsHelperException.Kind.HELPER_RUNTIME, "safety.parser.yaml",
                    "parse-failed", "safety.parser.yaml: " + e.getMessage(), e);
        }
    }

    private static Object parseCsv(Value[] args) {
        if (args.length == 0 || args[0] == null || args[0].isNull()) {
            throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "safety.parser.csv",
                    "missing-input", "safety.parser.csv: missing input");
        }
        String input = args[0].asString();
        Value opts = args.length > 1 && args[1] != null && !args[1].isNull() ? args[1] : null;
        boolean header = opts != null && opts.hasMember("header")
                && !opts.getMember("header").isNull() && opts.getMember("header").asBoolean();
        char delimiter = ',';
        if (opts != null && opts.hasMember("delimiter") && !opts.getMember("delimiter").isNull()) {
            String d = opts.getMember("delimiter").asString();
            if (!d.isEmpty()) delimiter = d.charAt(0);
        }
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(delimiter).get();
        if (header) {
            format = format.builder().setHeader().setSkipHeaderRecord(true).get();
        }
        try (CSVParser parser = CSVParser.parse(new StringReader(input), format)) {
            List<Object> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (header) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    record.toMap().forEach(row::put);
                    rows.add(row);
                } else {
                    List<String> row = new ArrayList<>();
                    record.forEach(row::add);
                    rows.add(row);
                }
            }
            return jsonToProxy(rows);
        } catch (Exception e) {
            throw new JsHelperException(JsHelperException.Kind.HELPER_RUNTIME, "safety.parser.csv",
                    "parse-failed", "safety.parser.csv: " + e.getMessage(), e);
        }
    }

    private static Object parseXml(Value[] args) {
        if (args.length == 0 || args[0] == null || args[0].isNull()) {
            throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "safety.parser.xml",
                    "missing-input", "safety.parser.xml: missing input");
        }
        String input = args[0].asString();
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            org.w3c.dom.Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(input)));
            return xmlElementToProxy(doc.getDocumentElement());
        } catch (Exception e) {
            throw new JsHelperException(JsHelperException.Kind.HELPER_RUNTIME, "safety.parser.xml",
                    "parse-failed", "safety.parser.xml: " + e.getMessage(), e);
        }
    }

    private static Object xmlElementToProxy(Element elem) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        NamedNodeMap attributes = elem.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            attrs.put(attr.getNodeName(), attr.getNodeValue());
        }
        List<Object> children = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        NodeList nodes = elem.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                children.add(xmlElementToProxy((Element) n));
            } else if (n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE) {
                text.append(n.getNodeValue());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tag", elem.getTagName());
        out.put("attrs", ProxyObject.fromMap(attrs));
        out.put("text", text.toString().trim());
        out.put("children", ProxyArray.fromArray(children.toArray()));
        return ProxyObject.fromMap(out);
    }

    public static void installFetch(Value bindings, EffectivePolicy policy) {
        if (policy == null || !policy.allowNetworkIo()) return;
        NetworkPolicy net = policy.network() == null ? STRICT_FALLBACK : policy.network();
        if ("blocked".equalsIgnoreCase(net.egressLevel())) return;
        bindings.putMember("fetch", (ProxyExecutable) args -> jsFetch(args, net));
    }

    private static Object jsFetch(Value[] args, NetworkPolicy net) {
        if (args.length == 0 || args[0] == null || args[0].isNull()) {
            throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "fetch", "missing-url",
                    "[fetch] missing url argument");
        }
        String url = args[0].asString();
        Value init = args.length > 1 && args[1] != null && !args[1].isNull() ? args[1] : null;

        String method = "GET";
        Map<String, String> headers = new LinkedHashMap<>();
        byte[] body = null;
        Integer maxLength = null;
        Integer startIndex = null;
        if (init != null && init.hasMembers()) {
            if (init.hasMember("method") && !init.getMember("method").isNull()) {
                method = init.getMember("method").asString();
            }
            if (init.hasMember("headers") && !init.getMember("headers").isNull()) {
                Value h = init.getMember("headers");
                for (String key : h.getMemberKeys()) {
                    Value v = h.getMember(key);
                    if (v != null && !v.isNull()) headers.put(key, v.asString());
                }
            }
            if (init.hasMember("body") && !init.getMember("body").isNull()) {
                Value b = init.getMember("body");
                if (b.isString()) {
                    body = b.asString().getBytes(StandardCharsets.UTF_8);
                } else if (b.hasArrayElements()) {
                    int n = (int) b.getArraySize();
                    byte[] tmp = new byte[n];
                    for (int i = 0; i < n; i++) tmp[i] = (byte) b.getArrayElement(i).asInt();
                    body = tmp;
                }
            }
            if (init.hasMember("maxLength") && !init.getMember("maxLength").isNull()) {
                maxLength = init.getMember("maxLength").asInt();
            }
            if (init.hasMember("startIndex") && !init.getMember("startIndex").isNull()) {
                startIndex = init.getMember("startIndex").asInt();
            }
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "fetch", "invalid-url",
                    "[fetch] invalid url: " + url);
        }
        return buildResponseProxy(SafeHttpFetch.fetch(uri, method, headers, body, net), maxLength, startIndex);
    }

    private static Object buildResponseProxy(FetchResponse resp, Integer maxLength, Integer startIndex) {
        byte[] body = resp.body();
        int total = body.length;
        int start = startIndex == null ? 0 : Math.max(0, Math.min(startIndex, total));
        int sliceLen = total - start;
        Integer nextStartIndex = null;
        boolean truncated = false;
        if (maxLength != null && maxLength > 0 && sliceLen > maxLength) {
            sliceLen = maxLength;
            nextStartIndex = start + sliceLen;
            truncated = true;
        }
        final int finalStart = start;
        final int finalLen = sliceLen;
        Map<String, String> headers = resp.headers();
        String contentType = headers.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getKey().equalsIgnoreCase("Content-Type"))
                .findFirst().map(Map.Entry::getValue).orElse(null);

        Map<String, Object> proxy = new LinkedHashMap<>();
        proxy.put("status", resp.status());
        proxy.put("ok", resp.status() >= 200 && resp.status() < 300);
        proxy.put("statusText", httpReason(resp.status()));
        proxy.put("url", resp.finalUrl());
        proxy.put("truncated", truncated);
        proxy.put("nextStartIndex", nextStartIndex);
        proxy.put("contentType", contentType);
        proxy.put("headers", ProxyObject.fromMap(Map.of(
                "get", (ProxyExecutable) hArgs -> {
                    if (hArgs.length == 0 || hArgs[0] == null || hArgs[0].isNull()) return null;
                    String name = hArgs[0].asString();
                    for (Map.Entry<String, String> e : headers.entrySet()) {
                        if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) return e.getValue();
                    }
                    return null;
                }
        )));
        proxy.put("text", (ProxyExecutable) tArgs ->
                new String(body, finalStart, finalLen, StandardCharsets.UTF_8));
        proxy.put("json", (ProxyExecutable) jArgs -> {
            String s = new String(body, finalStart, finalLen, StandardCharsets.UTF_8);
            try {
                return jsonToProxy(JSON_MAPPER.readValue(s, Object.class));
            } catch (Exception e) {
                throw new JsHelperException(JsHelperException.Kind.HELPER_RUNTIME, "fetch", "invalid-json",
                        "[fetch] invalid json: " + e.getMessage(), e);
            }
        });
        proxy.put("arrayBuffer", (ProxyExecutable) aArgs -> {
            byte[] slice = new byte[finalLen];
            System.arraycopy(body, finalStart, slice, 0, finalLen);
            return slice;
        });
        return ProxyObject.fromMap(proxy);
    }

    private static Object jsonToProxy(Object node) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(k.toString(), jsonToProxy(v)));
            return ProxyObject.fromMap(out);
        }
        if (node instanceof List<?> list) {
            Object[] arr = new Object[list.size()];
            int i = 0;
            for (Object v : list) arr[i++] = jsonToProxy(v);
            return ProxyArray.fromArray(arr);
        }
        return node;
    }

    private static String httpReason(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 304 -> "Not Modified";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            default -> "";
        };
    }

    public static void installUrl(Value bindings) {
        bindings.putMember("URL", (ProxyInstantiable) JsRuntimeGlobals::newUrl);
        bindings.putMember("URLSearchParams", (ProxyInstantiable) JsRuntimeGlobals::newSearchParams);
    }

    public static void installBase64(Value bindings) {
        bindings.putMember("btoa", (ProxyExecutable) JsRuntimeGlobals::btoa);
        bindings.putMember("atob", (ProxyExecutable) JsRuntimeGlobals::atob);
    }

    public static void installCrypto(Value bindings) {
        Map<String, Object> subtle = new LinkedHashMap<>();
        subtle.put("digest", (ProxyExecutable) JsRuntimeGlobals::cryptoDigest);
        subtle.put("sign", (ProxyExecutable) JsRuntimeGlobals::cryptoSign);
        subtle.put("importKey", (ProxyExecutable) JsRuntimeGlobals::cryptoImportKey);

        Map<String, Object> crypto = new LinkedHashMap<>();
        crypto.put("randomUUID", (ProxyExecutable) args -> UUID.randomUUID().toString());
        crypto.put("getRandomValues", (ProxyExecutable) JsRuntimeGlobals::cryptoGetRandomValues);
        crypto.put("subtle", ProxyObject.fromMap(subtle));

        bindings.putMember("crypto", ProxyObject.fromMap(crypto));
    }

    static Object newUrl(Value[] args) {
        if (args.length == 0 || args[0] == null || args[0].isNull()) {
            throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "URL", "missing-input",
                    "URL: missing input");
        }
        String input = args[0].asString();
        String base = args.length > 1 && args[1] != null && !args[1].isNull() ? args[1].asString() : null;
        URI uri;
        try {
            if (base != null) {
                uri = URI.create(base).resolve(input);
            } else {
                uri = URI.create(input);
            }
        } catch (IllegalArgumentException e) {
            throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "URL", "invalid-url",
                    "URL: invalid url: " + input);
        }
        return urlProxy(uri);
    }

    static Object newSearchParams(Value[] args) {
        List<String[]> store = new ArrayList<>();
        if (args.length > 0 && args[0] != null && !args[0].isNull()) {
            Value v = args[0];
            if (v.isString()) {
                String s = v.asString();
                if (s.startsWith("?")) s = s.substring(1);
                for (String[] pair : parseQuery(s)) {
                    store.add(pair);
                }
            } else if (v.hasArrayElements()) {
                long n = v.getArraySize();
                for (long i = 0; i < n; i++) {
                    Value pair = v.getArrayElement(i);
                    if (pair.hasArrayElements() && pair.getArraySize() >= 2) {
                        store.add(new String[]{pair.getArrayElement(0).asString(),
                                pair.getArrayElement(1).asString()});
                    }
                }
            } else if (v.hasMembers()) {
                for (String k : v.getMemberKeys()) {
                    Value mv = v.getMember(k);
                    store.add(new String[]{k, mv == null || mv.isNull() ? "" : mv.asString()});
                }
            }
        }
        return spProxy(store);
    }

    private static ProxyObject urlProxy(URI uri) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("href", uri.toString());
        m.put("protocol", uri.getScheme() == null ? "" : uri.getScheme() + ":");
        m.put("host", uri.getAuthority() == null ? "" : uri.getAuthority());
        m.put("hostname", uri.getHost() == null ? "" : uri.getHost());
        m.put("port", uri.getPort() == -1 ? "" : String.valueOf(uri.getPort()));
        m.put("pathname", uri.getRawPath() == null ? "" : uri.getRawPath());
        String rawQuery = uri.getRawQuery();
        m.put("search", rawQuery == null || rawQuery.isEmpty() ? "" : "?" + rawQuery);
        String rawFragment = uri.getRawFragment();
        m.put("hash", rawFragment == null || rawFragment.isEmpty() ? "" : "#" + rawFragment);
        m.put("origin", calcOrigin(uri));
        m.put("searchParams", spProxy(new ArrayList<>(parseQuery(rawQuery))));
        m.put("toString", (ProxyExecutable) a -> uri.toString());
        return ProxyObject.fromMap(m);
    }

    private static String calcOrigin(URI uri) {
        if (uri.getScheme() == null || uri.getHost() == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append(uri.getScheme()).append("://").append(uri.getHost());
        if (uri.getPort() != -1) sb.append(":").append(uri.getPort());
        return sb.toString();
    }

    static List<String[]> parseQuery(String rawQuery) {
        List<String[]> entries = new ArrayList<>();
        if (rawQuery == null || rawQuery.isEmpty()) return entries;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String val = eq < 0 ? "" : pair.substring(eq + 1);
            entries.add(new String[]{
                    URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(val, StandardCharsets.UTF_8)
            });
        }
        return entries;
    }

    static ProxyObject spProxy(List<String[]> store) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("get", (ProxyExecutable) a -> {
            if (a.length == 0) return null;
            String n = a[0].asString();
            for (String[] kv : store) if (kv[0].equals(n)) return kv[1];
            return null;
        });
        m.put("getAll", (ProxyExecutable) a -> {
            if (a.length == 0) return ProxyArray.fromArray();
            String n = a[0].asString();
            List<String> out = new ArrayList<>();
            for (String[] kv : store) if (kv[0].equals(n)) out.add(kv[1]);
            return ProxyArray.fromArray((Object[]) out.toArray(new String[0]));
        });
        m.put("has", (ProxyExecutable) a -> {
            if (a.length == 0) return false;
            String n = a[0].asString();
            for (String[] kv : store) if (kv[0].equals(n)) return true;
            return false;
        });
        m.put("set", (ProxyExecutable) a -> {
            if (a.length < 2) return null;
            String n = a[0].asString();
            String v = a[1].asString();
            boolean replaced = false;
            Iterator<String[]> it = store.iterator();
            while (it.hasNext()) {
                String[] kv = it.next();
                if (kv[0].equals(n)) {
                    if (!replaced) {
                        kv[1] = v;
                        replaced = true;
                    } else {
                        it.remove();
                    }
                }
            }
            if (!replaced) store.add(new String[]{n, v});
            return null;
        });
        m.put("append", (ProxyExecutable) a -> {
            if (a.length < 2) return null;
            store.add(new String[]{a[0].asString(), a[1].asString()});
            return null;
        });
        m.put("delete", (ProxyExecutable) a -> {
            if (a.length == 0) return null;
            String n = a[0].asString();
            store.removeIf(kv -> kv[0].equals(n));
            return null;
        });
        m.put("toString", (ProxyExecutable) a -> {
            StringBuilder sb = new StringBuilder();
            for (String[] kv : store) {
                if (sb.length() > 0) sb.append("&");
                sb.append(URLEncoder.encode(kv[0], StandardCharsets.UTF_8));
                sb.append("=");
                sb.append(URLEncoder.encode(kv[1], StandardCharsets.UTF_8));
            }
            return sb.toString();
        });
        return ProxyObject.fromMap(m);
    }

    static Object btoa(Value[] args) {
        if (args.length == 0 || args[0] == null || args[0].isNull()) {
            throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "btoa", "missing-input",
                    "btoa: missing input");
        }
        String input = args[0].asString();
        byte[] bytes = new byte[input.length()];
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c > 0xFF) {
                throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "btoa", "non-latin-1",
                        "btoa: input contains non-Latin-1 character at index " + i);
            }
            bytes[i] = (byte) c;
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    static Object atob(Value[] args) {
        if (args.length == 0 || args[0] == null || args[0].isNull()) {
            throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "atob", "missing-input",
                    "atob: missing input");
        }
        String input = args[0].asString();
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(input);
        } catch (IllegalArgumentException e) {
            throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "atob", "invalid-base64",
                    "atob: invalid base64", e);
        }
        StringBuilder sb = new StringBuilder(bytes.length);
        for (byte b : bytes) sb.append((char) (b & 0xFF));
        return sb.toString();
    }

    private static Object cryptoDigest(Value[] args) {
        if (args.length < 2) {
            throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "crypto.subtle.digest",
                    "missing-args", "crypto.subtle.digest: requires algorithm and data");
        }
        String alg = normalizeAlgorithm(args[0]);
        if (!SUPPORTED_HASHES.contains(alg)) {
            throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "crypto.subtle.digest",
                    "unsupported-algorithm", "crypto.subtle.digest: unsupported algorithm: " + alg);
        }
        byte[] data = readBytes(args[1]);
        try {
            MessageDigest md = MessageDigest.getInstance(alg);
            return toUnsignedProxyArray(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new JsHelperException(JsHelperException.Kind.HELPER_RUNTIME, "crypto.subtle.digest",
                    "no-such-algorithm", "crypto.subtle.digest: " + e.getMessage(), e);
        }
    }

    private static Object cryptoSign(Value[] args) {
        if (args.length < 3) {
            throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "crypto.subtle.sign",
                    "missing-args", "crypto.subtle.sign: requires algorithm, key, data");
        }
        String alg = normalizeAlgorithm(args[0]);
        if (!alg.equals("HMAC")) {
            throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "crypto.subtle.sign",
                    "unsupported-algorithm", "crypto.subtle.sign: only HMAC supported, got: " + alg);
        }
        Value keyVal = args[1];
        if (keyVal == null || !keyVal.hasMember("__isHmacKey")
                || !keyVal.getMember("__isHmacKey").asBoolean()) {
            throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "crypto.subtle.sign",
                    "invalid-key", "crypto.subtle.sign: invalid HMAC key");
        }
        return keyVal.getMember("__sign").execute(args[2]);
    }

    private static Object cryptoImportKey(Value[] args) {
        if (args.length < 3) {
            throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "crypto.subtle.importKey",
                    "missing-args", "crypto.subtle.importKey: requires format, keyData, algorithm");
        }
        String format = args[0].asString();
        if (!format.equals("raw")) {
            throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "crypto.subtle.importKey",
                    "unsupported-format",
                    "crypto.subtle.importKey: only 'raw' format supported, got: " + format);
        }
        byte[] keyBytes = readBytes(args[1]);
        Value algValue = args[2];
        String algName;
        String hashAlg;
        if (algValue.isString()) {
            algName = algValue.asString();
            hashAlg = "SHA-256";
        } else if (algValue.hasMembers()) {
            algName = algValue.getMember("name").asString();
            Value hashMember = algValue.getMember("hash");
            if (hashMember == null || hashMember.isNull()) {
                hashAlg = "SHA-256";
            } else if (hashMember.isString()) {
                hashAlg = hashMember.asString();
            } else {
                hashAlg = hashMember.getMember("name").asString();
            }
        } else {
            throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "crypto.subtle.importKey",
                    "invalid-algorithm", "crypto.subtle.importKey: invalid algorithm");
        }
        if (!algName.equals("HMAC")) {
            throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "crypto.subtle.importKey",
                    "unsupported-algorithm",
                    "crypto.subtle.importKey: only HMAC supported, got: " + algName);
        }
        if (!SUPPORTED_HASHES.contains(hashAlg)) {
            throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "crypto.subtle.importKey",
                    "unsupported-hash", "crypto.subtle.importKey: unsupported hash: " + hashAlg);
        }

        final byte[] capturedKey = keyBytes.clone();
        final String macName = "Hmac" + hashAlg.replace("-", "");

        Map<String, Object> keyObj = new LinkedHashMap<>();
        keyObj.put("type", "secret");
        keyObj.put("extractable", false);
        keyObj.put("algorithm", ProxyObject.fromMap(Map.of(
                "name", "HMAC",
                "hash", ProxyObject.fromMap(Map.of("name", hashAlg))
        )));
        keyObj.put("__isHmacKey", true);
        keyObj.put("__sign", (ProxyExecutable) signArgs -> {
            byte[] data = readBytes(signArgs[0]);
            try {
                Mac mac = Mac.getInstance(macName);
                mac.init(new SecretKeySpec(capturedKey, macName));
                return toUnsignedProxyArray(mac.doFinal(data));
            } catch (Exception e) {
                throw new JsHelperException(JsHelperException.Kind.HELPER_RUNTIME, "crypto.subtle.sign",
                        "sign-failed", "crypto.subtle.sign: " + e.getMessage(), e);
            }
        });
        return ProxyObject.fromMap(keyObj);
    }

    private static Object cryptoGetRandomValues(Value[] args) {
        if (args.length == 0 || args[0] == null || args[0].isNull()) {
            throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "crypto.getRandomValues",
                    "missing-args", "crypto.getRandomValues: missing argument");
        }
        Value arr = args[0];
        if (!arr.hasArrayElements()) {
            throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "crypto.getRandomValues",
                    "not-array", "crypto.getRandomValues: argument must be an array-like typed array");
        }
        int n = (int) arr.getArraySize();
        if (n > MAX_RANDOM_BYTES) {
            throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "crypto.getRandomValues",
                    "array-too-large",
                    "crypto.getRandomValues: array too large (max " + MAX_RANDOM_BYTES + ")");
        }
        byte[] bytes = new byte[n];
        RANDOM.nextBytes(bytes);
        for (int i = 0; i < n; i++) {
            arr.setArrayElement(i, bytes[i] & 0xFF);
        }
        return arr;
    }

    private static String normalizeAlgorithm(Value v) {
        if (v.isString()) return v.asString();
        if (v.hasMembers() && v.hasMember("name")) {
            return v.getMember("name").asString();
        }
        throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "crypto", "invalid-algorithm",
                "invalid algorithm parameter");
    }

    private static byte[] readBytes(Value v) {
        if (v == null || v.isNull()) return new byte[0];
        if (v.isString()) return v.asString().getBytes(StandardCharsets.UTF_8);
        if (v.hasBufferElements()) {
            long n = v.getBufferSize();
            byte[] out = new byte[(int) n];
            for (long i = 0; i < n; i++) out[(int) i] = v.readBufferByte(i);
            return out;
        }
        if (v.hasArrayElements()) {
            long n = v.getArraySize();
            byte[] out = new byte[(int) n];
            for (long i = 0; i < n; i++) out[(int) i] = (byte) v.getArrayElement(i).asInt();
            return out;
        }
        throw new JsHelperException(JsHelperException.Kind.INVALID_INPUT, "crypto", "unsupported-arg-type",
                "readBytes: unsupported argument type");
    }

    private static ProxyArray toUnsignedProxyArray(byte[] bytes) {
        Object[] arr = new Object[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            arr[i] = bytes[i] & 0xFF;
        }
        return ProxyArray.fromArray(arr);
    }
}
