package io.jobrunr.docsmcp.logging;

import io.jobrunr.docsmcp.web.ClientIpExtractor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestContextWebFilter implements WebFilter {

    static final String CONTEXT_KEY = "io.jobrunr.docsmcp.requestContext";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String ip = ClientIpExtractor.from(exchange.getRequest());
        String ua = exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT);
        RequestContextHolder.RequestContext ctx = new RequestContextHolder.RequestContext(ip, ua);

        RequestContextHolder.set(ctx);
        return chain.filter(exchange)
                .contextWrite(reactor.util.context.Context.of(CONTEXT_KEY, ctx))
                .doFinally(s -> RequestContextHolder.clear());
    }
}
