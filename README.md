# jobrunr-docs-mcp

Hosted [Model Context Protocol](https://modelcontextprotocol.io) server that exposes [JobRunr](https://www.jobrunr.io) documentation to AI coding assistants (Claude Code, Cursor, VS Code, Windsurf, ChatGPT desktop, ...).

Add one URL to your IDE config and JobRunr docs are live in every conversation — your agent stops hallucinating APIs and starts citing real `jobrunr.io` pages.

**Live endpoint:** `https://jobrunr-docs-mcp.fly.dev` (until DNS for `mcp.jobrunr.io` is wired)

Built on Spring Boot 3.3 + Spring AI's MCP server starter + Apache Lucene (BM25) + a local ONNX MiniLM embedding model. Retrieval is hybrid: BM25 and semantic search combined via Reciprocal Rank Fusion.

## Tools

| Tool | Purpose |
|---|---|
| `search_jobrunr_docs(query, limit?)` | Hybrid search. Returns ranked pages with title, URL, section, tier (`oss` or `pro`), and a highlighted snippet. |
| `fetch_jobrunr_doc(path)` | Full markdown for a single page. Use the `path` field from `search_jobrunr_docs` results. |
| `list_jobrunr_doc_sections()` | Grouped TOC. Useful for browsing before searching. |

## Use it

### Claude Code

```
claude mcp add --transport sse jobrunr-docs https://jobrunr-docs-mcp.fly.dev/sse
```

Restart Claude Code, then ask about anything JobRunr-related. Run `/mcp` inside Claude Code to confirm the connection.

### Cursor

Add to `~/.cursor/mcp.json`:

```json
{
  "mcpServers": {
    "jobrunr-docs": {
      "url": "https://jobrunr-docs-mcp.fly.dev/sse"
    }
  }
}
```

Restart Cursor. Tools appear under Settings → MCP.

### VS Code / Windsurf / others

Use the same SSE URL — `https://jobrunr-docs-mcp.fly.dev/sse` — with whatever MCP config the client expects. Most accept a one-line `url` entry.

### MCP Inspector (debugging)

```
npx @modelcontextprotocol/inspector
```

In the browser UI: transport = **SSE**, URL = **`https://jobrunr-docs-mcp.fly.dev/sse`**, click Connect.

### Raw curl

```bash
curl https://jobrunr-docs-mcp.fly.dev/actuator/health
# {"status":"UP",...}
```

For a full JSON-RPC roundtrip, see the SSE handshake convention in `docs/protocol.md` (TODO) or the `mcp-inspector` HTTP traces.

## Local development

```
mvn spring-boot:run
```

Defaults to polling `https://www.jobrunr.io/mcp/docs.json` (once the Hugo workflow lands), falling back to the bootstrap URL configured in `application.yml`. Override either via env:

```
DOCS_URL=file:///absolute/path/to/docs.json \
DOCS_MANIFEST_URL=file:///absolute/path/to/manifest.json \
mvn spring-boot:run
```

`file://` URLs are supported for quick local iteration without an HTTP server.

Run tests:

```
mvn test
```

Tests use the bundled `src/test/resources/sample-docs.json` so they don't depend on the network. A deterministic stub embedding model is used to keep the suite fast and offline.

## Architecture

```
   Hugo build (JobRunr website repo)
   ──► public/mcp/docs.json + manifest.json
       │
       ▼
   GitHub Pages: https://www.jobrunr.io/mcp/...
       │
       ▼   (15-min poll + HMAC-signed /admin/reindex webhook)
   Spring Boot app (this repo)
       │
       ├── LuceneIndex          BM25, in-memory RAMDirectory
       ├── VectorIndex          Spring AI TransformersEmbeddingModel (ONNX MiniLM)
       ├── HybridSearch         Reciprocal Rank Fusion (k=60)
       └── DocsTools            @Tool methods → MCP tools/call
            │
            ▼
   Streamable HTTP / SSE  ──►  Claude Code / Cursor / ...
```

The server **never generates answers** — it surfaces relevant pages and lets the client LLM synthesize. Stateless apart from the in-memory indexes.

## Operations

| Endpoint | Purpose |
|---|---|
| `GET /sse` | MCP Streamable HTTP / SSE entry |
| `POST /mcp/message?sessionId=…` | JSON-RPC messages (advertised over SSE) |
| `GET /actuator/health` | Liveness + readiness |
| `POST /admin/reindex` | Force reload of `docs.json` (HMAC headers required) |

`/admin/reindex` requires two headers:

```
X-Reindex-Timestamp: <unix seconds>
X-Reindex-Signature: <hex hmac-sha256 of the timestamp, keyed with MCP_REINDEX_SECRET>
```

The JobRunr website's GitHub Actions deploy step builds these automatically (see `.github/workflows/hugo.yml` in that repo).

## Configuration

| Env var | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port |
| `DOCS_URL` | website `docs.json` | Where to fetch the catalog (HTTP or `file://`) |
| `DOCS_MANIFEST_URL` | website `manifest.json` | Where to fetch the manifest (used to detect changes) |
| `DOCS_POLL_INTERVAL` | `PT15M` | ISO-8601 duration for the scheduled poll |
| `MCP_REINDEX_SECRET` | _(empty)_ | HMAC secret for `/admin/reindex`. If empty, the endpoint returns 503. |

## Things still to do before this is "production"

These are deliberate shortcuts taken to get the server live for testing. Track these before opening it up.

1. **Flip `DOCS_URL` back to the canonical website path.** Currently bootstrapped to
   `raw.githubusercontent.com/iNicholasBE/jobrunr-docs-mcp/main/data/docs.json` because the Hugo workflow that publishes
   `https://www.jobrunr.io/mcp/docs.json` hasn't been merged/deployed yet. Once the JobRunr website repo ships those
   artifacts, edit `fly.toml`'s `[env]` block to point at `https://www.jobrunr.io/mcp/docs.json` and
   `https://www.jobrunr.io/mcp/manifest.json`, then `flyctl deploy`. Delete `data/docs.json` and `data/manifest.json`
   from this repo afterwards.
2. **Wire up `MCP_REINDEX_SECRET`.** Generate a long random value and set it on both sides:
   - `flyctl secrets set MCP_REINDEX_SECRET=<value> -a jobrunr-docs-mcp`
   - GitHub repo `jobrunr/jobrunr.io` (or wherever the website lives) → Settings → Secrets → `MCP_REINDEX_SECRET = <same value>`
   - The Hugo deploy workflow will then push fresh docs to the server immediately on each website deploy, instead of
     waiting up to 15 minutes for the scheduled poll.
3. **DNS for `mcp.jobrunr.io`.** Add a Cloudflare CNAME `mcp` → `jobrunr-docs-mcp.fly.dev` (proxied = orange cloud for
   caching + DDoS), then in the Fly dashboard add `mcp.jobrunr.io` as a certificate. Update all docs/install instructions
   to use the friendlier URL.
4. **GitHub Actions auto-deploy.** Add a `FLY_API_TOKEN` repo secret (generate at fly.io/user/personal_access_tokens),
   then every push to `main` will run tests and deploy via `.github/workflows/deploy.yml`.
5. **Shrink the Docker image and drop VM size.** Current image is ~240 MB because Spring AI's `TransformersEmbeddingModel`
   pulls in DJL + a 150 MB libtorch native lib at first run, which is why the VM is 2 GB instead of the planned 512 MB.
   Options:
   - Pre-download the ONNX model + tokenizer into the image at build time (in the `Dockerfile`) so cold starts don't
     fetch them. Saves 30-60 s of startup. Still uses libtorch.
   - Switch to a direct ONNX-Runtime-only embedding pipeline (skip DJL/PyTorch). Drops the image by ~150 MB and the VM
     could likely go back to 512 MB. Means writing a small `EmbeddingModel` implementation backed by
     `com.microsoft.onnxruntime`.
6. **Move the Fly app from `personal` org to a `jobrunr` org.** Currently sits in Nicholas's personal Fly org. Migrate
   when JobRunr's Fly billing is set up. `flyctl apps move jobrunr-docs-mcp --new-org jobrunr`.
7. **Lower the WebClient body buffer.** Bumped to 16 MB in `DocsLoader` because 256 KB default choked on the 330 KB
   `docs.json`. 1-2 MB would be plenty and bounds memory better. Cosmetic.
8. **Pre-build a `VectorIndex` snapshot.** Embedding 301 chunks at startup takes ~30 s. Persisting the vectors to disk
   (volume or baked into the image alongside the model) would make warm restarts near-instant. Only worth doing if cold
   starts become a UX problem.
9. **Telemetry.** No query logging today. Once usage is non-trivial, add a simple `OncePerRequestFilter` that emits
   `tool_name`, hashed IP, query string, top-result path → wherever metrics live (Cloudflare Analytics Engine, Loki,
   ClickHouse, ...). Useful for understanding what devs actually ask before deciding what to improve.
10. **Marketing surface (separate sprint).** Per the original plan: `/en/mcp/` install page on jobrunr.io with copy-paste
    configs for every IDE, sidebar CTAs on every documentation page, launch blog post. Deferred until the server has been
    validated in production for a couple of weeks.

## Deploying to a different environment

If you want to run this somewhere other than the current Fly app:

1. Provision a host that can run a JVM container (Fly.io / Hetzner / DigitalOcean / Cloud Run / anywhere with Docker).
2. Build and push the image: `flyctl deploy` for Fly, or `docker build -t … . && docker push …` for anything else.
3. Set the env vars from the table above. At minimum: `DOCS_URL`, `DOCS_MANIFEST_URL`. Generate and set `MCP_REINDEX_SECRET`.
4. Make sure the host has enough RAM. Currently **2 GB** is the safe minimum because of the libtorch native lib (see TODO #5). 512 MB will OOM during startup; 1 GB is borderline.
5. Open ports 80/443 (or whatever your TLS proxy expects) and route to container port 8080.
6. Point a DNS name at it and add TLS.
7. Smoke test: `curl https://<host>/actuator/health` then a full MCP roundtrip via `mcp-inspector`.

The app is fully stateless and idempotent, so blue/green or rolling deploys work fine. Indexes rebuild from the polled
`docs.json` within a minute of startup.
