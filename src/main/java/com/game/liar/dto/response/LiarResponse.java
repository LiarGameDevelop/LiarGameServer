package com.game.liar.dto.response;

import com.game.liar.domain.GameState;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class LiarResponse {
    boolean liar;
    GameState state;
}
