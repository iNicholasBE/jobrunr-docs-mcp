package io.jobrunr.docsmcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Manifest(String version, String builtAt, int count, int chunks, String sha256) {
}
