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

import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.tool.FileUploadHandler;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FileUploadInterceptor implements AgentRoundInterceptor {

    static final String REQUEST_FILE_UPLOAD_TOOL = "requestFileUpload";

    private static final Logger logger = LoggerFactory.getLogger(FileUploadInterceptor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MeterRegistry meterRegistry;

    FileUploadInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Map<String, Interception> intercept(List<ToolCall> calls, AgentTurn turn,
            Map<String, Object> toolContext) {
        FileUploadHandler handler = toolContext == null ? null
                : (FileUploadHandler) toolContext.get(FileUploadHandler.TOOL_CONTEXT_KEY);
        if (handler == null) return Map.of();
        Map<String, Interception> claims = new LinkedHashMap<>();
        for (ToolCall call : calls) {
            if (!REQUEST_FILE_UPLOAD_TOOL.equals(call.name())) continue;
            claims.put(call.id(), Interception.of(resolve(call, turn, handler)));
        }
        return claims;
    }

    private String resolve(ToolCall call, AgentTurn turn, FileUploadHandler handler) {
        if (!turn.tryInteract()) {
            logger.info("file-upload.deferred reason=interaction-budget");
            this.meterRegistry.counter("chat.tool.loop", "outcome", "interaction-budget").increment();
            return AgentTurnMessages.interactionBudget(call.name());
        }
        FileUploadHandler.Result result;
        try {
            result = handler.requestUpload(parseUploadRequest(call.arguments()));
        } catch (RuntimeException e) {
            logger.warn("file-upload.request-failed error={}", e.getMessage());
            result = FileUploadHandler.Result.none("The file upload could not be completed.");
        }
        if (result != null && result.uploaded()) return uploadedMessage(result);
        turn.markDeclined(call.name());
        String note = result == null || result.note() == null ? "The user did not upload a file."
                : result.note();
        return AgentTurnMessages.notCompleted(call.name(), note);
    }

    static FileUploadHandler.Request parseUploadRequest(String arguments) {
        String prompt = null;
        String accept = null;
        if (arguments != null && !arguments.isBlank()) {
            try {
                JsonNode node = MAPPER.readTree(arguments);
                if (node.has("prompt") && node.get("prompt").isTextual()) prompt = node.get("prompt").asText();
                if (node.has("accept") && node.get("accept").isTextual()) accept = node.get("accept").asText();
            } catch (RuntimeException ignore) {
            }
        }
        return new FileUploadHandler.Request(prompt, accept);
    }

    static String uploadedMessage(FileUploadHandler.Result result) {
        return "The user uploaded a file. It is saved in the conversation workspace at \"" + result.path()
                + "\" (" + result.mediaType() + ", " + result.bytes() + " bytes). Read its contents with "
                + "readTextFile(\"" + result.path() + "\"). If it is CSV data, pass that text to parseCsv.";
    }
}
