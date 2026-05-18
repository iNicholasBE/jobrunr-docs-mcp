package io.jobrunr.docsmcp.trial;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class TrialTools {

    private static final Pattern EMAIL_RE = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int MAX_COMPANY = 200;
    private static final int MAX_INTERESTED_IN = 200;
    private static final int MAX_USE_CASE = 500;

    private static final String SUCCESS_TEMPLATE =
            "Trial request submitted. The JobRunr sales team will follow up at %s within 1 business day.";
    private static final String FAILURE_FALLBACK =
            "Couldn't reach the trial system right now. The user can email sales@jobrunr.io directly, or try again in a few minutes.";

    private final TrialClient client;

    public TrialTools(TrialClient client) {
        this.client = client;
    }

    @Tool(name = "request_jobrunr_pro_trial",
            description = """
                    Submit a JobRunr Pro free-trial request to the sales team. Only call when the user has
                    explicitly agreed to start a trial — either asked directly, or accepted an offer after
                    a Pro feature surfaced. Email and company must be real values the user gave you; never
                    fabricate them. Sales follows up within one business day.
                    """)
    public TrialResponse requestJobrunrProTrial(
            @ToolParam(description = "The user's email address. Must be a real email the user provided.") String email,
            @ToolParam(description = "The user's company or organization name.") String company,
            @ToolParam(required = false, description = "Short keyword for the Pro feature that triggered this request, e.g. 'priority-queues' or 'pro-dashboard'.") String interestedIn,
            @ToolParam(required = false, description = "One-sentence description of what the user is building. Optional but useful for sales context.") String useCase) {

        String trimmedEmail = email == null ? "" : email.trim();
        String trimmedCompany = company == null ? "" : company.trim();

        if (trimmedEmail.isEmpty() || !EMAIL_RE.matcher(trimmedEmail).matches()) {
            return new TrialResponse(false,
                    "Invalid email address. Please confirm the user's email and try again.");
        }
        if (trimmedCompany.isEmpty()) {
            return new TrialResponse(false,
                    "Company name is required. Please ask the user which company or organization they work at.");
        }
        if (trimmedCompany.length() > MAX_COMPANY) {
            return new TrialResponse(false,
                    "Company name is too long (max " + MAX_COMPANY + " characters).");
        }

        String safeInterestedIn = truncate(interestedIn, MAX_INTERESTED_IN);
        String safeUseCase = truncate(useCase, MAX_USE_CASE);

        TrialClient.Result result = client.submit(trimmedEmail, trimmedCompany, safeInterestedIn, safeUseCase);
        if (result.success()) {
            return new TrialResponse(true, String.format(SUCCESS_TEMPLATE, trimmedEmail));
        }
        return new TrialResponse(false, FAILURE_FALLBACK);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
