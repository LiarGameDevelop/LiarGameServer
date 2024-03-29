package com.game.liar.room.controller;

import com.game.liar.exception.StateNotAllowedExpcetion;
import com.game.liar.game.service.GameService;
import com.game.liar.room.domain.RoomId;
import com.game.liar.room.dto.*;
import com.game.liar.room.service.RoomService;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
public class RoomController {
    private final GameService gameService;
    private final RoomService roomService;

    @PostMapping("/room/create")
    @ApiOperation(value = "방 생성", notes = "방을 생성한다")
    public EnterRoomResponse create(@Valid @RequestBody RoomInfoRequest request, HttpServletRequest httpRequest) {
        log.info("[create] request :" + request + ", ip :" + getClientIp(httpRequest));
        EnterRoomResponse response = roomService.create(request);
        //TODO: introduce event handler
        if (response != null) {
            gameService.addGame(response.getRoom().getRoomId(), response.getUser().getUserId());
            log.info("[Create] [room:{}]", response);
        }
        return response;
    }

    @GetMapping("/room/info")
    @ApiOperation(value = "방 조회", notes = "방을 조회한다")
    @ApiImplicitParam(name = "roomId", value = "방 id")
    public RoomInfoResponse lookup(@Valid @RequestParam(value = "roomId") RoomIdRequest request, HttpServletRequest httpRequest) {
        log.info("[lookup] request :" + request + ", ip :" + getClientIp(httpRequest));
        return roomService.getRoom(request);
    }

    @PostMapping("/room/enter")
    @ApiOperation(value = "방 참여", notes = "방에 참여한다")
    public EnterRoomResponse enterRoom(@Valid @RequestBody RoomIdUserNameRequest request, HttpServletRequest httpRequest) {
        log.info("[enterRoom] request :" + request + ", ip :" + getClientIp(httpRequest));
        if (gameService.isGameStarted(RoomId.of(request.getRoomId())))
            throw new StateNotAllowedExpcetion("game already started");
        return roomService.addRoomMember(request);
    }

//    @PostMapping("/room/leave")
//    @ApiOperation(value = "방 나가기", notes = "방을 나간다")
//    public RoomInfoResponse leaveRoom(@Valid @RequestBody RoomIdUserIdRequest request, HttpServletRequest httpRequest) {
//        log.info("[leaveRoom] request :" + request + ", ip :" + getClientIp(httpRequest));
//        return roomService.leaveRoomMember(request);
//    }
//
//    @DeleteMapping("/room")
//    @ApiOperation(value = "방 삭제", notes = "방을 삭제한다")
//    public void removeRoom(@Valid @RequestBody RoomIdRequest request, HttpServletRequest httpRequest) {
//        log.info("[removeRoom] request :" + request + ", ip :" + getClientIp(httpRequest));
//        roomService.deleteRoom(request);
//        //알리기
//        gameController.removeRoom(request.getRoomId());
//    }

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
