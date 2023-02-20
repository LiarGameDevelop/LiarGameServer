package com.game.liar.game.repository;

import com.game.liar.game.domain.GameInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import javax.persistence.LockModeType;
import javax.persistence.QueryHint;
import java.util.Optional;

public interface GameInfoRepository extends JpaRepository<GameInfo, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g.voteCount from GameInfo g where g.roomId=:roomId")
    Integer findVoteCountByRoomId(String roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "javax.persistence.lock.timeout", value = "1000")})
    Optional<GameInfo> findByRoomId(String roomId);
}
