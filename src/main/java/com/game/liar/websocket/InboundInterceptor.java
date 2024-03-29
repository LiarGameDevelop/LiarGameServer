package com.game.liar.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.exception.NotAllowedActionException;
import com.game.liar.exception.NotExistException;
import com.game.liar.game.domain.Global;
import com.game.liar.game.service.MessageService;
import com.game.liar.security.domain.TokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static com.game.liar.security.util.JwtUtil.getRoomIdFromUUID;
import static com.game.liar.security.util.JwtUtil.getUserIdFromUUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class InboundInterceptor implements ChannelInterceptor {
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer";

    private final TokenProvider tokenProvider;
    private final ApplicationEventPublisher publisher;

    private final MessageService messageService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String token = verifyJwt(accessor);
            Claims claims = tokenProvider.parseClaims(token);
            String subject = claims.getSubject();
            String roomId = getRoomIdFromUUID(subject);
            String userId = getUserIdFromUUID(subject);

            String destination = accessor.getNativeHeader("destination").get(0);
            if (destination.contains("public") || destination.contains("room")) {
                if (!destination.contains(roomId)) {
                    throw new NotAllowedActionException("You can listen only your room");
                }
            } else if (destination.contains("private")) {
                if (!destination.contains(userId)) {
                    throw new NotAllowedActionException("You can listen only your id");
                }
            }
        }

        return ChannelInterceptor.super.preSend(message, channel);
    }

    @Override
    public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            checkTokenAndSendLoginMessage(channel, accessor, "login");
        } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            checkTokenAndSendLoginMessage(channel, accessor, "logout");
        }
        ChannelInterceptor.super.postSend(message, channel, sent);
    }

    private void checkTokenAndSendLoginMessage(MessageChannel channel, StompHeaderAccessor accessor, String login) {
        if (login.equals("login")) {
            String jwt = verifyJwt(accessor);
            Claims claims = tokenProvider.parseClaims(jwt);
            String subject = claims.getSubject();
            String roomId = getRoomIdFromUUID(subject);
            String userId = getUserIdFromUUID(subject);

            try {
                ObjectMapper objectMapper = new ObjectMapper();
                Global.LoginInfo loginInfo = new Global.LoginInfo(roomId, userId, true);
                String msgJson = objectMapper.writeValueAsString(loginInfo);
//                StompHeaderAccessor loginAccessor = StompHeaderAccessor.create(StompCommand.SEND);
//                loginAccessor.setMessage(msgJson);
//                loginAccessor.setSessionId(accessor.getSessionId());
//                loginAccessor.setSessionAttributes(accessor.getSessionAttributes());
//                loginAccessor.setDestination(String.format("/topic/room.%s.%s", roomId, login));
                //channel.send(MessageBuilder.createMessage(msgJson.getBytes(StandardCharsets.UTF_8), loginAccessor.getMessageHeaders()));
                messageService.sendLoginInfoMessage(roomId, loginInfo);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            WebsocketConnectedEvent event = new WebsocketConnectedEvent(this, roomId, userId, accessor.getSessionId());
            publisher.publishEvent(event);
        } else {
            WebsocketDisconnectedEvent event = new WebsocketDisconnectedEvent(this, accessor.getSessionId());
            publisher.publishEvent(event);
        }
    }

    private String verifyJwt(StompHeaderAccessor accessor) {
        String jwt = resolveToken(accessor);
        tokenProvider.validateTokenAndSetAuth(jwt);

        return jwt;
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        if (accessor.getNativeHeader(AUTHORIZATION_HEADER) == null)
            throw new NotExistException("No JWT header");

        String bearerToken = accessor.getNativeHeader(AUTHORIZATION_HEADER).get(0);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(7);
        }
        throw new IllegalArgumentException(String.format("JWT exists, but format error %s", accessor.getNativeHeader(AUTHORIZATION_HEADER)));
    }
}
