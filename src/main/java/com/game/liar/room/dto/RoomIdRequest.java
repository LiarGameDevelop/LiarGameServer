package com.game.liar.room.dto;

import com.game.liar.room.domain.RoomId;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;

import javax.validation.constraints.NotBlank;

@Getter
@ToString
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoomIdRequest {
    @NotBlank
    @ApiModelProperty(value = "룸 아이디",notes="룸 아이디",example="f2d89b44-a0ab-11ed-a8fc-0242ac120002")
    private String roomId;
}
