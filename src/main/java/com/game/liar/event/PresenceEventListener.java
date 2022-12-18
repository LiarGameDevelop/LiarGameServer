package com.game.liar.event;

import com.game.liar.config.ChatProperties;
import com.game.liar.repository.ParticipantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Objects;
import java.util.Optional;

@Slf4j
public class PresenceEventListener {

    private ParticipantRepository participantRepository;

    private SimpMessagingTemplate messagingTemplate;

    private String loginDestination;

    private String logoutDestination;
    @Autowired
    private ChatProperties chatProperties;

    public PresenceEventListener(SimpMessagingTemplate messagingTemplate, ParticipantRepository participantRepository) {
        this.messagingTemplate = messagingTemplate;
        this.participantRepository = participantRepository;
    }

    @EventListener
    private void handleSessionConnected(SessionConnectEvent event) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());

        LoginEvent loginEvent = new LoginEvent(headers.getSessionId());
        loginDestination = chatProperties.getDestinations().getLogin();
        logoutDestination = chatProperties.getDestinations().getLogout();
        log.info("STOMP client connected. login : {} , logout: {}", loginDestination, logoutDestination);
        messagingTemplate.convertAndSend(loginDestination, loginEvent);
        participantRepository.add(headers.getSessionId(), loginEvent);
    }

    @EventListener
    private void handleSessionDisconnect(SessionDisconnectEvent event) {
        Optional.ofNullable(participantRepository.getParticipant(event.getSessionId()))
                .ifPresent(login -> {
                    messagingTemplate.convertAndSend(logoutDestination, new LogoutEvent(login.getUsername()));
                    participantRepository.removeParticipant(event.getSessionId());
                });
    }

    public void setLoginDestination(String loginDestination) {
        this.loginDestination = loginDestination;
    }

    public void setLogoutDestination(String logoutDestination) {
        this.logoutDestination = logoutDestination;
    }
}
