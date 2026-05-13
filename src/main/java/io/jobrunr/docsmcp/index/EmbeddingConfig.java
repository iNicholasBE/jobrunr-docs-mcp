package io.jobrunr.docsmcp.index;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingConfig {

    @Bean
    public EmbeddingModel embeddingModel() throws Exception {
        TransformersEmbeddingModel model = new TransformersEmbeddingModel();
        model.afterPropertiesSet();
        return model;
    }
}
