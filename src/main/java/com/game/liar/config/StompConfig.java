package com.game.liar.config;

import com.game.liar.event.PresenceEventListener;
import com.game.liar.exception.MyStompChannelInboundInterceptor;
import com.game.liar.exception.StompErrorHandler;
import com.game.liar.repository.ParticipantRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

//https://tecoble.techcourse.co.kr/post/2021-09-05-web-socket-practice/

@Configuration
@EnableWebSocketMessageBroker
public class StompConfig implements WebSocketMessageBrokerConfigurer {
    private final String ENDPOINT = "/ws-connection";
    private final String CORS_PATTERN = "*";
    private final StompErrorHandler stompErrorHandler;
    private final MyStompChannelInboundInterceptor channelInboundInterceptor;

    public StompConfig(StompErrorHandler stompErrorHandler, MyStompChannelInboundInterceptor channelInboundInterceptor) {
        this.stompErrorHandler = stompErrorHandler;
        this.channelInboundInterceptor = channelInboundInterceptor;
    }

    /**
     * TODO: 다른 메세지브로커 사용할 수도 있음
     * memory 기반 simple message broker
     **/
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/subscribe"); //subscribe가 붙은 클라이언트 전부에 전달
        registry.setApplicationDestinationPrefixes("/publish");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT)
                .setAllowedOriginPatterns(CORS_PATTERN)
                .withSockJS();
        registry.setErrorHandler(stompErrorHandler);
    }

    @Bean
    public PresenceEventListener presenceEventListener(SimpMessagingTemplate messagingTemplate) {
        PresenceEventListener presence = new PresenceEventListener(messagingTemplate, participantRepository());
        return presence;
    }

    @Bean
    @Description("Keeps connected users")
    public ParticipantRepository participantRepository() {
        return new ParticipantRepository();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        //registration.interceptors(channelOutboundInterceptor);
    }
}
