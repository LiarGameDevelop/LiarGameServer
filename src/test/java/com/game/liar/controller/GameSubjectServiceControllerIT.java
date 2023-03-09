package com.game.liar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.game.dto.GameSubjectDto;
import com.game.liar.game.service.GameSubjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class GameSubjectServiceControllerIT {
    @Autowired
    private MockMvc mockMvc;
//    @MockBean
//    private GameSubjectService subjectService;

    @Test
    @DisplayName("전체 게임 카테고리를 조회한다")
    public void getAllCategories() throws Exception {
        //when(subjectService.getAllCategory()).thenReturn(Arrays.asList("sports", "place"));
        //Given
        ObjectMapper om = new ObjectMapper();
        String result = mockMvc.perform(
                        get("/game/categories")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        System.out.println("result =" + result);
    }

    @Test
    @DisplayName("게임 주제를 추가한다")
    public void addSubect() throws Exception {
        //doNothing().when(subjectService).addSubject(any());
        ObjectMapper om = new ObjectMapper();
        GameSubjectDto request = new GameSubjectDto("category", "keyword");
        String result = mockMvc.perform(
                        post("/game/subject")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(om.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        System.out.println("result =" + result);
    }

    @Test
    @DisplayName("다수의 게임 주제를 추가한다")
    public void addSubjects() throws Exception {
        //doNothing().when(subjectService).addSubject(any());
        ObjectMapper om = new ObjectMapper();
        List<GameSubjectDto> request = Arrays.asList(
                new GameSubjectDto("category", "keyword"),
                new GameSubjectDto("category1", "keyword1"),
                new GameSubjectDto("category2", "keyword2"));
        String result = mockMvc.perform(
                        post("/game/subjects")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(om.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        System.out.println("result =" + result);
    }
}
