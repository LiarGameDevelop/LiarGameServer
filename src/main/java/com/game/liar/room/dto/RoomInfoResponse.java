package com.game.liar.room.dto;

import com.game.liar.room.domain.Room;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;

import javax.validation.constraints.NotNull;
import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class RoomInfoResponse {
    @ApiModelProperty(notes="방 정보")
    private RoomDto room;
    @ApiModelProperty(notes="방에 속한 유저 정보")
    private List<UserDataDto> users;
}
