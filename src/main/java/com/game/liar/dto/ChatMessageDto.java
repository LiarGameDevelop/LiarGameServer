package com.game.liar.dto;

import com.game.liar.domain.Global;
import com.game.liar.domain.ChatMessage;
import lombok.*;

import javax.validation.constraints.NotBlank;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class ChatMessageDto {
    @NotBlank
    private String senderId;

    @NotBlank
    private String message;

    @NotBlank
    private Global.MessageType type;

    public ChatMessage toEntity(String roomId){
        return ChatMessage.builder()
                .message(message)
                .senderId(senderId)
                .type(type)
                .roomId(roomId)
                .build();
    }
}
