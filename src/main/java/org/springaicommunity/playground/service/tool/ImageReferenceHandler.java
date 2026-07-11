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
package org.springaicommunity.playground.service.tool;

import java.util.function.BooleanSupplier;

public interface ImageReferenceHandler extends PendingInteraction {

    String TOOL_CONTEXT_KEY = "imageReferenceHandler";

    Resolved resolve(Request request, BooleanSupplier interactionGate);

    record Request(String ref, String question) {}

    record Resolved(boolean resolved, byte[] bytes, String mimeType, String description, String note,
                    boolean userCancelled) {

        public static Resolved none(String note) {
            return new Resolved(false, null, null, null, note, false);
        }

        public static Resolved cancelled(String note) {
            return new Resolved(false, null, null, null, note, true);
        }

        public static Resolved of(byte[] bytes, String mimeType, String description) {
            return new Resolved(true, bytes, mimeType, description, null, false);
        }
    }
}
