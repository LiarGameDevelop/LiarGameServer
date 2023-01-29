package com.game.liar.room.controller;

import com.game.liar.room.domain.RoomId;
import com.game.liar.room.dto.*;
import com.game.liar.exception.MaxCountException;
import com.game.liar.game.controller.GameController;
import com.game.liar.room.service.RoomService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

@RestController
@Slf4j
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService, GameController gameController) {
        this.roomService = roomService;
        this.gameController = gameController;
    }
    private GameController gameController;

    @PostMapping("/room/create")
    public EnterRoomResponse create(@Valid @RequestBody RoomInfoRequest request, HttpServletRequest httpRequest) throws MaxCountException {
        log.info("[create] request :" + request + ", ip :" + getClientIp(httpRequest));
        EnterRoomResponse response = roomService.create(request);
        //TODO: introduce event handler
        if (response != null) {
            gameController.addRoom(response.getRoom().getRoomId(), response.getUser().getUserId());
            gameController.addMember(RoomId.of(response.getRoom().getRoomId()), UserDataDto.toDto(response.getUser()));
            log.info("[Create] [room:{}]", response);
        }
        return response;
    }

    @GetMapping("/room/info")
    public RoomInfoResponse lookup(@Valid @RequestParam(value = "roomId") RoomIdRequest request, HttpServletRequest httpRequest) {
        log.info("[lookup] request :" + request + ", ip :" + getClientIp(httpRequest));
        return roomService.getRoom(request);
    }

    @PostMapping("/room/enter")
    public EnterRoomResponse enterRoom(@Valid @RequestBody RoomIdUserNameRequest request, HttpServletRequest httpRequest) throws MaxCountException {
        log.info("[enterRoom] request :" + request + ", ip :" + getClientIp(httpRequest));
        EnterRoomResponse room = roomService.addRoomMember(request);

        //TODO: event handler로 이동
        gameController.addMember(RoomId.of(room.getRoom().getRoomId()), UserDataDto.toDto(room.getUser()));
        return room;
    }

    @PostMapping("/room/leave")
    public RoomInfoResponse leaveRoom(@Valid @RequestBody RoomIdUserIdRequest request, HttpServletRequest httpRequest) {
        //TODO: 토큰 추가
        log.info("[leaveRoom] request :" + request + ", ip :" + getClientIp(httpRequest));

        RoomInfoResponse room = roomService.leaveRoomMember(request);
        //TODO: event handler에서 처리
        //gameController.deleteMember(RoomId.of(room.getRoom().getRoomId()), null);
        return room;
    }

    @DeleteMapping("/room")
    public void removeRoom(@Valid @RequestBody RoomIdRequest request, HttpServletRequest httpRequest) {
        log.info("[removeRoom] request :" + request + ", ip :" + getClientIp(httpRequest));
        roomService.deleteRoom(request);
        //알리기
        gameController.removeRoom(request.getRoomId());
    }

    public static java.lang.String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        java.lang.String clientIp = null;
        boolean isIpInHeader = false;

        List<java.lang.String> headerList = new ArrayList<>();
        headerList.add("X-Forwarded-For");
        headerList.add("HTTP_CLIENT_IP");
        headerList.add("HTTP_X_FORWARDED_FOR");
        headerList.add("HTTP_X_FORWARDED");
        headerList.add("HTTP_FORWARDED_FOR");
        headerList.add("HTTP_FORWARDED");
        headerList.add("Proxy-Client-IP");
        headerList.add("WL-Proxy-Client-IP");
        headerList.add("HTTP_VIA");
        headerList.add("IPV6_ADR");

        for (java.lang.String header : headerList) {
            clientIp = request.getHeader(header);
            if (StringUtils.hasText(clientIp) && !clientIp.equals("unknown")) {
                isIpInHeader = true;
                break;
            }
        }

        return clientIp;
    }
}
