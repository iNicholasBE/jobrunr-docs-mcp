package io.jobrunr.docsmcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jobrunr.docsmcp.index.DocsRegistry;
import io.jobrunr.docsmcp.index.HybridSearch;
import io.jobrunr.docsmcp.index.LuceneIndex;
import io.jobrunr.docsmcp.index.VectorIndex;
import io.jobrunr.docsmcp.model.DocsCatalog;
import io.jobrunr.docsmcp.model.SearchHit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.io.InputStream;
import java.util.List;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocsToolsTest {

    private static DocsCatalog catalog;
    private static DocsTools tools;
    private static DocsRegistry registry;

    @BeforeAll
    static void setup() throws Exception {
        try (InputStream in = DocsToolsTest.class.getResourceAsStream("/sample-docs.json")) {
            assertThat(in).isNotNull();
            catalog = new ObjectMapper().readValue(in, DocsCatalog.class);
        }
        registry = new DocsRegistry();
        registry.set(catalog);

        LuceneIndex lucene = new LuceneIndex();
        lucene.rebuild(catalog);

        VectorIndex vector = new VectorIndex(new DeterministicEmbeddingModel());
        vector.rebuild(catalog);

        HybridSearch hybrid = new HybridSearch(lucene, vector, 60, 50);
        tools = new DocsTools(hybrid, registry);
    }

    @Test
    void searchReturnsRankedResults() {
        DocsTools.SearchResponse resp = tools.searchJobrunrDocs("recurring jobs", 5);
        assertThat(resp.results()).isNotEmpty();
        assertThat(resp.results().get(0).path()).contains("recurring-jobs");
        SearchHit top = resp.results().get(0);
        assertThat(top.url()).startsWith("https://www.jobrunr.io/en/documentation/");
        assertThat(top.snippet()).isNotBlank();
    }

    @Test
    void searchEmptyQueryReturnsNoResults() {
        DocsTools.SearchResponse resp = tools.searchJobrunrDocs("", 5);
        assertThat(resp.results()).isEmpty();
        assertThat(resp.proTrialHint()).isNull();
    }

    @Test
    void searchAttachesProTrialHintWhenProResultsPresent() {
        DocsTools.SearchResponse resp = tools.searchJobrunrDocs("priority queues", 5);
        assertThat(resp.results()).isNotEmpty();
        assertThat(resp.results()).anyMatch(h -> "pro".equals(h.tier()));
        assertThat(resp.proTrialHint()).isNotNull();
        assertThat(resp.proTrialHint().tool()).isEqualTo("request_jobrunr_pro_trial");
        assertThat(resp.proTrialHint().message()).containsIgnoringCase("trial");
    }

    @Test
    void searchOmitsProTrialHintWhenOnlyOssResults() {
        DocsTools.SearchResponse resp = tools.searchJobrunrDocs("Jackson serialization", 3);
        assertThat(resp.results()).isNotEmpty();
        assertThat(resp.results()).allMatch(h -> "oss".equals(h.tier()));
        assertThat(resp.proTrialHint()).isNull();
    }

    @Test
    void fetchReturnsMarkdown() {
        DocsTools.DocPage page = tools.fetchJobrunrDoc("background-methods/recurring-jobs");
        assertThat(page.markdown()).startsWith("# ");
        assertThat(page.url()).contains("recurring-jobs");
    }

    @Test
    void fetchRejectsPathTraversal() {
        assertThatThrownBy(() -> tools.fetchJobrunrDoc("../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.fetchJobrunrDoc("/absolute"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fetchUnknownPathThrows() {
        assertThatThrownBy(() -> tools.fetchJobrunrDoc("does-not-exist"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listSectionsGroupsPages() {
        List<DocsTools.SectionListing> sections = tools.listJobrunrDocSections();
        assertThat(sections).isNotEmpty();
        assertThat(sections.stream().map(DocsTools.SectionListing::section))
                .contains("background-methods", "configuration");
    }

    /**
     * Deterministic 32-dim embedding so tests don't need to download an ONNX model.
     * Quality is irrelevant — we only validate wiring.
     */
    static final class DeterministicEmbeddingModel implements EmbeddingModel {
        @Override
        public float[] embed(String text) {
            float[] v = new float[32];
            CRC32 crc = new CRC32();
            for (int i = 0; i < v.length; i++) {
                crc.reset();
                crc.update((text + ":" + i).getBytes());
                v[i] = (crc.getValue() % 1000) / 1000f - 0.5f;
            }
            return v;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<org.springframework.ai.embedding.Embedding> items = new java.util.ArrayList<>();
            int i = 0;
            for (String t : request.getInstructions()) {
                items.add(new org.springframework.ai.embedding.Embedding(embed(t), i++));
            }
            return new EmbeddingResponse(items);
        }

        @Override
        public float[] embed(org.springframework.ai.document.Document document) {
            return embed(document.getText());
        }
    }
}
