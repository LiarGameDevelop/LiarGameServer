package com.game.liar.room.dto;

import com.game.liar.room.domain.RoomId;
import com.game.liar.room.domain.UserId;
import com.game.liar.security.dto.TokenDto;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
@AllArgsConstructor
@ToString
public class RoomIdUserIdRequest{
    @NotBlank
    @ApiModelProperty(name = "룸 아이디",notes = "룸 아이디",example = "f2d89b44-a0ab-11ed-a8fc-0242ac120002")
    private String roomId;

    @NotBlank
    @ApiModelProperty(name = "유저 아이디",example = "fc14c428-b565-4aad-973d-49a896e2515f")
    private String userId;
}
