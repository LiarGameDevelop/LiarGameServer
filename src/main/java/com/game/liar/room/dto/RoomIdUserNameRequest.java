package com.game.liar.room.dto;

import com.game.liar.room.domain.RoomId;
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
    private String roomId;

    @NotBlank
    private String username;

    @NotNull
    private String password;
}
