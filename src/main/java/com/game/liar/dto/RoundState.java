package com.game.liar.dto;

public enum RoundState {
    SELECT_LIAR,
    OPEN_KEYWORD,
    IN_PROGRESS,
    VOTE,
    OPEN_LIAR,
    END_ROUND;

    public String getStatus(){
        return this.name();
    }
}
