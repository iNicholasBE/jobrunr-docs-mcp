package io.jobrunr.docsmcp.logging;

public final class RequestContextHolder {

    public record RequestContext(String clientIp, String userAgent) {}

    private static final ThreadLocal<RequestContext> CURRENT = new ThreadLocal<>();

    private RequestContextHolder() {}

    public static void set(RequestContext ctx) {
        CURRENT.set(ctx);
    }

    public static RequestContext peek() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
