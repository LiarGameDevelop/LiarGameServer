package com.game.liar.dto.response;

import com.game.liar.domain.GameState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class OpenLiarResponse {
    String liar;
    GameState state;
    boolean matchLiar;
}
