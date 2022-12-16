package com.game.liar.domain.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
@AllArgsConstructor
@ToString
public class RoomIdAndUserIdRequest {
    @NotBlank
    private String roomId;

    private String userId;
}
