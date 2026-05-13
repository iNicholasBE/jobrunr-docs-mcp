package io.jobrunr.docsmcp;

import io.jobrunr.docsmcp.tools.DocsTools;
import io.jobrunr.docsmcp.trial.TrialTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DocsMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocsMcpApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider docsToolCallbacks(DocsTools docsTools, TrialTools trialTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(docsTools, trialTools)
                .build();
    }
}
