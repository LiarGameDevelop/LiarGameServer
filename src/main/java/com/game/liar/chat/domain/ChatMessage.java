package com.game.liar.chat.domain;

import com.game.liar.game.domain.Global;
import com.game.liar.room.domain.RoomId;
import com.game.liar.user.domain.UserId;
import lombok.*;

import javax.persistence.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Entity
@Builder
public class ChatMessage extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private UserId senderId;

    private String message;

    private Global.MessageType type;

    @Embedded
    private RoomId roomId;
}
