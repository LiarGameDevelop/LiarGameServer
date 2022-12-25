package com.game.liar.dto.response;

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
