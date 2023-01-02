package com.game.liar.dto.response;

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
public class RoomEnterInfoResponseDto {
    private int maxPersonCount;
    private String ownerId;
    private String roomId;
    private String roomName;

    private User user;

    private int personCount;

    private List<User> userList;

    public RoomEnterInfoResponseDto(RoomInfoResponseDto room, User user) {
        this.maxPersonCount = room.getMaxPersonCount();
        this.ownerId = room.getOwnerId();
        this.roomId = room.getRoomId();
        this.roomName = room.getRoomName();
        this.personCount = room.getUserList().size();
        this.userList = room.getUserList();
        this.user = user;
    }

    public User getUser(String userId) {
        Optional<User> optional = userList.stream().filter(user -> user.getUserId().equals(userId)).findFirst();
        if (optional.isPresent()) {
            return optional.get();
        }
        throw new NotExistException("There is no user id");
    }
}
