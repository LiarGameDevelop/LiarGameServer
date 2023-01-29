package com.game.liar.game.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.game.liar.game.dto.MessageBody;
import lombok.*;

import java.util.List;

@NoArgsConstructor
@Getter
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@ToString
@JsonDeserialize
@Builder
public class OpenedGameInfo extends MessageBody {
    String category;
    String keyword;
    List<String> turnOrder;
}
