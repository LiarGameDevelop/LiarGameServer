package com.game.liar.chat.domain;

import com.game.liar.game.domain.Global;
import com.game.liar.chat.domain.ChatMessage;
import com.game.liar.room.domain.RoomId;
import com.game.liar.user.domain.UserId;
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
                .senderId(UserId.of(senderId))
                .type(type)
                .roomId(RoomId.of(roomId))
                .build();
    }
}
