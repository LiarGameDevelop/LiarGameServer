package com.game.liar.room.dto;

import com.game.liar.room.domain.Room;
import com.game.liar.security.dto.TokenDto;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
public class EnterRoomResponse {
    private RoomDto room;
    private UserDto user;
    private TokenDto token;
}

