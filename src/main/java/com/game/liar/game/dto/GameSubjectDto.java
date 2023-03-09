package com.game.liar.game.dto;

import com.game.liar.game.domain.GameSubject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter

@NoArgsConstructor
@ToString
@AllArgsConstructor
public class GameSubjectDto {
    String category;
    String keyword;

    public GameSubject toEntity() {
        return new GameSubject(category, keyword);
    }
}
