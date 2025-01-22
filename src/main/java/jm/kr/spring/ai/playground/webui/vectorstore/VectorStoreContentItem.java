package jm.kr.spring.ai.playground.webui.vectorstore;

import javax.validation.constraints.NotNull;

public record VectorStoreContentItem(Double score, @NotNull String id, @NotNull String text, String media,
                                     String metadata, String embedding) {}