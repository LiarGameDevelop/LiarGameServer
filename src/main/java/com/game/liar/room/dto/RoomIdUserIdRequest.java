package com.game.liar.room.dto;

import com.game.liar.room.domain.RoomId;
import com.game.liar.room.domain.UserId;
import com.game.liar.security.dto.TokenDto;
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
    private String roomId;

    @NotBlank
    private String userId;
}
