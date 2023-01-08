package com.game.liar.exception;

public class NotExistException extends LiarGameException {

    public NotExistException(String message) {
        super(message, "Not Exist");
    }
}
