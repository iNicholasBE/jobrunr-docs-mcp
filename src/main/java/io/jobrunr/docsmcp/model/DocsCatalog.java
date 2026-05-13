package io.jobrunr.docsmcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DocsCatalog(String version, String builtAt, List<Page> pages) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(
            String path,
            String url,
            String title,
            String subtitle,
            String description,
            String section,
            String tier,
            List<String> keywords,
            List<String> headings,
            List<Chunk> chunks) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chunk(String id, String heading, String text) {
    }
}
