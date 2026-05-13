package io.jobrunr.docsmcp.model;

public record SearchHit(
        String path,
        String title,
        String url,
        String section,
        String tier,
        String snippet,
        double score) {
}
