package io.jobrunr.docsmcp.admin;

import io.jobrunr.docsmcp.index.DocsLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/admin")
public class ReindexController {

    private static final Logger log = LoggerFactory.getLogger(ReindexController.class);
    private static final long CLOCK_SKEW_SECONDS = 300;

    private final DocsLoader loader;
    private final String secret;

    public ReindexController(DocsLoader loader, @Value("${docs.reindex-secret:}") String secret) {
        this.loader = loader;
        this.secret = secret == null ? "" : secret;
    }

    @PostMapping("/reindex")
    public ResponseEntity<String> reindex(
            @RequestHeader(value = "X-Reindex-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Reindex-Signature", required = false) String signature) {
        if (secret.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("reindex secret not configured");
        }
        if (timestamp == null || signature == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("missing signature headers");
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("bad timestamp");
        }
        long now = System.currentTimeMillis() / 1000;
        if (Math.abs(now - ts) > CLOCK_SKEW_SECONDS) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("timestamp out of window");
        }
        String expected = hmacHex(timestamp, secret);
        if (!constantTimeEquals(expected, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("bad signature");
        }
        try {
            loader.reload(true);
        } catch (Exception e) {
            log.warn("Manual reindex failed: {}", e.toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("reindex failed");
        }
        return ResponseEntity.ok("ok");
    }

    private static String hmacHex(String message, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(ab, bb);
    }
}
