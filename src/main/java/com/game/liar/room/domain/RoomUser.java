package com.game.liar.room.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RoomUser {

    private String roomId;
    private String userId;
    private String username;
    private Authority authority;

//    public static RoomUser of(String roomId, String userId) {
//        return new RoomUser(roomId, userId);
//    }
}
