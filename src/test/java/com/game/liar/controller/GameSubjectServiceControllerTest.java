package com.game.liar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.game.controller.GameSubjectController;
import com.game.liar.game.dto.GameSubjectDto;
import com.game.liar.game.repository.GameSubjectRepository;
import com.game.liar.game.service.GameSubjectService;
import org.apache.catalina.security.SecurityConfig;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@WebMvcTest(GameSubjectController.class)
@ContextConfiguration(classes= SecurityConfig.class)
@Disabled("401/403 에러")
public class GameSubjectServiceControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private GameSubjectService subjectService;

    @Test
    @DisplayName("전체 게임 카테고리를 조회한다")
    public void getAllCategories() throws Exception {
        when(subjectService.getAllCategory()).thenReturn(Arrays.asList("sports", "place"));
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
        doNothing().when(subjectService).addSubject(any());
        ObjectMapper om = new ObjectMapper();
        GameSubjectDto request = new GameSubjectDto("category", "keyword");
        String result = mockMvc.perform(
                        post("/game/subject")
                                .contentType(MediaType.APPLICATION_JSON)
                                .with(csrf())
                                .content(om.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        System.out.println("result =" + result);
    }
}
