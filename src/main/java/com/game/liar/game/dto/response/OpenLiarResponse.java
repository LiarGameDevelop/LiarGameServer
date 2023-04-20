package com.game.liar.game.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.game.liar.game.domain.GameState;
import com.game.liar.game.dto.MessageBase;
import lombok.*;

@NoArgsConstructor
@Getter
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@ToString
@JsonDeserialize
@Builder
public class OpenLiarResponse extends MessageBase {
    String liar;
    boolean matchLiar;
    GameState state;

}
