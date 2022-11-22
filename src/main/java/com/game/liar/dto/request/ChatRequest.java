package com.game.liar.dto.request;

import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ChatRequest {
    @NotNull
    private String senderId;

    @NotNull
    private String roomId;

    @NotBlank
    private String message;
}
