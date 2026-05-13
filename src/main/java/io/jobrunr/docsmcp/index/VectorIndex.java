package io.jobrunr.docsmcp.index;

import io.jobrunr.docsmcp.model.DocsCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class VectorIndex {

    private static final Logger log = LoggerFactory.getLogger(VectorIndex.class);

    private final EmbeddingModel embeddingModel;
    private volatile List<Entry> entries = List.of();

    public VectorIndex(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public void rebuild(DocsCatalog catalog) {
        List<Entry> next = new ArrayList<>();
        long t0 = System.nanoTime();
        for (DocsCatalog.Page page : catalog.pages()) {
            for (DocsCatalog.Chunk chunk : page.chunks()) {
                String text = (chunk.heading() == null ? "" : chunk.heading() + "\n") +
                        (chunk.text() == null ? "" : chunk.text());
                if (text.isBlank()) continue;
                float[] vec = embeddingModel.embed(text);
                normalize(vec);
                next.add(new Entry(
                        chunk.id(),
                        page.path(),
                        page.title(),
                        page.url(),
                        page.section() == null ? "" : page.section(),
                        page.tier() == null ? "oss" : page.tier(),
                        chunk.heading() == null ? page.title() : chunk.heading(),
                        chunk.text() == null ? "" : chunk.text(),
                        vec));
            }
        }
        this.entries = List.copyOf(next);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        log.info("VectorIndex rebuilt: {} chunks in {} ms", next.size(), ms);
    }

    public List<Result> search(String query, int limit) {
        if (entries.isEmpty() || query == null || query.isBlank()) return List.of();
        float[] q = embeddingModel.embed(query);
        normalize(q);
        return entries.stream()
                .map(e -> new Result(
                        e.chunkId, e.path, e.title, e.url, e.section, e.tier,
                        e.heading, e.text, cosine(q, e.vec)))
                .sorted(Comparator.comparingDouble(Result::score).reversed())
                .limit(Math.max(limit, 1))
                .toList();
    }

    public boolean isReady() {
        return !entries.isEmpty();
    }

    private static void normalize(float[] v) {
        double n = 0;
        for (float x : v) n += x * x;
        n = Math.sqrt(n);
        if (n == 0) return;
        for (int i = 0; i < v.length; i++) v[i] = (float) (v[i] / n);
    }

    private static double cosine(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        double s = 0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    private record Entry(
            String chunkId,
            String path,
            String title,
            String url,
            String section,
            String tier,
            String heading,
            String text,
            float[] vec) {
    }

    public record Result(
            String chunkId,
            String path,
            String title,
            String url,
            String section,
            String tier,
            String heading,
            String text,
            double score) {
    }
}
