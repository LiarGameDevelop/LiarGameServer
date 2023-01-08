package com.game.liar.exception;

import lombok.Getter;

public class AlreadyExistException extends LiarGameException {
    public AlreadyExistException(String message) {
        super(message,"Already Exist");
    }
}
