package io.jobrunr.docsmcp.index;

import io.jobrunr.docsmcp.model.SearchHit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Component
public class HybridSearch {

    private final LuceneIndex lucene;
    private final VectorIndex vector;
    private final int rrfK;
    private final int candidatePool;

    public HybridSearch(
            LuceneIndex lucene,
            VectorIndex vector,
            @Value("${search.hybrid.rrf-k:60}") int rrfK,
            @Value("${search.hybrid.candidate-pool:50}") int candidatePool) {
        this.lucene = lucene;
        this.vector = vector;
        this.rrfK = rrfK;
        this.candidatePool = candidatePool;
    }

    public List<SearchHit> search(String query, int limit) {
        CompletableFuture<List<LuceneIndex.Result>> luceneF =
                CompletableFuture.supplyAsync(() -> lucene.search(query, candidatePool));
        CompletableFuture<List<VectorIndex.Result>> vectorF =
                CompletableFuture.supplyAsync(() -> vector.search(query, candidatePool));

        List<LuceneIndex.Result> luceneHits = luceneF.join();
        List<VectorIndex.Result> vectorHits = vectorF.join();

        Map<String, Integer> bestLuceneRank = new HashMap<>();
        Map<String, Integer> bestVectorRank = new HashMap<>();
        Map<String, PageMeta> meta = new HashMap<>();
        Map<String, String> snippetByPath = new HashMap<>();

        for (int i = 0; i < luceneHits.size(); i++) {
            LuceneIndex.Result r = luceneHits.get(i);
            bestLuceneRank.putIfAbsent(r.path(), i);
            meta.putIfAbsent(r.path(),
                    new PageMeta(r.path(), r.title(), r.url(), r.section(), r.tier()));
            snippetByPath.putIfAbsent(r.path(), r.snippet());
        }
        for (int i = 0; i < vectorHits.size(); i++) {
            VectorIndex.Result r = vectorHits.get(i);
            bestVectorRank.putIfAbsent(r.path(), i);
            meta.putIfAbsent(r.path(),
                    new PageMeta(r.path(), r.title(), r.url(), r.section(), r.tier()));
            snippetByPath.putIfAbsent(r.path(), shorten(r.text()));
        }

        Set<String> allPaths = new HashSet<>();
        allPaths.addAll(bestLuceneRank.keySet());
        allPaths.addAll(bestVectorRank.keySet());

        List<SearchHit> ranked = new ArrayList<>();
        for (String path : allPaths) {
            double score = 0.0;
            Integer lr = bestLuceneRank.get(path);
            if (lr != null) score += 1.0 / (rrfK + lr + 1);
            Integer vr = bestVectorRank.get(path);
            if (vr != null) score += 1.0 / (rrfK + vr + 1);
            PageMeta m = meta.get(path);
            ranked.add(new SearchHit(
                    m.path, m.title, m.url, m.section, m.tier,
                    snippetByPath.getOrDefault(path, ""),
                    score));
        }
        ranked.sort(Comparator.comparingDouble(SearchHit::score).reversed());
        return ranked.size() > limit ? ranked.subList(0, Math.max(limit, 0)) : ranked;
    }

    private static String shorten(String text) {
        if (text == null) return "";
        return text.length() <= 300 ? text : text.substring(0, 300).trim() + "…";
    }

    private record PageMeta(String path, String title, String url, String section, String tier) {}
}
