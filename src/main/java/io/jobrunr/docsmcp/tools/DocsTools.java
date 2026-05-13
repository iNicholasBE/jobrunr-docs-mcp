package io.jobrunr.docsmcp.tools;

import io.jobrunr.docsmcp.index.DocsRegistry;
import io.jobrunr.docsmcp.index.HybridSearch;
import io.jobrunr.docsmcp.model.DocsCatalog;
import io.jobrunr.docsmcp.model.SearchHit;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DocsTools {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 25;

    private final HybridSearch hybridSearch;
    private final DocsRegistry registry;

    public DocsTools(HybridSearch hybridSearch, DocsRegistry registry) {
        this.hybridSearch = hybridSearch;
        this.registry = registry;
    }

    @Tool(name = "search_jobrunr_docs",
            description = """
                    Search JobRunr documentation using hybrid retrieval (BM25 + semantic).
                    Returns a ranked list of pages with title, URL, section, tier (oss or pro), and a relevant snippet.
                    Use this to find the right page before calling fetch_jobrunr_doc for the full content.
                    """)
    public SearchResponse searchJobrunrDocs(
            @ToolParam(description = "Natural-language or keyword query. Examples: 'recurring jobs', 'how to retry failed jobs', 'spring boot configuration'.") String query,
            @ToolParam(required = false, description = "Maximum number of results (default 10, max 25).") Integer limit) {
        if (query == null || query.isBlank()) {
            return new SearchResponse(query, List.of());
        }
        int n = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
        List<SearchHit> hits = hybridSearch.search(query, n);
        return new SearchResponse(query, hits);
    }

    @Tool(name = "fetch_jobrunr_doc",
            description = """
                    Fetch the full markdown content of a JobRunr documentation page by its path.
                    Use the 'path' field returned by search_jobrunr_docs (e.g. 'background-methods/enqueueing-jobs').
                    """)
    public DocPage fetchJobrunrDoc(
            @ToolParam(description = "Page path returned by search_jobrunr_docs, e.g. 'background-methods/recurring-jobs'.") String path) {
        if (path == null || path.isBlank() || path.contains("..") || path.startsWith("/")) {
            throw new IllegalArgumentException("Invalid path");
        }
        DocsCatalog.Page page = registry.find(path)
                .orElseThrow(() -> new IllegalArgumentException("Unknown doc path: " + path));
        StringBuilder md = new StringBuilder();
        md.append("# ").append(page.title()).append("\n\n");
        if (page.subtitle() != null && !page.subtitle().isBlank()) {
            md.append("_").append(page.subtitle()).append("_\n\n");
        }
        for (DocsCatalog.Chunk chunk : page.chunks()) {
            if (chunk.heading() != null && !chunk.heading().equals(page.title())) {
                md.append("## ").append(chunk.heading()).append("\n\n");
            }
            md.append(chunk.text()).append("\n\n");
        }
        return new DocPage(page.path(), page.title(), page.url(), page.tier(), md.toString().trim());
    }

    @Tool(name = "list_jobrunr_doc_sections",
            description = """
                    List the top-level sections of JobRunr documentation with the pages in each.
                    Useful to browse what's available before searching.
                    """)
    public List<SectionListing> listJobrunrDocSections() {
        Map<String, SectionListing> grouped = new LinkedHashMap<>();
        for (DocsCatalog.Page page : registry.pages()) {
            String section = page.section() == null ? "introduction" : page.section();
            grouped.computeIfAbsent(section, s -> new SectionListing(s, new java.util.ArrayList<>()))
                    .pages().add(new SectionPage(page.path(), page.title(), page.url(), page.tier()));
        }
        return List.copyOf(grouped.values());
    }

    public record SearchResponse(String query, List<SearchHit> results) {}
    public record DocPage(String path, String title, String url, String tier, String markdown) {}
    public record SectionListing(String section, List<SectionPage> pages) {}
    public record SectionPage(String path, String title, String url, String tier) {}
}
