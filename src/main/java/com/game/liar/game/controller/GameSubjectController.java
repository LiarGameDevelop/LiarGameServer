package com.game.liar.game.controller;


import com.game.liar.game.dto.GameSubjectDto;
import com.game.liar.game.service.GameSubjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class GameSubjectController {
    private final GameSubjectService gameSubjectService;

    @GetMapping("/game/categories")
    public List<String> getAllCategories() {
        List<String> response = gameSubjectService.getAllCategory();
        log.info("[getAllCategories] response {}", response);
        return response;
    }

    @PostMapping("/game/subject")
    public ResponseEntity.BodyBuilder addSubject(@RequestBody GameSubjectDto request) {
        log.info("[addSubject] request {}", request);
        gameSubjectService.addSubject(request);
        return ResponseEntity.ok();
    }

    @PostMapping("/game/subjects")
    public ResponseEntity.BodyBuilder addSubjects(@RequestBody List<GameSubjectDto> request) {
        log.info("[addSubject] request {}", request);
        gameSubjectService.addSubjects(request);
        return ResponseEntity.ok();
    }
}
