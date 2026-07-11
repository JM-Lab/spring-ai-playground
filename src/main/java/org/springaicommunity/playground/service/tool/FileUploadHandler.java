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

public interface FileUploadHandler extends PendingInteraction {

    String TOOL_CONTEXT_KEY = "fileUploadHandler";

    Result requestUpload(Request request);

    record Request(String prompt, String accept) {}

    record Result(boolean uploaded, String path, String fileName, String mediaType, long bytes, String note) {

        public static Result none(String note) {
            return new Result(false, null, null, null, 0L, note);
        }

        public static Result of(String path, String fileName, String mediaType, long bytes) {
            return new Result(true, path, fileName, mediaType, bytes, null);
        }
    }
}
