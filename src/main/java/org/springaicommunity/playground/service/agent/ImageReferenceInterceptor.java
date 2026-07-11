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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.tool.ImageReferenceHandler;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ImageReferenceInterceptor implements AgentRoundInterceptor {

    static final String DESCRIBE_IMAGE_TOOL = "describeImage";

    private static final Logger logger = LoggerFactory.getLogger(ImageReferenceInterceptor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Map<String, Interception> intercept(List<ToolCall> calls, AgentTurn turn,
            Map<String, Object> toolContext) {
        ImageReferenceHandler handler = toolContext == null ? null
                : (ImageReferenceHandler) toolContext.get(ImageReferenceHandler.TOOL_CONTEXT_KEY);
        if (handler == null) return Map.of();
        Map<String, Interception> claims = new LinkedHashMap<>();
        for (ToolCall call : calls) {
            if (!DESCRIBE_IMAGE_TOOL.equals(call.name())) continue;
            ImageReferenceHandler.Resolved resolved;
            try {
                resolved = handler.resolve(parseImageRequest(call.arguments()), turn::tryInteract);
            } catch (RuntimeException e) {
                logger.warn("image-reference.resolve-failed error={}", e.getMessage());
                resolved = ImageReferenceHandler.Resolved.none("The image could not be loaded.");
            }
            if (resolved.resolved()) {
                claims.put(call.id(), new Interception(imageResultText(resolved), toMediaMessage(resolved)));
            } else if (resolved.userCancelled()) {
                turn.markDeclined(call.name());
                claims.put(call.id(),
                        Interception.of(AgentTurnMessages.notCompleted(call.name(), noteOf(resolved))));
            } else {
                claims.put(call.id(), Interception.of(imageResultText(resolved)));
            }
        }
        return claims;
    }

    private static String noteOf(ImageReferenceHandler.Resolved resolved) {
        return resolved.note() == null ? "No image was provided." : resolved.note();
    }

    static ImageReferenceHandler.Request parseImageRequest(String arguments) {
        String ref = null;
        String question = null;
        if (arguments != null && !arguments.isBlank()) {
            try {
                JsonNode node = MAPPER.readTree(arguments);
                if (node.has("ref") && node.get("ref").isTextual()) ref = node.get("ref").asText();
                if (node.has("question") && node.get("question").isTextual())
                    question = node.get("question").asText();
            } catch (RuntimeException ignore) {
            }
        }
        return new ImageReferenceHandler.Request(ref, question);
    }

    static String imageResultText(ImageReferenceHandler.Resolved resolved) {
        if (!resolved.resolved())
            return resolved.note() == null ? "No matching image was available." : resolved.note();
        String suffix = resolved.description() == null || resolved.description().isBlank()
                ? "" : " (" + resolved.description() + ")";
        return "The requested image" + suffix + " is attached to the following message. Analyze it to answer "
                + "the user.";
    }

    static UserMessage toMediaMessage(ImageReferenceHandler.Resolved resolved) {
        Media media = Media.builder().mimeType(MimeType.valueOf(resolved.mimeType()))
                .data(resolved.bytes()).build();
        String text = resolved.description() == null || resolved.description().isBlank()
                ? "Attached image." : "Attached image. " + resolved.description();
        return UserMessage.builder().text(text).media(media).build();
    }
}
