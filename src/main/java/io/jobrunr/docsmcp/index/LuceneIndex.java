package io.jobrunr.docsmcp.index;

import io.jobrunr.docsmcp.model.DocsCatalog;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class LuceneIndex {

    private static final Logger log = LoggerFactory.getLogger(LuceneIndex.class);
    private static final String[] QUERY_FIELDS = {"title", "heading", "text", "keywords"};
    private static final float[] FIELD_BOOSTS = {4.0f, 2.5f, 1.0f, 2.0f};
    private static final int SNIPPET_CHARS = 300;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile Directory directory;

    public void rebuild(DocsCatalog catalog) {
        Directory next = new ByteBuffersDirectory();
        IndexWriterConfig cfg = new IndexWriterConfig(new StandardAnalyzer());
        cfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        int docCount = 0;
        try (IndexWriter writer = new IndexWriter(next, cfg)) {
            for (DocsCatalog.Page page : catalog.pages()) {
                String keywordsBlob = page.keywords() == null ? "" : String.join(" ", page.keywords());
                for (DocsCatalog.Chunk chunk : page.chunks()) {
                    Document doc = new Document();
                    doc.add(new StringField("chunkId", chunk.id(), Field.Store.YES));
                    doc.add(new StringField("path", page.path(), Field.Store.YES));
                    doc.add(new StringField("section", page.section() == null ? "" : page.section(), Field.Store.YES));
                    doc.add(new StringField("tier", page.tier() == null ? "oss" : page.tier(), Field.Store.YES));
                    doc.add(new StoredField("url", page.url() == null ? "" : page.url()));
                    doc.add(new TextField("title", page.title() == null ? "" : page.title(), Field.Store.YES));
                    doc.add(new TextField("heading", chunk.heading() == null ? "" : chunk.heading(), Field.Store.YES));
                    doc.add(new TextField("text", chunk.text() == null ? "" : chunk.text(), Field.Store.YES));
                    doc.add(new TextField("keywords", keywordsBlob, Field.Store.NO));
                    writer.addDocument(doc);
                    docCount++;
                }
            }
            writer.commit();
        } catch (Exception e) {
            try { next.close(); } catch (Exception ignored) { }
            throw new IllegalStateException("Failed to build Lucene index", e);
        }
        lock.writeLock().lock();
        try {
            Directory old = this.directory;
            this.directory = next;
            if (old != null) {
                try { old.close(); } catch (Exception ignored) { }
            }
        } finally {
            lock.writeLock().unlock();
        }
        log.info("LuceneIndex rebuilt: {} chunks across {} pages", docCount, catalog.pages().size());
    }

    public List<Result> search(String queryText, int limit) {
        Directory dir = this.directory;
        if (dir == null || queryText == null || queryText.isBlank()) return List.of();
        try (DirectoryReader reader = DirectoryReader.open(dir)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            StandardAnalyzer analyzer = new StandardAnalyzer();
            MultiFieldQueryParser parser = new MultiFieldQueryParser(QUERY_FIELDS, analyzer, boostMap());
            parser.setDefaultOperator(QueryParser.Operator.OR);
            Query query;
            try {
                query = parser.parse(QueryParser.escape(queryText));
            } catch (Exception e) {
                return List.of();
            }
            TopDocs hits = searcher.search(query, Math.max(limit, 1));
            String[] queryTerms = tokenize(queryText);

            List<Result> results = new ArrayList<>(hits.scoreDocs.length);
            for (ScoreDoc sd : hits.scoreDocs) {
                Document doc = searcher.storedFields().document(sd.doc);
                String text = doc.get("text");
                String snippet = buildSnippet(text, queryTerms);
                results.add(new Result(
                        doc.get("chunkId"),
                        doc.get("path"),
                        doc.get("title"),
                        doc.get("url"),
                        doc.get("section"),
                        doc.get("tier"),
                        doc.get("heading"),
                        snippet,
                        sd.score));
            }
            return results;
        } catch (Exception e) {
            log.warn("LuceneIndex search failed: {}", e.toString());
            return List.of();
        }
    }

    public boolean isReady() {
        return directory != null;
    }

    private static String[] tokenize(String query) {
        String[] raw = query.toLowerCase().split("[^a-z0-9]+");
        List<String> kept = new ArrayList<>();
        for (String t : raw) if (t.length() >= 2) kept.add(t);
        return kept.toArray(new String[0]);
    }

    static String buildSnippet(String text, String[] terms) {
        if (text == null || text.isBlank()) return "";
        if (terms.length == 0) {
            return text.length() <= SNIPPET_CHARS ? text.trim() : text.substring(0, SNIPPET_CHARS).trim() + "…";
        }
        String lower = text.toLowerCase();
        int hit = -1;
        for (String t : terms) {
            int idx = lower.indexOf(t);
            if (idx >= 0 && (hit == -1 || idx < hit)) hit = idx;
        }
        int start;
        int end;
        if (hit == -1) {
            start = 0;
            end = Math.min(text.length(), SNIPPET_CHARS);
        } else {
            start = Math.max(0, hit - 60);
            end = Math.min(text.length(), start + SNIPPET_CHARS);
        }
        String window = text.substring(start, end).trim();
        String prefix = start > 0 ? "…" : "";
        String suffix = end < text.length() ? "…" : "";
        return prefix + highlight(window, terms) + suffix;
    }

    private static String highlight(String window, String[] terms) {
        String result = window;
        for (String t : terms) {
            if (t.isBlank()) continue;
            String pattern = "(?i)\\b" + java.util.regex.Pattern.quote(t) + "\\b";
            result = result.replaceAll(pattern, "**$0**");
        }
        return result;
    }

    private static java.util.Map<String, Float> boostMap() {
        java.util.Map<String, Float> boosts = new java.util.HashMap<>();
        for (int i = 0; i < QUERY_FIELDS.length; i++) boosts.put(QUERY_FIELDS[i], FIELD_BOOSTS[i]);
        return boosts;
    }

    public record Result(
            String chunkId,
            String path,
            String title,
            String url,
            String section,
            String tier,
            String heading,
            String snippet,
            double score) {
    }
}
