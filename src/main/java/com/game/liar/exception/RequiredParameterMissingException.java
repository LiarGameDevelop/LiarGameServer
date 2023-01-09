package com.game.liar.exception;

public class RequiredParameterMissingException extends LiarGameException{
    public RequiredParameterMissingException(String message) {
        super(message, "Required Parameter Missing");
    }
}
