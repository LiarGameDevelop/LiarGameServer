package com.game.liar.security.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class TokenRequestDto {
    String accessToken;
    String refreshToken;
}
