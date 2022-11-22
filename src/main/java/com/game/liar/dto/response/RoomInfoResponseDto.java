package com.game.liar.dto.response;

import com.game.liar.dto.Room;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RoomInfoResponseDto {
    private int maxPersonCount;
    private String ownerId;
    private String roomId;
    private String roomName;

    private Integer personCount;

    public RoomInfoResponseDto(Room room){
        maxPersonCount = room.getMaxCount();
        ownerId = room.getOwnerId();
        roomId = room.getRoomId();
        roomName = room.getRoomName();
        personCount = room.getMemberList().size();
    }
}
