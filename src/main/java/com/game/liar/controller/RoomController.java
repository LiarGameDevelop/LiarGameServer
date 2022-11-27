package com.game.liar.controller;

import com.game.liar.dto.request.RoomIdAndSenderIdRequest;
import com.game.liar.dto.request.RoomIdRequest;
import com.game.liar.dto.request.RoomInfoRequest;
import com.game.liar.dto.response.RoomInfoResponseDto;
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
    //해야할 역할은? 클라가 만들고 싶은 방의 정보를 받아서->방 만들기

    @PostMapping("/room")
    public RoomInfoResponseDto create(@Valid @RequestBody RoomInfoRequest request, HttpServletRequest httpRequest) throws MaxCountException {
        log.info("request :" + request + ", ip :" + getClientIp(httpRequest));
        RoomInfoResponseDto response = roomService.create(request);
        if (response != null) {
            gameController.initialize(request.getRoomName());
        }
        return response;
    }

    @GetMapping("/room")
    public RoomInfoResponseDto lookup(@Valid @RequestParam("roomId") String roomId, HttpServletRequest httpRequest) {
        log.info("request :" + roomId + ", ip :" + getClientIp(httpRequest));
        return roomService.getRoom(new RoomIdRequest(roomId));
    }

    @PostMapping("/room/enter")
    public RoomInfoResponseDto enterRoom(@Valid @RequestBody RoomIdAndSenderIdRequest request, HttpServletRequest httpRequest) throws MaxCountException {
        log.info("request :" + request + ", ip :" + getClientIp(httpRequest));
        return roomService.addRoomMember(request);
    }

    @PostMapping("/room/leave")
    public RoomInfoResponseDto leaveRoom(@Valid @RequestBody RoomIdAndSenderIdRequest request, HttpServletRequest httpRequest) {
        log.info("request :" + request + ", ip :" + getClientIp(httpRequest));
        return roomService.leaveRoomMember(request);
    }

    @DeleteMapping("/room")
    public RoomInfoResponseDto remove(@Valid @RequestBody RoomIdAndSenderIdRequest request, HttpServletRequest httpRequest) {
        log.info("request :" + request + ", ip :" + getClientIp(httpRequest));
        RoomInfoResponseDto response = roomService.deleteRoom(request);
        if (response != null) {
            gameController.close(request.getRoomId());
        }
        return response;
    }

    public static String getClientIp(HttpServletRequest request) {
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

        if (!isIpInHeader) {
            clientIp = request.getRemoteAddr();
        }

        return clientIp;
    }
}
