package com.game.liar.game.repository;

import com.game.liar.game.domain.GameSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface GameSubjectRepository extends JpaRepository<GameSubject, Long> {
    Optional<GameSubject> findByCategoryAndKeyword(String category, String keyword);
    //@Query("SELECT NOT EXISTS (SELECT 1 FROM GameSubject)")
    @Query("SELECT COUNT(*) = 0 FROM GameSubject g")
    Boolean isTableEmpty();

}

