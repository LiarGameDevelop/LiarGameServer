package com.game.liar.dto.response;

import com.game.liar.domain.GameState;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class TurnResponse {
    String turnId;
    GameState state;
}
