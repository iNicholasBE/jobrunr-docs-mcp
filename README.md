# jobrunr-docs-mcp

Hosted [Model Context Protocol](https://modelcontextprotocol.io) server that exposes [JobRunr](https://www.jobrunr.io) documentation to AI coding assistants (Claude Code, Cursor, VS Code, Windsurf, ...).

Built on Spring Boot 3.3, Spring AI, Apache Lucene (BM25) and a local ONNX MiniLM embedding model. Retrieval is hybrid: BM25 + semantic search merged via Reciprocal Rank Fusion.

## Tools

- `search_jobrunr_docs(query, limit?)` — hybrid search returning ranked pages with title, URL, section, tier, and a highlighted snippet
- `fetch_jobrunr_doc(path)` — full markdown for a single page
- `list_jobrunr_doc_sections()` — grouped TOC

## Run locally

```
mvn spring-boot:run
```

Default config polls `https://www.jobrunr.io/mcp/docs.json`. Override with `DOCS_URL`.

## Inspect with mcp-inspector

```
npx @modelcontextprotocol/inspector http://localhost:8080
```

## Hosted endpoint

`https://mcp.jobrunr.io` — Streamable HTTP. Add it to Claude Code:

```
claude mcp add --transport http jobrunr https://mcp.jobrunr.io
```

## Deploy

Production runs on Fly.io. See `fly.toml`. The docs source is rebuilt by the [JobRunr website](https://github.com/jobrunr/jobrunr.io) on every Hugo deploy, which then pings `/admin/reindex` with an HMAC-signed timestamp.
