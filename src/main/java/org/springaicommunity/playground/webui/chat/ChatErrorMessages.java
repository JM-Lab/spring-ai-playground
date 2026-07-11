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

import java.util.Locale;
import java.util.concurrent.TimeoutException;

public final class ChatErrorMessages {

    private ChatErrorMessages() {}

    public static String friendly(Throwable throwable) {
        if (throwable == null) return "Something went wrong.";
        String raw = throwable.getMessage() == null ? "" : throwable.getMessage();
        if (throwable instanceof TimeoutException || hasCause(throwable, TimeoutException.class))
            return "The model stopped responding. Try again, or switch to a faster model.";
        if (raw.contains("Stream processing failed"))
            return "The model stream was interrupted before it finished. Send the message again.";
        if (isVisionUnsupported(raw.toLowerCase(Locale.ROOT)))
            return "This model can't process images. Pick a vision-capable model (for example an OpenAI GPT-5 "
                    + "model, or an Ollama vision model like gemma3 or qwen2.5-vl).";
        return raw.isBlank() ? "Something went wrong." : raw;
    }

    private static boolean isVisionUnsupported(String lower) {
        return lower.contains("image_url is only supported")
                || lower.contains("does not support image")
                || lower.contains("image input")
                || (lower.contains("vision") && lower.contains("not support"));
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable cause = throwable.getCause(); cause != null; cause = cause.getCause())
            if (type.isInstance(cause)) return true;
        return false;
    }
}
