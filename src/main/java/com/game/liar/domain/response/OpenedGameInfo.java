package com.game.liar.domain.response;

import com.game.liar.domain.GameState;
import lombok.*;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class OpenedGameInfo {
    String category;
    String keyword;
    List<String> turnOrder;
    GameState state;
}
