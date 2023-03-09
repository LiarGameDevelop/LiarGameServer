package com.game.liar.game.repository;

import com.game.liar.game.domain.GameSubject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameSubjectRepository extends JpaRepository<GameSubject, Long> {
    Optional<GameSubject> findByCategoryAndKeyword(String category, String keyword);
}

