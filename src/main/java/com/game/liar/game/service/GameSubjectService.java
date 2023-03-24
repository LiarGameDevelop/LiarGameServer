package com.game.liar.game.service;

import com.game.liar.exception.AlreadyExistException;
import com.game.liar.exception.NotExistException;
import com.game.liar.game.config.GameCategoryProperties;
import com.game.liar.game.domain.GameSubject;
import com.game.liar.game.dto.GameSubjectDto;
import com.game.liar.game.repository.GameSubjectRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GameSubjectService {
    private final GameCategoryProperties predefined;
    private final GameSubjectRepository gameSubjectRepository;

    public GameSubjectService(GameCategoryProperties predefined, GameSubjectRepository gameSubjectRepository) {
        this.predefined = predefined;
        this.gameSubjectRepository = gameSubjectRepository;
    }

    public Map<String, List<String>> loadInitialCategory() {
        if (!hasNoCategory()) return null;

        for (Map.Entry<String, List<String>> subject : predefined.getKeywords().entrySet()) {
            String category = subject.getKey();
            for (String keyword : subject.getValue()) {
                GameSubject newSubject = new GameSubject(category, keyword);
                gameSubjectRepository.save(newSubject);
            }
        }
        return getAllSubject();
    }

    public void addSubject(GameSubjectDto gameSubjectDto) {
        String category = gameSubjectDto.getCategory();
        String keyword = gameSubjectDto.getKeyword();
        if (gameSubjectRepository.findByCategoryAndKeyword(category, keyword).isPresent()) {
            throw new AlreadyExistException("requested category and keyword already exist");
        }
        gameSubjectRepository.save(gameSubjectDto.toEntity());
    }

    public void addSubjects(List<GameSubjectDto> subjects) {
        for (GameSubjectDto subject : subjects) {
            addSubject(subject);
        }
    }

    public Map<String, List<String>> getAllSubject() throws NotExistException {
        Map<String, List<String>> ret = new HashMap<>();
        List<GameSubject> gameSubjects = gameSubjectRepository.findAll().stream().sorted().collect(Collectors.toList());
        if (gameSubjects.isEmpty())
            throw new NotExistException("There is no game subject in DB.");

        String lastCategory = gameSubjects.get(0).getCategory();
        List<String> keywords = new ArrayList<>();
        for (GameSubject subject : gameSubjects) {
            String category = subject.getCategory();
            String keyword = subject.getKeyword();

            if (lastCategory.equals(subject.getCategory())) {
                keywords.add(keyword);
            } else {
                ret.put(lastCategory, keywords);
                lastCategory = category;
                keywords = new ArrayList<>();
                keywords.add(keyword);
            }
        }
        if (!keywords.isEmpty())
            ret.put(lastCategory, keywords);
        return ret;
    }

    public List<String> getAllCategory() throws NotExistException {
        if (hasNoCategory())
            loadInitialCategory();

        log.info("all categories :{}", gameSubjectRepository.findAll());
        return gameSubjectRepository.findAll().stream().sorted().map(GameSubject::getCategory).distinct().collect(Collectors.toList());
    }

    private boolean hasNoCategory() {
        return gameSubjectRepository.isTableEmpty();
    }
}
