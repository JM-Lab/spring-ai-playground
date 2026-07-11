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
package org.springaicommunity.playground.service.agent;

import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.agent.AgentRoundInterceptor.Interception;
import org.springaicommunity.playground.service.tool.ImageReferenceHandler;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageReferenceInterceptorTest {

    private final ImageReferenceInterceptor interceptor = new ImageReferenceInterceptor();

    @Test
    void testToMediaMessageAttachesResolvedImageAsMedia() {
        byte[] bytes = {1, 2, 3, 4};
        ImageReferenceHandler.Resolved resolved = ImageReferenceHandler.Resolved.of(bytes, "image/png", "beach.png");
        UserMessage message = ImageReferenceInterceptor.toMediaMessage(resolved);

        assertEquals(1, message.getMedia().size());
        assertArrayEquals(bytes, message.getMedia().get(0).getDataAsByteArray());
        assertEquals("image/png", message.getMedia().get(0).getMimeType().toString());
        assertTrue(message.getText().contains("beach.png"));
    }

    @Test
    void testToMediaMessageWithoutDescriptionStillAttaches() {
        ImageReferenceHandler.Resolved resolved = ImageReferenceHandler.Resolved.of(new byte[]{9}, "image/jpeg", null);
        UserMessage message = ImageReferenceInterceptor.toMediaMessage(resolved);
        assertEquals(1, message.getMedia().size());
        assertEquals("image/jpeg", message.getMedia().get(0).getMimeType().toString());
    }

    @Test
    void testImageResultTextForResolvedMentionsAttachment() {
        ImageReferenceHandler.Resolved resolved = ImageReferenceHandler.Resolved.of(new byte[]{1}, "image/png", "x");
        assertTrue(ImageReferenceInterceptor.imageResultText(resolved).contains("attached"));
    }

    @Test
    void testImageResultTextForMissingUsesNote() {
        ImageReferenceHandler.Resolved none = ImageReferenceHandler.Resolved.none("No matching image.");
        assertEquals("No matching image.", ImageReferenceInterceptor.imageResultText(none));
    }

    @Test
    void testParseImageRequestExtractsRefAndQuestion() {
        ImageReferenceHandler.Request request = ImageReferenceInterceptor.parseImageRequest(
                "{\"ref\":\"abc123\",\"question\":\"what is in this photo\"}");
        assertEquals("abc123", request.ref());
        assertEquals("what is in this photo", request.question());
    }

    @Test
    void testParseImageRequestHandlesBlankAndInvalid() {
        assertNull(ImageReferenceInterceptor.parseImageRequest(null).ref());
        assertNull(ImageReferenceInterceptor.parseImageRequest("").ref());
        assertNull(ImageReferenceInterceptor.parseImageRequest("not-json{{{").ref());
    }

    @Test
    void testWithoutHandlerNothingIsClaimed() {
        Map<String, Interception> claims = interceptor.intercept(
                List.of(new ToolCall("1", "function", "describeImage", "{}")), AgentTurn.detached(), Map.of());
        assertTrue(claims.isEmpty());
    }

    @Test
    void testUserCancelledResolutionMarksToolDeclinedForTheTurn() {
        AgentTurn turn = AgentTurn.detached();
        ImageReferenceHandler handler = (request, gate) ->
                ImageReferenceHandler.Resolved.cancelled("The user cancelled the image upload.");
        Map<String, Interception> claims = interceptor.intercept(
                List.of(new ToolCall("1", "function", "describeImage", "{}")), turn,
                Map.of(ImageReferenceHandler.TOOL_CONTEXT_KEY, handler));

        assertTrue(claims.get("1").response().contains("Do NOT call 'describeImage' again"));
        assertTrue(turn.isDeclined("describeImage"));
    }

    @Test
    void testResolvedImageProducesFollowUpMediaMessage() {
        ImageReferenceHandler handler = (request, gate) ->
                ImageReferenceHandler.Resolved.of(new byte[]{5}, "image/png", "cat.png");
        Map<String, Interception> claims = interceptor.intercept(
                List.of(new ToolCall("1", "function", "describeImage", "{}")), AgentTurn.detached(),
                Map.of(ImageReferenceHandler.TOOL_CONTEXT_KEY, handler));

        Interception interception = claims.get("1");
        assertTrue(interception.response().contains("attached"));
        assertEquals(1, interception.followUp().getMedia().size());
    }
}
