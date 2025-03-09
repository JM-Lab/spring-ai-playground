package jm.kr.spring.ai.playground;

import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "spring.ai.playground")
public record SpringAiPlaygroundOptions(Chat chat) {
    public record Chat(String systemPrompt, List<String> models, DefaultChatOptions chatOptions) {}
}
