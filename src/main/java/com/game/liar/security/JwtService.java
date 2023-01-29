package com.game.liar.security;

import com.game.liar.room.domain.Room;
import com.game.liar.room.dto.UserDto;
import com.game.liar.security.domain.TokenProvider;
import com.game.liar.security.dto.TokenDto;
import com.game.liar.security.dto.TokenRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtService {
    private final TokenProvider tokenProvider;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    private TokenDto generateToken(Room room, UserDto user) {
        UsernamePasswordAuthenticationToken authenticationToken = user.toAuthentication(room.getId());
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        return tokenProvider.generateTokenDto(authentication);
    }

    private TokenDto reissue(TokenRequestDto tokenRequestDto) {
        if (!tokenProvider.validateToken(tokenRequestDto.getRefreshToken())) {
            throw new RuntimeException("Refresh Token 이 유효하지 않습니다.");
        }

        Authentication authentication = tokenProvider.getAuthentication(tokenRequestDto.getAccessToken());

        // 저장소에서 Member ID 를 기반으로 Refresh Token 값 가져옴
        //RefreshToken refreshToken = refreshTokenRepository.findByKey(authentication.getName())
        //        .orElseThrow(() -> new RuntimeException("로그아웃 된 사용자입니다."));

        // Refresh Token 일치하는지 검사
        //if (!refreshToken.getValue().equals(tokenRequestDto.getRefreshToken())) {
        //    throw new RuntimeException("토큰의 유저 정보가 일치하지 않습니다.");
        //}

        // 새로운 토큰 생성
        TokenDto tokenDto = tokenProvider.generateTokenDto(authentication);

        // 저장소 정보 업데이트
        //RefreshToken newRefreshToken = refreshToken.updateValue(tokenDto.getRefreshToken());
        //refreshTokenRepository.save(newRefreshToken);

        // 토큰 발급
        return tokenDto;
    }

    public TokenDto getJwtToken(UserDto user, Room room) {
        TokenDto token = generateToken(room, user);

        // RefreshToken 저장
//        RefreshToken refreshToken = RefreshToken.builder()
//                .key(authentication.getName())
//                .value(tokenDto.getRefreshToken())
//                .build();
//        refreshTokenRepository.save(refreshToken);
        return token;
    }
}
