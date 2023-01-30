package com.game.liar.room.dto;

import com.game.liar.room.domain.RoomId;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@AllArgsConstructor
@ToString
public class RoomIdUserNameRequest {
    @NotBlank
    @ApiModelProperty(value = "룸 아이디",notes="참여할 방 아이디",example="f2d89b44-a0ab-11ed-a8fc-0242ac120002")
    private String roomId;

    @NotBlank
    @ApiModelProperty(value = "유저 이름",notes="사용자에게 보여주는 유저 이름",example = "chulsoo")
    private String username;

    @NotNull
    @ApiModelProperty(value = "비밀번호", notes = "token을 만드는데 필요한 문자열",example = "ebb9084e-a0ab-11ed-a8fc-0242ac120002")
    private String password;
}
