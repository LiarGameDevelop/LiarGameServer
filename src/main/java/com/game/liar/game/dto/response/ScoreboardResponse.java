package com.game.liar.game.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.game.liar.game.dto.MessageBase;
import lombok.*;

import java.util.Map;


@NoArgsConstructor
@Getter
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@ToString
@JsonDeserialize
@Builder
public class ScoreboardResponse extends MessageBase {
    Map<String,Integer> scoreboard;

}
