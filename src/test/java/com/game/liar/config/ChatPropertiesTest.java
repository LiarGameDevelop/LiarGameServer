package com.game.liar.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(ChatProperties.class)
class ChatPropertiesTest {
    @Autowired
    ChatProperties properties;
    @Test
    public void yml파일에서_읽어온값이_제대로_객체에들어가있다 (){
        assertThat(properties.getDestinations().getLogin()).isEqualTo("/subscribe/room.login/");
        assertThat(properties.getDestinations().getLogout()).isEqualTo("/subscribe/room.logout/");
    }
}