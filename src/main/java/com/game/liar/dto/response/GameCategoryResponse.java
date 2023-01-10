package com.game.liar.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.game.liar.dto.MessageBody;
import lombok.*;

import java.util.List;

@NoArgsConstructor
@Getter
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@ToString
@JsonDeserialize
public class GameCategoryResponse extends MessageBody {
    List<String> category;
}
