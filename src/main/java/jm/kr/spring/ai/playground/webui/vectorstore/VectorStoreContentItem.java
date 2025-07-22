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
package jm.kr.spring.ai.playground.webui.vectorstore;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.Objects;

public class VectorStoreContentItem {
    private Double score;

    private String id;

    @NotEmpty
    private String text;

    private String media;
    @Pattern(
            regexp = "\\{(?:[^{}]|\\{(?:[^{}]|\\{[^}]*\\})*\\})*\\}",
            message = "Invalid JSON format!"
    )
    private String metadata;

    public VectorStoreContentItem() {}

    public VectorStoreContentItem(Double score, String id, String text, String media,
            String metadata) {
        this.score = score;
        this.id = id;
        this.text = text;
        this.media = media;
        this.metadata = metadata;
    }

    public Double getScore() {
        return score;
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public String getMedia() {
        return media;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setMedia(String media) {
        this.media = media;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VectorStoreContentItem that = (VectorStoreContentItem) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(text, that.text) && Objects.equals(media, that.media);
    }

    @Override
    public int hashCode() {
        return Objects.hash(score, id, text, media, metadata);
    }
}