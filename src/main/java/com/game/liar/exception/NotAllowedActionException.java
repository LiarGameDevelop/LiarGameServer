package com.game.liar.exception;

public class NotAllowedActionException extends LiarGameException {

    public NotAllowedActionException(String message) {
        super(message, "Not Allowed Action");
    }
}
