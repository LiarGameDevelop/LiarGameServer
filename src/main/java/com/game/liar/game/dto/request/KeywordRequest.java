package com.game.liar.game.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.game.liar.game.dto.MessageBase;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@JsonDeserialize
@ToString
public class KeywordRequest extends MessageBase {
    String keyword;
}
