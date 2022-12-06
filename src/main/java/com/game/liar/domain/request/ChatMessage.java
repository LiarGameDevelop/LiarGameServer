package com.game.liar.domain.request;

import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class ChatMessage {
    @NotBlank
    private String senderId;

    @NotBlank
    private String message;
}
