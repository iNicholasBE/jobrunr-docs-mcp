package io.jobrunr.docsmcp.logging;

import java.time.Instant;
import java.util.Map;

public record QueryLogEntry(
        String toolName,
        Map<String, Object> arguments,
        Map<String, Object> resultMetadata,
        String resultBodyTruncated,
        Integer resultBodyTotalBytes,
        boolean success,
        String errorMessage,
        long latencyMs,
        String clientIp,
        String userAgent,
        String mcpServerVersion,
        Instant occurredAt
) {}
