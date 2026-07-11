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
package org.springaicommunity.playground.webui.chat;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatErrorMessagesTest {

    @Test
    void testTimeoutGetsRetryHint() {
        assertTrue(ChatErrorMessages.friendly(new TimeoutException()).contains("stopped responding"));
    }

    @Test
    void testTimeoutAsCauseGetsRetryHint() {
        Throwable wrapped = new RuntimeException("stream failed", new TimeoutException());
        assertTrue(ChatErrorMessages.friendly(wrapped).contains("stopped responding"));
    }

    @Test
    void testOpenAiVisionRejectionGetsHint() {
        Throwable t = new RuntimeException("Invalid content type. image_url is only supported by certain models");
        assertTrue(ChatErrorMessages.friendly(t).contains("vision-capable model"));
    }

    @Test
    void testOllamaImageInputErrorGetsHint() {
        Throwable t = new RuntimeException("model is missing data required for image input");
        assertTrue(ChatErrorMessages.friendly(t).contains("vision-capable model"));
    }

    @Test
    void testStreamProcessingFailureGetsRetryHint() {
        Throwable t = new IllegalStateException("Stream processing failed");
        assertTrue(ChatErrorMessages.friendly(t).contains("Send the message again"));
    }

    @Test
    void testGenericErrorPassesThrough() {
        assertEquals("Connection refused", ChatErrorMessages.friendly(new RuntimeException("Connection refused")));
    }

    @Test
    void testNullThrowableGetsFallback() {
        assertEquals("Something went wrong.", ChatErrorMessages.friendly(null));
    }

    @Test
    void testBlankMessageGetsFallback() {
        assertEquals("Something went wrong.", ChatErrorMessages.friendly(new RuntimeException()));
    }
}
