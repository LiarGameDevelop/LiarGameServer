package com.game.liar.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
@AllArgsConstructor
@ToString
public class RoomIdAndSenderIdRequest {
    @NotBlank
    private String roomId;

    private String senderId;
}