package com.game.liar.common.event;

import com.game.liar.chat.ChatProperties;
//import com.game.liar.repository.ParticipantRepository;
import com.game.liar.repository.ParticipantRepository;
import com.game.liar.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Optional;

//@Slf4j
//@RequiredArgsConstructor
//public class PresenceEventListener {
//
//    private ParticipantRepository participantRepository;
//
//    private SimpMessagingTemplate messagingTemplate;
//
//    private RoomService roomService;
//
//    private String loginDestination;
//
//    private String logoutDestination;
//    @Autowired
//    private ChatProperties chatProperties;
//
//    public PresenceEventListener(ParticipantRepository participantRepository, SimpMessagingTemplate messagingTemplate, RoomService roomService) {
//        this.participantRepository = participantRepository;
//        this.messagingTemplate = messagingTemplate;
//        this.roomService = roomService;
//    }
//
//    public PresenceEventListener(SimpMessagingTemplate messagingTemplate, ParticipantRepository participantRepository) {
//        this.messagingTemplate = messagingTemplate;
//        this.participantRepository = participantRepository;
//    }
//
//    @EventListener
//    public void handleSessionConnected(SessionConnectEvent event) {
//        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
//        log.info("header :{}",headers);
//        LoginEvent loginEvent = new LoginEvent(headers.getSessionId());
//        loginDestination = chatProperties.getDestinations().getLogin();
//        logoutDestination = chatProperties.getDestinations().getLogout();
//        log.info("STOMP client connected. login : {} , logout: {}", loginDestination, logoutDestination);
//        messagingTemplate.convertAndSend(loginDestination, loginEvent);
//        //participantRepository.add(headers.getSessionId(), loginEvent);
//    }
//
//    @EventListener
//    public void handleSessionDisconnect(SessionDisconnectEvent event) {
//        log.info("STOMP client session disconnected. login : {} , logout: {}", loginDestination, logoutDestination);
//        Optional.ofNullable(participantRepository.getParticipant(event.getSessionId()))
//                .ifPresent(login -> {
//                    messagingTemplate.convertAndSend(logoutDestination, new LogoutEvent(login.getUsername()));
//                    participantRepository.removeParticipant(event.getSessionId());
//                });
//    }
//
//    public void setLoginDestination(String loginDestination) {
//        this.loginDestination = loginDestination;
//    }
//
//    public void setLogoutDestination(String logoutDestination) {
//        this.logoutDestination = logoutDestination;
//    }
//}
