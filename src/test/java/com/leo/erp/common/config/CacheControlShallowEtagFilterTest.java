package com.leo.erp.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CacheControlShallowEtagFilterTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new SampleController())
            .addFilter(new CacheControlShallowEtagFilter())
            .build();

    @Test
    void get_shouldReturnWeakEtagAndNoCache() throws Exception {
        MvcResult result = mockMvc.perform(get("/v2.0/sample"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn();

        String etag = result.getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etag).startsWith("W/");

        mockMvc.perform(get("/v2.0/sample").header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified());
    }

    @Test
    void head_shouldReturnEtagConsistentWithGet() throws Exception {
        String getEtag = mockMvc.perform(get("/v2.0/sample"))
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);

        mockMvc.perform(head("/v2.0/sample"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                .andExpect(header().string(HttpHeaders.ETAG, getEtag));
    }

    @Test
    void existingCacheControl_shouldBePreserved() throws Exception {
        mockMvc.perform(get("/v2.0/sample/custom"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=60"));
    }

    @Test
    void binaryDownload_shouldSkipEtag() throws Exception {
        mockMvc.perform(get("/v2.0/attachments/1/content"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ETAG));
    }

    @RestController
    static class SampleController {

        @GetMapping("/v2.0/sample")
        String sample() {
            return "hello";
        }

        @GetMapping("/v2.0/sample/custom")
        ResponseEntity<String> custom() {
            return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "max-age=60").body("custom");
        }

        @GetMapping("/v2.0/attachments/1/content")
        String content() {
            return "binary";
        }
    }
}
