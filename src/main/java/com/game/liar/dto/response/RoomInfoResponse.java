package com.game.liar.dto.response;

import com.game.liar.domain.Room;
import com.game.liar.domain.User;
import com.game.liar.exception.NotExistException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Optional;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RoomInfoResponse {
    private int maxPersonCount;
    private String ownerId;
    private String roomId;
    private String roomName;

    private int personCount;

    private List<User> userList;

    public RoomInfoResponse(Room room) {
        maxPersonCount = room.getMaxCount();
        ownerId = room.getOwnerId();
        roomId = room.getRoomId();
        roomName = room.getOwnerName();
        personCount = room.getUserList().size();
        userList = room.getUserList();
    }

    public User getUser(String userId) {
        Optional<User> optional = userList.stream().filter(user -> user.getUserId().equals(userId)).findFirst();
        if (optional.isPresent()) {
            return optional.get();
        }
        throw new NotExistException("There is no user id");
    }
}
