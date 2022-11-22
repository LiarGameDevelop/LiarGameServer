package com.game.liar.controller;

import com.game.liar.dto.request.RoomIdRequest;
import com.game.liar.dto.request.RoomInfoRequest;
import com.game.liar.dto.response.RoomInfoResponseDto;
import com.game.liar.exception.MaxCountException;
import com.game.liar.service.RoomService;
import org.springframework.http.HttpRequest;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
public class RoomController {
    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    private RoomService roomService;
    //해야할 역할은? 클라가 만들고 싶은 방의 정보를 받아서->방 만들기

    @PostMapping("/room")
    public RoomInfoResponseDto create(@Valid @RequestParam RoomInfoRequest request, HttpRequest httpRequest) throws MaxCountException {
        return roomService.create(request);
    }

    @GetMapping("/room")
    public RoomInfoResponseDto lookup(@Valid @RequestParam RoomIdRequest request,HttpRequest httpRequest) {
        return roomService.getRoom(request);
    }

    @PostMapping("/member")
    public RoomInfoResponseDto addMember(@Valid @RequestParam RoomIdRequest request,HttpRequest httpRequest) throws MaxCountException {
        return roomService.addRoom(request);
    }

    @DeleteMapping("/room")
    public RoomInfoResponseDto remove(@Valid @RequestParam RoomIdRequest request,HttpRequest httpRequest) {
        return roomService.deleteRoom(request);
    }
}
