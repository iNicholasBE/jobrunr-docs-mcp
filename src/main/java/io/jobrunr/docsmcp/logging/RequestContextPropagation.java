package io.jobrunr.docsmcp.logging;

import io.micrometer.context.ContextRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;

/**
 * Bridges {@link RequestContextHolder}'s ThreadLocal across Reactor scheduler hops.
 *
 * Without this, the WebFilter sets the ThreadLocal on the netty event-loop thread, but the
 * Spring AI MCP server dispatches tool calls on a different scheduler (typically
 * boundedElastic via McpServerSession.handle), so RequestContextHolder.peek() returns null
 * inside the tool callback and rows land in mcp_query_log with client_ip=null.
 *
 * Registering a ThreadLocalAccessor + enabling automatic context propagation means Reactor
 * will read the request context entry from the request's Reactor Context and re-install the
 * ThreadLocal on whatever thread the tool callback runs on.
 */
@Configuration
public class RequestContextPropagation {

    private static final Logger log = LoggerFactory.getLogger(RequestContextPropagation.class);

    static final String KEY = RequestContextWebFilter.CONTEXT_KEY;

    @PostConstruct
    public void init() {
        ContextRegistry.getInstance().registerThreadLocalAccessor(
                KEY,
                RequestContextHolder::peek,
                RequestContextHolder::set,
                RequestContextHolder::clear);
        Hooks.enableAutomaticContextPropagation();
        log.info("Enabled Reactor automatic context propagation for {}", KEY);
    }
}
