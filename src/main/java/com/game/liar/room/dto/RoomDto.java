package com.game.liar.room.dto;

import com.game.liar.game.domain.RoomSettings;
import com.game.liar.room.domain.Room;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class RoomDto {
    @ApiModelProperty(example="7ba35758-a377-4962-a1fa-81f04fcfc970")
    private String roomId;
    @ApiModelProperty(example = "05dec89b-7a3a-45b5-9c51-eca2a27bf604")
    private String ownerId;
    @ApiModelProperty(notes="게임 세팅관련 정보")
    private RoomSettings settings;

    public RoomDto(Room room){
        this.settings = room.getSettings();
        this.ownerId = room.getOwnerId().getUserId();
        this.roomId = room.getId().getId();
    }
}
