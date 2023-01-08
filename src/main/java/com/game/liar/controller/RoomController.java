package com.game.liar.controller;

import com.game.liar.domain.User;
import com.game.liar.dto.request.RoomIdRequest;
import com.game.liar.dto.request.RoomIdUserIdRequest;
import com.game.liar.dto.request.RoomIdUserNameRequest;
import com.game.liar.dto.request.RoomInfoRequest;
import com.game.liar.dto.response.RoomEnterInfoResponse;
import com.game.liar.dto.response.RoomInfoResponse;
import com.game.liar.exception.MaxCountException;
import com.game.liar.service.RoomService;
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

    private RoomService roomService;

    public RoomController(RoomService roomService, GameController gameController) {
        this.roomService = roomService;
        this.gameController = gameController;
    }

    private GameController gameController;

    @PostMapping("/room")
    public RoomInfoResponse create(@Valid @RequestBody RoomInfoRequest request, HttpServletRequest httpRequest) throws MaxCountException {
        log.info("request :" + request + ", ip :" + getClientIp(httpRequest));
        RoomInfoResponse response = roomService.create(request);
        if (response != null) {
            gameController.addRoom(response.getRoomId(), response.getOwnerId());
            gameController.addMember(response.getRoomId(), response.getUserList().get(response.getUserList().size() - 1));
            log.info("[Create] [room:{}]", response);
        }
        return response;
    }

    @GetMapping("/room")
    public RoomInfoResponse lookup(@Valid @RequestParam("roomId") String roomId, HttpServletRequest httpRequest) {
        log.info("request :" + roomId + ", ip :" + getClientIp(httpRequest));
        return roomService.getRoom(new RoomIdRequest(roomId));
    }

    @PostMapping("/room/enter")
    public RoomEnterInfoResponse enterRoom(@Valid @RequestBody RoomIdUserNameRequest request, HttpServletRequest httpRequest) throws MaxCountException {
        log.info("request :" + request + ", ip :" + getClientIp(httpRequest));
        RoomInfoResponse room = roomService.addRoomMember(request);
        User newUser = room.getUserList().get(room.getUserList().size() - 1);
        gameController.addMember(room.getRoomId(), newUser);
        log.info("[Enter] User:{}, [room:{}]", newUser, room);
        return new RoomEnterInfoResponse(room, newUser);
    }

    @PostMapping("/room/leave")
    public RoomInfoResponse leaveRoom(@Valid @RequestBody RoomIdUserIdRequest request, HttpServletRequest httpRequest) {
        log.info("request :" + request + ", ip :" + getClientIp(httpRequest));
        //User willDeleteUser = roomService.getUsers(new RoomIdRequest(request.getRoomId()))
        RoomInfoResponse room = roomService.leaveRoomMember(request);
        gameController.deleteMember(room.getRoomId(), room.getUser(request.getUserId()));
        return room;
    }

    @DeleteMapping("/room")
    public RoomInfoResponse removeRoom(@Valid @RequestBody RoomIdUserIdRequest request, HttpServletRequest httpRequest) {
        log.info("request :" + request + ", ip :" + getClientIp(httpRequest));
        RoomInfoResponse response = roomService.deleteRoom(request);
        if (response != null) {
            //알리기
            gameController.removeRoom(request.getRoomId());
        }
        return response;
    }

    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        String clientIp = null;
        boolean isIpInHeader = false;

        List<String> headerList = new ArrayList<>();
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

        for (String header : headerList) {
            clientIp = request.getHeader(header);
            if (StringUtils.hasText(clientIp) && !clientIp.equals("unknown")) {
                isIpInHeader = true;
                break;
            }
        }

        return clientIp;
    }
}
