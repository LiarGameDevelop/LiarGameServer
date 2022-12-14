package com.game.liar.domain.response;

import com.game.liar.domain.GameState;
import lombok.*;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder(builderMethodName = "MessageResponseBuilder")
public class MessageResponse {
    @NotBlank
    private String senderId;

    @NotBlank
    private MessageResponse.Message message;

    @NotBlank
    private GameState state;

    @Getter
    @Setter
    @ToString
    @AllArgsConstructor
    public static class Message {
        @NotBlank
        String uuid;
        @NotBlank
        String method;
        @NotBlank
        String body;
    }
}
