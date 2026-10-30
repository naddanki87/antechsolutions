package com.antech.tasktracker.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

/** Full HTTP flow against the real app (in-memory H2), mapped to the acceptance criteria in docs/SPEC.md. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskApiIntegrationTest {

    private static final String API = "/api/v1/tasks";

    @Autowired
    private MockMvc mvc;

    private String create(String json) throws Exception {
        MvcResult r = mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void createReturns201WithLocationAndDefaults() throws Exception { // AC-1
        mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Pay invoices\",\"dueDate\":\"2026-11-05\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(API + "/")))
                .andExpect(jsonPath("$.title").value("Pay invoices"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.done").value(false))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.dueDate").value("2026-11-05"));
    }

    @Test
    void invalidTitleReturns400ProblemDetail() throws Exception { // AC-2
        mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.type").value("https://antech.dev/problems/validation"))
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + "x".repeat(121) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listExcludesDeletedAndFiltersByDone() throws Exception { // AC-3, AC-4
        String open = create("{\"title\":\"open task\"}");
        String done = create("{\"title\":\"done task\"}");
        String gone = create("{\"title\":\"gone task\"}");
        mvc.perform(put(API + "/" + done).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"done task\",\"done\":true,\"version\":0}")).andExpect(status().isOk());
        mvc.perform(delete(API + "/" + gone)).andExpect(status().isNoContent());

        mvc.perform(get(API).param("size", "100"))
                .andExpect(jsonPath("$.content[*].id", hasItem(open)))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(gone))));
        mvc.perform(get(API).param("done", "true").param("size", "100"))
                .andExpect(jsonPath("$.content[*].id", hasItem(done)))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(open))));
    }

    @Test
    void pageSizeIsClampedTo100() throws Exception { // AC-5
        mvc.perform(get(API).param("size", "500")).andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void unknownTaskReturns404() throws Exception { // AC-6
        mvc.perform(get(API + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://antech.dev/problems/not-found"));
    }

    @Test
    void putUpdatesCreatesAndDetectsConflicts() throws Exception { // AC-7, AC-8, AC-9, AC-10
        String id = create("{\"title\":\"versioned\"}");
        mvc.perform(put(API + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"versioned v1\",\"priority\":\"HIGH\",\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.priority").value("HIGH"));
        mvc.perform(put(API + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"stale\",\"version\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.type").value("https://antech.dev/problems/conflict"));

        String offlineId = UUID.randomUUID().toString();
        mvc.perform(put(API + "/" + offlineId).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"made offline\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(offlineId));

        mvc.perform(delete(API + "/" + offlineId)).andExpect(status().isNoContent());
        mvc.perform(put(API + "/" + offlineId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"edit deleted\",\"version\":1}"))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteIsSoftAndUnknownDeleteIs404() throws Exception { // AC-11
        String id = create("{\"title\":\"to delete\"}");
        mvc.perform(delete(API + "/" + id)).andExpect(status().isNoContent());
        mvc.perform(get(API + "/" + id)).andExpect(status().isNotFound());
        mvc.perform(delete(API + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void changesFeedIncludesDeletedTasks() throws Exception { // AC-12
        String since = Instant.now().minusSeconds(1).toString();
        String id = create("{\"title\":\"sync me\"}");
        mvc.perform(delete(API + "/" + id)).andExpect(status().isNoContent());
        MvcResult r = mvc.perform(get(API + "/changes").param("since", since)).andExpect(status().isOk()).andReturn();
        String body = r.getResponse().getContentAsString();
        assertThat(JsonPath.<java.util.List<String>>read(body, "$.content[?(@.id == '" + id + "')].id")).hasSize(1);
        assertThat(JsonPath.<java.util.List<Boolean>>read(body, "$.content[?(@.id == '" + id + "')].deleted")).containsExactly(true);
    }

    @Test
    void badInputNeverReturns500() throws Exception { // SEC-1, SEC-2
        mvc.perform(get(API).param("sort", "bogus,asc")).andExpect(status().isBadRequest());
        mvc.perform(get(API + "/changes")).andExpect(status().isBadRequest());
        mvc.perform(get(API + "/changes").param("since", "yesterday")).andExpect(status().isBadRequest());
        mvc.perform(get(API + "/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON).content("{\"title\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://antech.dev/problems/malformed-request"));
        mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\",\"priority\":\"URGENT\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(API).contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void errorsDoNotLeakInternals() throws Exception {
        String body = mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON).content("{\"title\":"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("trace", "Exception", "jackson", "at com.");
    }

    @Test
    void securityHeadersAndCors() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
        mvc.perform(options(API).header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
        mvc.perform(options(API).header("Origin", "http://localhost:8080")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8080"));
    }

    @Test
    void openApiDocsArePublished() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/tasks/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/tasks/changes']").exists());
    }
}
