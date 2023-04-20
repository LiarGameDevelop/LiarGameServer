package com.game.liar.game.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.game.liar.game.dto.MessageBase;
import lombok.*;

import java.util.List;

@NoArgsConstructor
@Getter
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@ToString
@JsonDeserialize
public class TurnOrderResponse extends MessageBase {
    List<String> turnOrder;
}
