package cc.fascinated.tests;

import cc.fascinated.config.TestRedisConfig;
import cc.fascinated.model.player.Skin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(classes = TestRedisConfig.class)
class PlayerControllerTests {

    private final String testPlayerUuid = "eeab5f8a-18dd-4d58-af78-2b3c4543da48";
    private final String testPlayer = "ImFascinated";
    private final String testInvalidPlayer = "invalidplayeromgyeswow";

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void ensurePlayerLookupUuidSuccess() throws Exception {
        mockMvc.perform(get("/player/" + testPlayerUuid)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(testPlayer));
    }

    @Test
    public void ensurePlayerLookupUsernameSuccess() throws Exception {
        mockMvc.perform(get("/player/" + testPlayer)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(testPlayer));
    }

    @Test
    public void ensurePlayerUsernameToUuidLookupSuccess() throws Exception {
        mockMvc.perform(get("/player/uuid/" + testPlayer)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(testPlayer))
                .andExpect(jsonPath("$.uniqueId").value(testPlayerUuid));
    }

    @Test
    public void ensurePlayerUsernameToUuidLookupFailure() throws Exception {
        mockMvc.perform(get("/player/uuid/" + testInvalidPlayer)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    public void ensurePlayerLookupFailure() throws Exception {
        mockMvc.perform(get("/player/" + testInvalidPlayer)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    public void ensurePlayerSkinPartsLookupSuccess() throws Exception {
        for (Skin.Parts part : Skin.Parts.values()) {
            mockMvc.perform(get("/player/" + part.getName() + "/" + testPlayerUuid)
                    .accept(MediaType.IMAGE_PNG)
                    .contentType(MediaType.IMAGE_PNG))
                    .andExpect(status().isOk());
        }
    }

    @Test
    public void ensurePlayerSkinPartsLookupFailure() throws Exception {
        mockMvc.perform(get("/player/invalidpart/" + testPlayer))
                .andExpect(status().isBadRequest());
    }
}
