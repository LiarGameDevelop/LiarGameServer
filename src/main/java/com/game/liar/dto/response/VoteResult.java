package com.game.liar.dto.response;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.game.liar.dto.MessageBody;
import lombok.*;

import java.util.List;
import java.util.Map;

@NoArgsConstructor
@Getter
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@ToString
@JsonDeserialize
@Builder
public class VoteResult extends MessageBody {
    Map<String,String> voteResult;
    List<Map.Entry<String,Long>> mostVoted;
}
