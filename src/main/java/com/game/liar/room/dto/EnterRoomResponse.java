package com.game.liar.room.dto;

import com.game.liar.security.dto.TokenDto;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
@EqualsAndHashCode
public class EnterRoomResponse {
    @ApiModelProperty(notes = "방 정보")
    private RoomDto room;
    @ApiModelProperty(notes = "생성된 유저정보")
    private UserDto user;
    @ApiModelProperty(notes="방에 속한 유저 정보")
    private List<UserDataDto> userList;
    @ApiModelProperty(notes = "생성된 토큰정보")
    private TokenDto token;
}

