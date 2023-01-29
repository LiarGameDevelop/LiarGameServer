package com.game.liar.room.dto;

import com.game.liar.game.domain.RoomSettings;
import com.game.liar.room.domain.Room;
import lombok.*;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class RoomDto {
    private String roomId;
    private String ownerId;
    private RoomSettings settings;

    public RoomDto(Room room){
        this.settings = room.getSettings();
        this.ownerId = room.getOwnerId().getUserId();
        this.roomId = room.getId().getId();
    }
}
