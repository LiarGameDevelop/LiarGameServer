package com.game.liar.dto.request;

import lombok.*;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class MessageRequest {
    @NotBlank
    private String senderId;

    @NotBlank
    private MessageRequest.Message message;

    @NotBlank
    private String uuid;

    @Getter
    @Setter
    @ToString
    @AllArgsConstructor
    public static class Message {
        String method;
        String body;
    }
}
