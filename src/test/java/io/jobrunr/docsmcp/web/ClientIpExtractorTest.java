package io.jobrunr.docsmcp.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpExtractorTest {

    @Test
    void flyClientIpWins() {
        var req = MockServerHttpRequest.get("/")
                .header("Fly-Client-IP", "1.1.1.1")
                .header("X-Forwarded-For", "2.2.2.2, 3.3.3.3")
                .build();
        assertThat(ClientIpExtractor.from(req)).isEqualTo("1.1.1.1");
    }

    @Test
    void xffFirstHopUsedWhenNoFlyHeader() {
        var req = MockServerHttpRequest.get("/")
                .header("X-Forwarded-For", "5.5.5.5, 6.6.6.6")
                .build();
        assertThat(ClientIpExtractor.from(req)).isEqualTo("5.5.5.5");
    }

    @Test
    void fallsBackToRemoteAddress() {
        var req = MockServerHttpRequest.get("/")
                .remoteAddress(new java.net.InetSocketAddress("7.7.7.7", 12345))
                .build();
        assertThat(ClientIpExtractor.from(req)).isEqualTo("7.7.7.7");
    }

    @Test
    void returnsNullWhenNothingAvailable() {
        var req = MockServerHttpRequest.get("/").build();
        assertThat(ClientIpExtractor.from(req)).isNull();
    }
}
