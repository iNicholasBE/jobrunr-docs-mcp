package io.jobrunr.docsmcp.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.net.InetSocketAddress;

public final class ClientIpExtractor {

    private ClientIpExtractor() {}

    public static String from(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();

        String flyClientIp = headers.getFirst("Fly-Client-IP");
        if (flyClientIp != null && !flyClientIp.isBlank()) {
            return flyClientIp.trim();
        }

        String xff = headers.getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }

        InetSocketAddress remote = request.getRemoteAddress();
        if (remote != null && remote.getAddress() != null) {
            return remote.getAddress().getHostAddress();
        }
        return null;
    }
}
