package com.game.liar.domain.response;

import com.game.liar.domain.GameState;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class GameStateResponse {
    GameState state;
}
