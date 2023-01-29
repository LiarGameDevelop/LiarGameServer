package com.game.liar.room.dto;

import com.game.liar.room.domain.RoomId;
import lombok.*;

import javax.validation.constraints.NotBlank;

@Getter
@ToString
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoomIdRequest {
    @NotBlank
    private String roomId;
}
