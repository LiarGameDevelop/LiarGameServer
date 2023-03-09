package com.game.liar.security;

import javax.security.sasl.AuthenticationException;

public class BadAuthenticationException extends AuthenticationException {
    public BadAuthenticationException(String message) {
        super(message);
    }
}
