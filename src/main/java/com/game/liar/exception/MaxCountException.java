package com.game.liar.exception;

import lombok.Getter;

public class MaxCountException extends LiarGameException {

    public MaxCountException(String message) {
        super(message,"Max count");
    }
}
