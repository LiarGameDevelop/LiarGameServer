package com.game.liar.repository;

import com.game.liar.room.domain.GameUser;
import com.game.liar.room.domain.RoomId;
import com.game.liar.room.domain.UserId;
import com.game.liar.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class UserRepositoryTest {
    @Autowired
    private UserRepository userRepository;
    private final PasswordEncoder encoder= Mockito.mock(PasswordEncoder.class);

    @Test
    @DisplayName("유저를 생성한다")
    public void createUserTest() throws Exception{
        //Given
        String username = "user1";
        String password = "password";
        when(encoder.encode(anyString())).thenReturn("password2");

        //When
        GameUser user = userRepository.createUser(username,password, RoomId.of("asdf"),encoder);

        //Then
        assertThat(userRepository.findAll().size()).isEqualTo(1);
        assertThat(user.getPassword()).isNotEqualTo(password);
        assertThat(userRepository.findByUserIdAndRoomId(user.getUserId(),RoomId.of("asdf")).isPresent()).isTrue();
        assertThat(userRepository.findByRoomId(RoomId.of("asdf")).size()).isEqualTo(1);
        assertThat(userRepository.findByUserId(user.getUserId()).get()).isEqualTo(user);
    }

    @Test
    @DisplayName("세션 아이디를 저장한다.")
    public void saveSessionIdTest() throws Exception{
        //Given
        String username = "user1";
        String password = "password";
        when(encoder.encode(anyString())).thenReturn("password2");
        GameUser user = userRepository.createUser(username,password, RoomId.of("asdf"),encoder);

        //when
        String sessionId= "mysessionId";
        user.saveSession(sessionId);

        //Then
        assertThat(userRepository.findAll().size()).isEqualTo(1);
        assertThat(user.getPassword()).isNotEqualTo(password);
        assertThat(userRepository.findByUserIdAndRoomId(user.getUserId(),RoomId.of("asdf")).isPresent()).isTrue();
        assertThat(userRepository.findByRoomId(RoomId.of("asdf")).size()).isEqualTo(1);
        assertThat(userRepository.findByUserId(user.getUserId()).get()).isEqualTo(user);
        assertThat(userRepository.findByUserId(user.getUserId()).get().getSessionId()).isEqualTo(sessionId);
    }

    @Test
    @DisplayName("많은 유저를 생성한다")
    public void createUsersTest() throws Exception{
        //Given
        String username = "user1";
        String password = "password";
        when(encoder.encode(anyString())).thenReturn("password2");

        //When
        GameUser user=userRepository.createUser(username,password, RoomId.of("room"),encoder);;
        for(int i=0;i<4;++i){
            userRepository.createUser(username+i,password, RoomId.of("room2"),encoder);
        }

        //Then
        assertThat(userRepository.findAll().size()).isEqualTo(5);
        assertThat(userRepository.findByUserIdAndRoomId(user.getUserId(),RoomId.of("room")).isPresent()).isTrue();
        assertThat(userRepository.findByUserIdAndRoomId(user.getUserId(),RoomId.of("room2")).isPresent()).isFalse();
        assertThat(userRepository.findByRoomId(RoomId.of("room2")).size()).isEqualTo(4);
        assertThat(userRepository.findByUserId(user.getUserId()).get()).isEqualTo(user);
    }
}
