package com.game.liar.room.dto;

import com.game.liar.room.domain.Room;
import lombok.*;

import javax.validation.constraints.NotNull;
import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class RoomInfoResponse {
    private RoomDto room;
    private List<UserDataDto> users;
}
