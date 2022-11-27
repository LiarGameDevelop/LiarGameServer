package com.game.liar.dto.response;

import com.game.liar.dto.GameState;
import lombok.*;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class MessageResponse {
    @NotBlank
    private String senderId;

    @NotBlank
    private MessageDetail message;

    @NotBlank
    private GameState status;

    //TODO: do I need to distinguish which response handles which request?

    @Getter
    @Setter
    @ToString
    @AllArgsConstructor
    public static class MessageDetail{
        String detail;
    }
}
