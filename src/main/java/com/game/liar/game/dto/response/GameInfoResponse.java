package com.game.liar.game.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.game.liar.game.domain.GameState;
import com.game.liar.game.dto.MessageBase;
import com.game.liar.room.domain.RoomId;
import com.game.liar.user.domain.UserId;
import lombok.*;

@NoArgsConstructor
@Getter
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@ToString
@JsonDeserialize
public class GameInfoResponse extends MessageBase {
    GameState state;
    RoomId roomId;
    UserId ownerId;
    Integer currentTurn;
    Integer currentRound;
    GameSettingsResponse gameSettings;
}
