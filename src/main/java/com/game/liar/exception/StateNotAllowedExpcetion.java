package com.game.liar.exception;

public class StateNotAllowedExpcetion extends LiarGameException {

    public StateNotAllowedExpcetion(String message) {
        super(message, "State Not Allowed");
    }
}