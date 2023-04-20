package com.game.liar.game.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.game.liar.game.dto.MessageBase;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@Getter
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@ToString
@JsonDeserialize
public class RankingsResponse extends MessageBase {
    private List<RankingInfo> rankings = new ArrayList<>();

    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @Setter
    @EqualsAndHashCode
    @ToString
    public static class RankingInfo {
        private String id;
        private Integer score;
    }
}
