package com.game.liar.exception;

import lombok.Getter;
import lombok.ToString;

@ToString
public class LiarGameException extends RuntimeException {
    @Getter
    private String code;

    public LiarGameException(String message, String code) {
        super(message);
        this.code = code;
    }
}
