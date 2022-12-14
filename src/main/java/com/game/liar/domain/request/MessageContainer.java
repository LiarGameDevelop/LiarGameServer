package com.game.liar.domain.request;

import lombok.*;

import javax.validation.constraints.NotBlank;




@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(builderMethodName = "messageContainerBuilder")
@ToString
@EqualsAndHashCode
public class MessageContainer {
    @NotBlank
    private String senderId;

    @NotBlank
    private MessageContainer.Message message;

    @NotBlank
    private String uuid;

    @Getter
    @AllArgsConstructor
    @ToString
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Message {
        @NotBlank
        String method;
        @NotBlank
        String body;
    }

    public static MessageContainerBuilder builder(MessageContainer.Message message) {
        if (message == null) {
            throw new IllegalArgumentException("필수 메세지 누락");
        }
        return messageContainerBuilder().message(message);
    }
}
