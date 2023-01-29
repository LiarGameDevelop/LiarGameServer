package com.game.liar.security.dto;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class TokenDto {
    private String grantType;
    String accessToken;
    String refreshToken;
    private Long accessTokenExpiresIn;
}
