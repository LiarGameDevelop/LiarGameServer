package com.game.liar.service;

import com.game.liar.exception.AlreadyExistException;
import com.game.liar.game.config.GameCategoryProperties;
import com.game.liar.game.domain.GameSubject;
import com.game.liar.game.dto.GameSubjectDto;
import com.game.liar.game.repository.GameSubjectRepository;
import com.game.liar.game.service.GameSubjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GameSubjectServiceTest {
    @Mock
    private GameSubjectRepository gameSubjectRepository;
    @Mock
    private GameCategoryProperties predefined;
    @InjectMocks
    private GameSubjectService subjectService;

    @Test
    @DisplayName("게임 주제 초깃값 로드에 성공한다.")
    public void loadGameSubject() throws Exception {
        //Given
        when(gameSubjectRepository.save(any())).thenReturn(null);
        Map<String, List<String>> subjects = new HashMap<String, List<String>>() {
            {
                put("category1", Arrays.asList("keyword1", "keyword2"));
                put("category2", Arrays.asList("keyword3", "keyword4", "keyword5"));
                put("category3", Arrays.asList("keyword3", "keyword4", "keyword2"));
                put("category4", Arrays.asList("keyword6", "keyword7", "keyword8"));
                put("category5", Arrays.asList("keyword9", "keyword10", "keyword11"));
            }
        };
        List<GameSubject> gameSubjects = new ArrayList<GameSubject>() {
            {
                add(new GameSubject("category1", "keyword1"));
                add(new GameSubject("category1", "keyword2"));
                add(new GameSubject("category3", "keyword3"));
                add(new GameSubject("category3", "keyword4"));
                add(new GameSubject("category2", "keyword3"));
                add(new GameSubject("category2", "keyword4"));
                add(new GameSubject("category2", "keyword5"));
                add(new GameSubject("category3", "keyword2"));
                add(new GameSubject("category4", "keyword6"));
                add(new GameSubject("category5", "keyword9"));
                add(new GameSubject("category5", "keyword10"));
                add(new GameSubject("category4", "keyword7"));
                add(new GameSubject("category4", "keyword8"));
                add(new GameSubject("category5", "keyword11"));
            }
        };
        when(gameSubjectRepository.findAll()).thenReturn(gameSubjects);
        when(predefined.getKeywords()).thenReturn(subjects);
        when(gameSubjectRepository.isTableEmpty()).thenReturn(true);

        //When
        Map<String, List<String>> loadedInitialCategory = subjectService.loadInitialCategory();

        //Then
        assertThat(loadedInitialCategory.size()).isEqualTo(predefined.getKeywords().size());
        assertThat(new ArrayList<>(loadedInitialCategory.keySet())).isEqualTo(new ArrayList<>(predefined.getKeywords().keySet()));
        assertThat(loadedInitialCategory.values().stream().map(List::size).reduce(0, Integer::sum))
                .isEqualTo(predefined.getKeywords().values().stream().map(List::size).reduce(0, Integer::sum));

        verify(gameSubjectRepository, times(gameSubjects.size())).save(any());
        verify(gameSubjectRepository, times(predefined.getKeywords().values().stream().map(List::size).reduce(0, Integer::sum))).save(any());
        verify(gameSubjectRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("게임 주제를 추가한다")
    public void addGameSubject() throws Exception {
        //Given
        GameSubjectDto gameSubjectDto = new GameSubjectDto("category", "keyword");

        //When
        subjectService.addSubject(gameSubjectDto);
        //Then
        verify(gameSubjectRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("게임 주제를 추가하는데 실패한다")
    public void addGameSubject_error() throws Exception {
        //Given
        GameSubjectDto gameSubjectDto = new GameSubjectDto("category", "keyword");
        GameSubjectDto gameSubjectDto2 = new GameSubjectDto("category", "keyword");

        //When
        subjectService.addSubject(gameSubjectDto);
        GameSubject fakeList = new GameSubject("category", "keyword");
        when(gameSubjectRepository.findByCategoryAndKeyword(any(), any())).thenReturn(Optional.of(fakeList));

        //Then
        assertThrows(AlreadyExistException.class, () -> subjectService.addSubject(gameSubjectDto2));
        verify(gameSubjectRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("게임 주제를 여러개 추가한다")
    public void addGameSubjects() throws Exception {
        //Given
        when(gameSubjectRepository.save(any())).thenReturn(null);
        List<GameSubjectDto> subjects = new ArrayList<>();
        subjects.add(new GameSubjectDto("category", "keyword"));
        subjects.add(new GameSubjectDto("category2", "keyword2"));
        subjects.add(new GameSubjectDto("category3", "keyword3"));

        //When
        subjectService.addSubjects(subjects);
        //Then
        verify(gameSubjectRepository, times(3)).save(any());
    }

}
