package com.game.liar.dto.response;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.game.liar.domain.User;
import com.game.liar.domain.Room;
import com.game.liar.dto.MessageBody;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RoomInfoResponseDto{
    private int maxPersonCount;
    private String ownerId;
    private String roomId;
    private String roomName;

    private int personCount;

    private List<User> userList;

    public RoomInfoResponseDto(Room room) {
        maxPersonCount = room.getMaxCount();
        ownerId = room.getOwnerId();
        roomId = room.getRoomId();
        roomName = room.getOwnerName();
        personCount = room.getUserList().size();
        userList = room.getUserList();
    }
}
