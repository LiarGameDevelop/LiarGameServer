package com.game.liar.dto.response;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.game.liar.domain.GameState;
import com.game.liar.dto.MessageBody;
import lombok.*;

@NoArgsConstructor
@Getter
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@ToString
@JsonDeserialize
@Builder
public class LiarAnswerResponse extends MessageBody {
    GameState state;
    boolean answer;
    String keyword;
}
