package com.game.liar.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@NoArgsConstructor
@Getter
@Builder
@AllArgsConstructor
public class VoteResult {
    Map<String,String> voteResult;
    List<Map.Entry<String,Long>> mostVoted;
}
