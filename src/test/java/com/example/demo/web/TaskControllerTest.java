package com.example.demo.web;

import com.example.demo.domain.Task;
import com.example.demo.domain.TaskStatus;
import com.example.demo.service.TaskService;
import com.example.demo.service.exception.IllegalTaskStateException;
import com.example.demo.service.exception.TaskAlreadyExistsException;
import com.example.demo.service.exception.TaskNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = { TaskController.class, GlobalExceptionHandler.class })
class TaskControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean TaskService service;

    private Task fake(String id, TaskStatus status) {
        Task t = org.mockito.Mockito.mock(Task.class);
        when(t.getTaskId()).thenReturn(id);
        when(t.getExecuteAt()).thenReturn(Instant.parse("2030-01-01T00:00:00Z"));
        when(t.getPayload()).thenReturn(Map.of("k", "v"));
        when(t.getStatus()).thenReturn(status);
        when(t.getCreatedAt()).thenReturn(Instant.parse("2026-06-25T00:00:00Z"));
        when(t.getTriggeredAt()).thenReturn(null);
        return t;
    }

    @Test
    void post_happyPath_returns201() throws Exception {
        Task fakeTask = fake("abc", TaskStatus.PENDING);
        when(service.create(eq("abc"), any(Instant.class), any())).thenReturn(fakeTask);
        String body = """
                {"taskId":"abc","executeAt":"2030-01-01T00:00:00Z","payload":{"k":"v"}}""";
        mvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskId").value("abc"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void post_missingTaskId_returns400() throws Exception {
        String body = """
                {"executeAt":"2030-01-01T00:00:00Z","payload":{"k":"v"}}""";
        mvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void post_pastExecuteAt_returns400() throws Exception {
        when(service.create(any(), any(), any()))
                .thenThrow(new IllegalTaskStateException("executeAt is in the past beyond the grace window"));
        String body = """
                {"taskId":"abc","executeAt":"2000-01-01T00:00:00Z","payload":{"k":"v"}}""";
        mvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("past")));
    }

    @Test
    void post_duplicate_returns409() throws Exception {
        when(service.create(any(), any(), any()))
                .thenThrow(new TaskAlreadyExistsException("abc"));
        String body = """
                {"taskId":"abc","executeAt":"2030-01-01T00:00:00Z","payload":{"k":"v"}}""";
        mvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void post_malformedJson_returns400() throws Exception {
        mvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_returns200() throws Exception {
        Task fakeTask = fake("abc", TaskStatus.PENDING);
        when(service.get("abc")).thenReturn(fakeTask);
        mvc.perform(get("/tasks/abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value("abc"));
    }

    @Test
    void get_notFound_returns404() throws Exception {
        when(service.get("abc")).thenThrow(new TaskNotFoundException("abc"));
        mvc.perform(get("/tasks/abc")).andExpect(status().isNotFound());
    }

    @Test
    void list_defaultStatusPending_returnsPage() throws Exception {
        Task fakeTask = fake("abc", TaskStatus.PENDING);
        when(service.list(eq(TaskStatus.PENDING), any()))
                .thenReturn(new PageImpl<>(List.of(fakeTask),
                                            PageRequest.of(0, 20), 1));
        mvc.perform(get("/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].taskId").value("abc"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void list_caseInsensitiveStatus() throws Exception {
        when(service.list(eq(TaskStatus.PENDING), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        mvc.perform(get("/tasks?status=pending")).andExpect(status().isOk());
    }

    @Test
    void list_sizeOver100_returns400() throws Exception {
        mvc.perform(get("/tasks?size=200")).andExpect(status().isBadRequest());
    }

    @Test
    void list_invalidStatus_returns400() throws Exception {
        mvc.perform(get("/tasks?status=FOO")).andExpect(status().isBadRequest());
    }

    @Test
    void delete_pending_returns204() throws Exception {
        mvc.perform(delete("/tasks/abc")).andExpect(status().isNoContent());
    }

    @Test
    void delete_notFound_returns404() throws Exception {
        doThrow(new TaskNotFoundException("abc")).when(service).cancel("abc");
        mvc.perform(delete("/tasks/abc")).andExpect(status().isNotFound());
    }

    @Test
    void delete_alreadyTriggered_returns409() throws Exception {
        doThrow(new IllegalTaskStateException("task already triggered")).when(service).cancel("abc");
        mvc.perform(delete("/tasks/abc")).andExpect(status().isConflict());
    }
}
