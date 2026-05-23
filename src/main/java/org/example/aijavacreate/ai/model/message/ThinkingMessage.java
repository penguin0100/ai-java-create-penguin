package org.example.aijavacreate.ai.model.message;

import dev.langchain4j.model.chat.response.PartialThinking;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 深度思考消息（reasoning_content）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ThinkingMessage extends StreamMessage {
    private String data;

    public ThinkingMessage(PartialThinking partialThinking) {
        super(StreamMessageTypeEnum.THINKING.getValue());
        this.data = partialThinking == null ? null : partialThinking.text();
    }
}
