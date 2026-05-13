package io.jobrunr.docsmcp.trial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TrialClient {

    private static final Logger log = LoggerFactory.getLogger(TrialClient.class);

    private final WebClient webClient;
    private final String webhookUrl;
    private final Duration timeout;

    public TrialClient(
            @Value("${trial.webhook-url}") String webhookUrl,
            @Value("${trial.timeout:PT10S}") Duration timeout) {
        this.webhookUrl = webhookUrl;
        this.timeout = timeout;
        this.webClient = WebClient.builder().build();
    }

    public Result submit(String email, String company, String interestedIn, String useCase) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("company", company);
        body.put("form", "mcp-trial");
        if (interestedIn != null && !interestedIn.isBlank()) body.put("interested_in", interestedIn);
        if (useCase != null && !useCase.isBlank()) body.put("use_case", useCase);
        body.put("submitted_at", Instant.now().toString());

        try {
            HttpStatusCode status = webClient.post()
                    .uri(webhookUrl)
                    .bodyValue(body)
                    .exchangeToMono(resp -> resp.releaseBody().thenReturn(resp.statusCode()))
                    .timeout(timeout)
                    .block();
            if (status == null || !status.is2xxSuccessful()) {
                log.warn("Trial webhook returned non-2xx: {}", status);
                return new Result(false, "Webhook returned " + status);
            }
            log.info("Trial request submitted for {} @ {} (interested_in={})", email, company, interestedIn);
            return new Result(true, "Submitted");
        } catch (WebClientResponseException e) {
            log.warn("Trial webhook responded {}: {}", e.getStatusCode(), e.getMessage());
            return new Result(false, "Webhook error: " + e.getStatusCode());
        } catch (Exception e) {
            log.warn("Trial webhook failed: {}", e.toString());
            return new Result(false, "Webhook unreachable");
        }
    }

    public record Result(boolean success, String detail) {}
}
