package io.jobrunr.docsmcp.index;

import io.jobrunr.docsmcp.model.DocsCatalog;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DocsRegistry {

    private volatile DocsCatalog catalog;
    private volatile Map<String, DocsCatalog.Page> byPath = Map.of();

    public void set(DocsCatalog catalog) {
        this.catalog = catalog;
        Map<String, DocsCatalog.Page> next = new LinkedHashMap<>();
        for (DocsCatalog.Page p : catalog.pages()) next.put(p.path(), p);
        this.byPath = Map.copyOf(next);
    }

    public Optional<DocsCatalog.Page> find(String path) {
        if (path == null) return Optional.empty();
        return Optional.ofNullable(byPath.get(path));
    }

    public List<DocsCatalog.Page> pages() {
        return catalog == null ? List.of() : catalog.pages();
    }

    public boolean isReady() {
        return catalog != null && !catalog.pages().isEmpty();
    }
}
