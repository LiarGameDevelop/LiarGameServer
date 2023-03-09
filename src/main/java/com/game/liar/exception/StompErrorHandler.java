package com.game.liar.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

@Component
public class StompErrorHandler extends StompSubProtocolErrorHandler {
    public StompErrorHandler() {
        super();
    }

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
        StompExceptionHandler handler = new StompExceptionHandler(ex);
        return handler.handleException(clientMessage, ex);
        //return super.handleClientMessageProcessingError(clientMessage, ex);
    }
}
