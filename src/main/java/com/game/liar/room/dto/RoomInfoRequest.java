package com.game.liar.room.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.*;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class RoomInfoRequest {
    @NotNull
    @ApiModelProperty(value = "최대 참여자수(max 6)", notes = "방에 참여할 최대 인원수", example = "5")
    private Integer maxPersonCount;
    @NotNull
    @ApiModelProperty(value = "방장 이름", notes = "방을 생성할 방장 이름", example = "younghee")
    private String ownerName;
    @NotNull
    @ApiModelProperty(value = "비밀번호", notes = "token을 만드는데 필요한 문자열", example = "ebb9084e-a0ab-11ed-a8fc-0242ac120002")
    private String password;
}
