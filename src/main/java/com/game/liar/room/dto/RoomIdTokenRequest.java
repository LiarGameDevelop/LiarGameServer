package com.game.liar.room.dto;

import com.game.liar.room.domain.RoomId;
import com.game.liar.security.dto.TokenRequestDto;
import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@ToString
public class RoomIdTokenRequest {
    @NotBlank
    private final String roomId;

    @NotNull
    private final TokenRequestDto token;

    public RoomIdTokenRequest(String roomId, TokenRequestDto token) {
        this.roomId = roomId;
        this.token = token;
    }
}
