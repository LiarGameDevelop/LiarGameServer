package com.game.liar.game.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.game.liar.game.domain.GameSettings;
import com.game.liar.game.dto.MessageBase;
import lombok.*;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonDeserialize
@Builder
@ToString
public class GameSettingsResponse extends MessageBase {
    private Integer round;
    private Integer turn;
    private List<String> category;

    public GameSettings toEntity() {
        return GameSettings.builder()
                .round(round)
                .turn(turn)
                .category(category)
                .build();
    }
}
