package io.jobrunr.docsmcp.trial;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrialToolsTest {

    private RecordingClient client;
    private TrialTools tools;

    @BeforeEach
    void setup() {
        client = new RecordingClient();
        tools = new TrialTools(client);
    }

    @Test
    void submitsValidRequest() {
        TrialResponse resp = tools.requestJobrunrProTrial(
                "alice@acme.com", "Acme Corp", "priority-queues", "Background billing reconciliation");
        assertThat(resp.success()).isTrue();
        assertThat(resp.message()).contains("alice@acme.com");
        assertThat(client.calls).hasSize(1);
        RecordingClient.Call call = client.calls.get(0);
        assertThat(call.email).isEqualTo("alice@acme.com");
        assertThat(call.company).isEqualTo("Acme Corp");
        assertThat(call.interestedIn).isEqualTo("priority-queues");
        assertThat(call.useCase).isEqualTo("Background billing reconciliation");
    }

    @Test
    void omitsOptionalFieldsWhenAbsent() {
        TrialResponse resp = tools.requestJobrunrProTrial(
                "bob@example.io", "Example Co", null, null);
        assertThat(resp.success()).isTrue();
        assertThat(client.calls).hasSize(1);
        RecordingClient.Call call = client.calls.get(0);
        assertThat(call.interestedIn).isNull();
        assertThat(call.useCase).isNull();
    }

    @Test
    void rejectsInvalidEmail() {
        TrialResponse resp = tools.requestJobrunrProTrial(
                "not-an-email", "Acme Corp", null, null);
        assertThat(resp.success()).isFalse();
        assertThat(resp.message()).containsIgnoringCase("email");
        assertThat(client.calls).isEmpty();
    }

    @Test
    void rejectsBlankEmail() {
        TrialResponse resp = tools.requestJobrunrProTrial("   ", "Acme Corp", null, null);
        assertThat(resp.success()).isFalse();
        assertThat(client.calls).isEmpty();
    }

    @Test
    void rejectsBlankCompany() {
        TrialResponse resp = tools.requestJobrunrProTrial("alice@acme.com", "  ", null, null);
        assertThat(resp.success()).isFalse();
        assertThat(resp.message()).containsIgnoringCase("company");
        assertThat(client.calls).isEmpty();
    }

    @Test
    void surfacesWebhookFailureWithFallback() {
        client.nextResult = new TrialClient.Result(false, "Webhook returned 500");
        TrialResponse resp = tools.requestJobrunrProTrial(
                "alice@acme.com", "Acme Corp", null, null);
        assertThat(resp.success()).isFalse();
        assertThat(resp.message()).contains("sales@jobrunr.io");
    }

    @Test
    void truncatesOverlongOptionalFields() {
        String longInterest = "x".repeat(500);
        String longUseCase = "y".repeat(2000);
        TrialResponse resp = tools.requestJobrunrProTrial(
                "alice@acme.com", "Acme Corp", longInterest, longUseCase);
        assertThat(resp.success()).isTrue();
        RecordingClient.Call call = client.calls.get(0);
        assertThat(call.interestedIn).hasSize(200);
        assertThat(call.useCase).hasSize(500);
    }

    @Test
    void trimsWhitespace() {
        tools.requestJobrunrProTrial("  alice@acme.com  ", "  Acme Corp  ", "  priority  ", "  use case  ");
        RecordingClient.Call call = client.calls.get(0);
        assertThat(call.email).isEqualTo("alice@acme.com");
        assertThat(call.company).isEqualTo("Acme Corp");
        assertThat(call.interestedIn).isEqualTo("priority");
        assertThat(call.useCase).isEqualTo("use case");
    }

    /** Test double that records submit() args and returns a configurable result. */
    static final class RecordingClient extends TrialClient {
        record Call(String email, String company, String interestedIn, String useCase) {}
        final List<Call> calls = new ArrayList<>();
        Result nextResult = new Result(true, "Submitted");

        RecordingClient() {
            super("http://localhost/unused", Duration.ofSeconds(1));
        }

        @Override
        public Result submit(String email, String company, String interestedIn, String useCase) {
            calls.add(new Call(email, company, interestedIn, useCase));
            return nextResult;
        }
    }
}
