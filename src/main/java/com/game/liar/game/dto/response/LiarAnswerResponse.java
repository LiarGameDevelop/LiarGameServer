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
public class LiarAnswerResponse extends MessageBase {
    GameState state;
    boolean answer;
    String keyword;
}
