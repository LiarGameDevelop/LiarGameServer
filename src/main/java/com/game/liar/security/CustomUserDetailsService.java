package com.game.liar.security;

import com.game.liar.user.domain.GameUser;
import com.game.liar.room.domain.RoomId;
import com.game.liar.user.domain.UserId;
import com.game.liar.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

import static com.game.liar.security.util.JwtUtil.getRoomIdFromUUID;
import static com.game.liar.security.util.JwtUtil.getUserIdFromUUID;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    //Username is room id + username
    @Override
    public UserDetails loadUserByUsername(java.lang.String s) throws UsernameNotFoundException {
        RoomId roomId = RoomId.of(getRoomIdFromUUID(s));
        UserId userId = UserId.of(getUserIdFromUUID(s));
        GameUser user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User does not exist"));

        return new User(
                roomId.getId()+ userId.getUserId(),
                user.getPassword(),
                Collections.singleton(new SimpleGrantedAuthority(user.getAuthority().toString()))
        );
    }
}
