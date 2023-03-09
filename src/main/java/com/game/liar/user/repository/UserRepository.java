package com.game.liar.user.repository;

import com.game.liar.room.domain.Authority;
import com.game.liar.room.domain.GameUser;
import com.game.liar.room.domain.RoomId;
import com.game.liar.room.domain.UserId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<GameUser, UserId> {
    default GameUser createUser(String username, String password, RoomId roomId, PasswordEncoder encoder) {
        GameUser user = new GameUser
                (nextUserId(),
                        roomId,
                        username,
                        encoder.encode(password),
                        Authority.ROLE_USER);
        return save(user);
    }

    Optional<GameUser> findByUserId(UserId userId);

    @Query("select u from GameUser u where u.roomId=:roomId and u.userId=:userId")
    Optional<GameUser> findByUserIdAndRoomId(UserId userId, RoomId roomId);

    default UserId nextUserId() {
        return new UserId(UUID.randomUUID().toString());
    }

    @Query("select u from GameUser u where u.roomId =:id order by u.createdTime")
    List<GameUser> findByRoomId(RoomId id);

    @Query("select u from GameUser u where u.sessionId =:sessionId")
    Optional<GameUser> findBySessionId(String sessionId);
}
