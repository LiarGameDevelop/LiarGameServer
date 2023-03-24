package com.game.liar.repository;

import com.game.liar.game.domain.GameSubject;
import com.game.liar.game.repository.GameSubjectRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class GameSubjectRepositoryTest {
    @Autowired
    private GameSubjectRepository gameSubjectRepository;

    @Test
    @DisplayName("새로운 카테고리 및 키워드 추가에 성공한다")
    public void addGameSubject () throws Exception{
        //Given
        GameSubject subject = new GameSubject("keyword1","category1");

        //When
        GameSubject result = gameSubjectRepository.save(subject);

        //Then
        assertThat(gameSubjectRepository.findAll().size()).isEqualTo(1);
        assertThat(gameSubjectRepository.findById(result.getId()).isPresent()).isTrue();
        assertThat(gameSubjectRepository.findById(result.getId()).get()).isEqualTo(subject);
    }

    @Test
    @DisplayName("카테고리 및 키워드를 검색한다")
    public void findGameSubject () throws Exception{
        //Given
        GameSubject subject = new GameSubject("category1","keyword1");

        //When
        GameSubject result = gameSubjectRepository.save(subject);

        //Then
        assertThat(gameSubjectRepository.findAll().size()).isEqualTo(1);
        assertThat(gameSubjectRepository.findByCategoryAndKeyword("category1","keyword1").isPresent()).isTrue();
    }

    @Test
    @DisplayName("카테고리가 비어있는지 확인한다")
    public void checkGameSubjectIsEmpty () throws Exception{
        //Given
        GameSubject subject = new GameSubject("category1","keyword1");

        //When
        Boolean result = gameSubjectRepository.isTableEmpty();

        //Then
        assertThat(result).isEqualTo(true);
    }

    @Test
    @DisplayName("카테고리가 비어있어 있지 않은지 확인한다")
    public void checkGameSubjectIsNotEmpty () throws Exception{
        //Given
        GameSubject subject = new GameSubject("category1","keyword1");

        //When
        gameSubjectRepository.save(subject);
        Boolean result = gameSubjectRepository.isTableEmpty();

        //Then
        assertThat(result).isEqualTo(false);
    }

}
