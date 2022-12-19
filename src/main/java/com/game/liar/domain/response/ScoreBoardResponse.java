package com.game.liar.domain.response;

import lombok.*;

import java.util.Map;
import java.util.Objects;


@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@EqualsAndHashCode
public class ScoreBoardResponse{
    Map<String,Integer> scoreBoard;
}
