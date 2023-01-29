package com.game.liar.game.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.game.liar.game.dto.MessageBody;
import com.game.liar.game.domain.GameInfo;
import lombok.*;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonDeserialize
@Builder
@ToString
public class GameSettingsRequest extends MessageBody {
    private Integer round;
    private Integer turn;
    private List<String> category;

    public GameInfo.GameSettings toEntity() {
        return GameInfo.GameSettings.builder()
                .round(round)
                .turn(turn)
                .category(category)
                .build();
    }
}
