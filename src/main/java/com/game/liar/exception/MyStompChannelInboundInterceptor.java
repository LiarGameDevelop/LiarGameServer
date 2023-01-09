package com.game.liar.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class MyStompChannelInboundInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.info("StompChannelOutboundInterceptor preSend error detected");

            MessageHeaders headers = message.getHeaders();
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
            MultiValueMap<String, String> map = headers.get(StompHeaderAccessor.NATIVE_HEADERS, MultiValueMap.class);
            log.info("StompChannelOutboundInterceptor preSend headers :{}", map);
            accessor.setSessionId(accessor.getSessionId());
            accessor.setSubscriptionId(accessor.getSubscriptionId());
            channel.send(MessageBuilder.createMessage(new byte[0], headerAccessor.getMessageHeaders()));
            //clientOutboundChannel.send(MessageBuilder.createMessage(new byte[0], headerAccessor.getMessageHeaders()));
            return message;
        }
        return ChannelInterceptor.super.preSend(message, channel);
    }
}
