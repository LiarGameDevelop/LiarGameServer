package com.game.liar.domain.response;

import com.game.liar.domain.GameState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class LiarAnswerResponse{
    boolean answer;
    GameState state;
}
