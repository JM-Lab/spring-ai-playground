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
package org.springaicommunity.playground.service.mcp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpConnectionParametersWithExtrasTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void streamableLegacyJsonWithoutExtrasDeserializesWithEmptyDefaults() throws Exception {
        String legacy = """
                {"url": "http://127.0.0.1:8282", "endpoint": "/mcp"}""";
        var parsed = objectMapper.readValue(legacy, HttpConnectionParametersWithExtras.StreamableHttp.class);
        assertEquals("http://127.0.0.1:8282", parsed.url());
        assertEquals("/mcp", parsed.endpoint());
        assertNotNull(parsed.headers());
        assertTrue(parsed.headers().isEmpty());
        assertNotNull(parsed.requiredEnv());
        assertTrue(parsed.requiredEnv().isEmpty());
    }

    @Test
    void streamableFullJsonCarriesHeadersAndRequiredEnv() throws Exception {
        String full = """
                {
                  "url": "https://api.example.com",
                  "endpoint": "/mcp",
                  "headers": {"Authorization": "Bearer ${TOKEN}", "X-T": "1"},
                  "requiredEnv": ["TOKEN"]
                }""";
        var parsed = objectMapper.readValue(full, HttpConnectionParametersWithExtras.StreamableHttp.class);
        assertEquals(2, parsed.headers().size());
        assertEquals("Bearer ${TOKEN}", parsed.headers().get("Authorization"));
        assertEquals("1", parsed.headers().get("X-T"));
        assertEquals(1, parsed.requiredEnv().size());
        assertEquals("TOKEN", parsed.requiredEnv().get(0));
    }

    @Test
    void streamableUnknownFieldIsIgnored() throws Exception {
        String json = """
                {"url": "http://x", "endpoint": "/y", "future": {"foo": 1}}""";
        var parsed = objectMapper.readValue(json, HttpConnectionParametersWithExtras.StreamableHttp.class);
        assertEquals("http://x", parsed.url());
        assertEquals("/y", parsed.endpoint());
    }

    @Test
    void streamableMalformedJsonThrows() {
        String bad = "{\"url\": ";
        assertThrows(Exception.class,
                () -> objectMapper.readValue(bad, HttpConnectionParametersWithExtras.StreamableHttp.class));
    }

    @Test
    void sseLegacyJsonWithDashEndpointDeserializes() throws Exception {
        String legacy = """
                {"url": "http://127.0.0.1:8282", "sse-endpoint": "/sse"}""";
        var parsed = objectMapper.readValue(legacy, HttpConnectionParametersWithExtras.Sse.class);
        assertEquals("http://127.0.0.1:8282", parsed.url());
        assertEquals("/sse", parsed.sseEndpoint());
        assertTrue(parsed.headers().isEmpty());
        assertTrue(parsed.requiredEnv().isEmpty());
    }

    @Test
    void sseMissingSseEndpointIsNull() throws Exception {
        String json = """
                {"url": "http://x"}""";
        var parsed = objectMapper.readValue(json, HttpConnectionParametersWithExtras.Sse.class);
        assertNull(parsed.sseEndpoint());
    }

    @Test
    void sseFullJsonCarriesHeadersAndRequiredEnv() throws Exception {
        String full = """
                {
                  "url": "http://x",
                  "sse-endpoint": "/events",
                  "headers": {"X-API-Key": "${API_KEY}"},
                  "requiredEnv": ["API_KEY"]
                }""";
        var parsed = objectMapper.readValue(full, HttpConnectionParametersWithExtras.Sse.class);
        assertEquals("/events", parsed.sseEndpoint());
        assertEquals("${API_KEY}", parsed.headers().get("X-API-Key"));
        assertEquals(1, parsed.requiredEnv().size());
    }

    @Test
    void streamableRecordCopyIsDefensive() throws Exception {
        String full = """
                {"url":"http://x","endpoint":"/y","headers":{"k":"v"},"requiredEnv":["A"]}""";
        var parsed = objectMapper.readValue(full, HttpConnectionParametersWithExtras.StreamableHttp.class);
        assertThrows(UnsupportedOperationException.class, () -> parsed.requiredEnv().add("Z"));
    }

    @Test
    void streamableLegacyJsonHasNullOAuth() throws Exception {
        String legacy = """
                {"url":"http://x","endpoint":"/y"}""";
        var parsed = objectMapper.readValue(legacy, HttpConnectionParametersWithExtras.StreamableHttp.class);
        assertNull(parsed.oauth());
    }

    @Test
    void streamableJsonWithOAuthCarriesAllFields() throws Exception {
        String json = """
                {
                  "url": "https://api.notion.com/mcp",
                  "endpoint": "/mcp",
                  "oauth": {
                    "issuer-uri": "https://api.notion.com",
                    "client-id": "client-abc",
                    "client-secret": "${NOTION_SECRET}",
                    "scopes": ["read","write"],
                    "client-authentication-method": "client_secret_basic"
                  }
                }""";
        var parsed = objectMapper.readValue(json, HttpConnectionParametersWithExtras.StreamableHttp.class);
        var oauth = parsed.oauth();
        assertNotNull(oauth);
        assertEquals("https://api.notion.com", oauth.issuerUri());
        assertEquals("client-abc", oauth.clientId());
        assertEquals("${NOTION_SECRET}", oauth.clientSecret());
        assertEquals(2, oauth.scopes().size());
        assertEquals("client_secret_basic", oauth.clientAuthenticationMethod());
    }

    @Test
    void sseJsonWithOAuthCarriesAllFields() throws Exception {
        String json = """
                {
                  "url": "https://api.linear.app/sse",
                  "sse-endpoint": "/events",
                  "oauth": {
                    "authorization-uri": "https://linear.app/oauth/authorize",
                    "token-uri": "https://api.linear.app/oauth/token",
                    "client-id": "linear-client",
                    "scopes": ["read"]
                  }
                }""";
        var parsed = objectMapper.readValue(json, HttpConnectionParametersWithExtras.Sse.class);
        var oauth = parsed.oauth();
        assertNotNull(oauth);
        assertEquals("https://linear.app/oauth/authorize", oauth.authorizationUri());
        assertEquals("https://api.linear.app/oauth/token", oauth.tokenUri());
        assertEquals("linear-client", oauth.clientId());
        assertNull(oauth.clientSecret());
        assertEquals(1, oauth.scopes().size());
    }

    @Test
    void oauthScopesListIsImmutable() throws Exception {
        String json = """
                {"url":"http://x","oauth":{"client-id":"c","authorization-uri":"https://a","token-uri":"https://t","scopes":["read"]}}""";
        var parsed = objectMapper.readValue(json, HttpConnectionParametersWithExtras.StreamableHttp.class);
        assertThrows(UnsupportedOperationException.class, () -> parsed.oauth().scopes().add("write"));
    }
}
