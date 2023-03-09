package com.game.liar.security.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class TokenDto {
    @ApiModelProperty(value="Authorization 인증",notes = "Authorization 인증타입",example = "Bearer")
    private String grantType;
    @ApiModelProperty(value="access 토큰",notes = "Authorization에 사용되는 토큰",
    example = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJiZmE2ZDc4My01MDFhLTQ2NWYtOTZkMC02NmYwYjBkN2NlMDVmYzE0YzQyOC1iNTY1LTRhYWQtOTczZC00OWE4OTZlMjUxNWYiLCJhdXRoIjoiUk9MRV9VU0VSIiwiZXhwIjoxNjc1MDAyMzc4fQ.rzddZApcFKVAdW3sIqNvcbFEf9xfR_ufZgH8UIbjpBRgLnqTNlCYHIfBqKC1306HVw1rTgwguRRrihcJ3w8u2A")
    String accessToken;
    @ApiModelProperty(value="refresh 토큰",notes = "토큰을 재발행하기 위한 토큰. 현재 미사용",
    example = "eyJhbGciOiJIUzUxMiJ9.eyJleHAiOjE2NzUwMDIzOTN9.pQRoblyv8N1EEELJxQzbtKEkS0nE6AO6y8TZPwao5nuvbwYG4Ct81FSihK_rXMEQqlk5ZgHhZbiPJdc5oUHF-g")
    String refreshToken;
    @ApiModelProperty(value="토큰 만료 시각",notes="토큰이 만료되는 시각")
    private Long accessTokenExpiresIn;
}
