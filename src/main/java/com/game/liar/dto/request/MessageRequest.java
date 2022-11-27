package com.game.liar.dto.request;

import com.game.liar.dto.GameState;
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
    private MessageDetail message;

    @Getter
    @Setter
    @ToString
    @AllArgsConstructor
    public class MessageDetail{
        String method;
        String detail;
    }
}
