package com.game.liar.room.dto;

import com.game.liar.room.domain.Room;
import com.game.liar.security.dto.TokenDto;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
public class EnterRoomResponse {
    @ApiModelProperty(notes = "방 정보")
    private RoomDto room;
    @ApiModelProperty(notes = "생성된 유저정보")
    private UserDto user;
    @ApiModelProperty(notes = "생성된 토큰정보")
    private TokenDto token;
}

