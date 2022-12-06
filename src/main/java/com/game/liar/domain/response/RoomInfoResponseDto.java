package com.game.liar.domain.response;

import com.game.liar.domain.Room;
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

    private int personCount;

    public RoomInfoResponseDto(Room room){
        maxPersonCount = room.getMaxCount();
        ownerId = room.getOwnerId();
        roomId = room.getRoomId();
        roomName = room.getOwnerName();
        personCount = room.getMemberList().size();
    }
}
