package com.csthesis.projectTracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ProjectTrackerApiIntegrationTests {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private WebApplicationContext webApplicationContext;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void registerAndLoginReturnJwtTokens() throws Exception {
        String username = uniqueUsername("authuser");
        String password = "password123";

        String registerToken = registerAndGetToken(username, password);
        assertThat(registerToken).isNotBlank();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedUserCanManageProjectsAndTasks() throws Exception {
        String token = registerAndGetToken(uniqueUsername("owner"), "password123");

        long projectId = createProject(token, "Project Alpha", "Main delivery");

        MvcResult createTaskResult = mockMvc.perform(post("/api/projects/{projectId}/tasks", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Build API",
                                "description", "Implement secured endpoints",
                                "status", "IN_PROGRESS",
                                "dueDate", "2026-12-31"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Build API"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andReturn();

        long taskId = readLongField(createTaskResult, "id");
        assertThat(taskId).isPositive();

        mockMvc.perform(get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value((int) projectId))
                .andExpect(jsonPath("$[0].name").value("Project Alpha"));

        mockMvc.perform(get("/api/projects/{projectId}/tasks", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value((int) taskId))
                .andExpect(jsonPath("$[0].projectId").value((int) projectId));

        mockMvc.perform(put("/api/projects/{projectId}/tasks/{taskId}", projectId, taskId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Build API v2",
                                "description", "Finalize endpoints",
                                "status", "DONE",
                                "dueDate", "2026-11-30"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Build API v2"))
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void userCannotAccessAnotherUsersProject() throws Exception {
        String ownerToken = registerAndGetToken(uniqueUsername("ownerA"), "password123");
        String otherUserToken = registerAndGetToken(uniqueUsername("ownerB"), "password123");
        long ownerProjectId = createProject(ownerToken, "Private Project", "Only owner can access");

        mockMvc.perform(get("/api/projects/{projectId}", ownerProjectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherUserToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateUsernameRegistrationIsRejected() throws Exception {
        String username = uniqueUsername("duplicate");
        String payload = jsonBody(username, "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTaskWithoutStatusDefaultsToTodo() throws Exception {
        String token = registerAndGetToken(uniqueUsername("defaults"), "password123");
        long projectId = createProject(token, "Project Defaults", "Verify task defaults");

        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "  Task without explicit status  ");
        payload.put("description", "Should default status");
        payload.put("dueDate", "2026-10-01");

        mockMvc.perform(post("/api/projects/{projectId}/tasks", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Task without explicit status"))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    void deletingTaskRemovesItFromProjectTaskList() throws Exception {
        String token = registerAndGetToken(uniqueUsername("deleter"), "password123");
        long projectId = createProject(token, "Cleanup Project", "Task deletion flow");

        MvcResult createTaskResult = mockMvc.perform(post("/api/projects/{projectId}/tasks", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Disposable task",
                                "description", "Will be deleted",
                                "status", "TODO",
                                "dueDate", "2026-10-15"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        long taskId = readLongField(createTaskResult, "id");
        assertThat(taskId).isPositive();

        mockMvc.perform(delete("/api/projects/{projectId}/tasks/{taskId}", projectId, taskId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/{projectId}/tasks", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    private String registerAndGetToken(String username, String password) throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(username, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andReturn();

        return readTextField(registerResult, "token");
    }

    private long createProject(String token, String name, String description) throws Exception {
        MvcResult createProjectResult = mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "description", description
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andReturn();

        return readLongField(createProjectResult, "id");
    }

    private String jsonBody(String username, String password) throws Exception {
        Map<String, String> payload = new HashMap<>();
        payload.put("username", username);
        payload.put("password", password);
        return objectMapper.writeValueAsString(payload);
    }

    private long readLongField(MvcResult result, String fieldName) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.path(fieldName).asLong();
    }

    private String readTextField(MvcResult result, String fieldName) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.path(fieldName).asText();
    }

    private String uniqueUsername(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
