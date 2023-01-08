package com.game.liar.exception;

import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

@Component
public class ErrorHandler extends StompSubProtocolErrorHandler {
    public ErrorHandler() {
        super();
    }

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
        MyExceptionHandler handler = new MyExceptionHandler(ex);
        handler.handleException(clientMessage, ex);
        return super.handleClientMessageProcessingError(clientMessage, ex);
    }
}
