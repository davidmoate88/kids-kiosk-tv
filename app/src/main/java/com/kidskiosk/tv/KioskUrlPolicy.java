package com.kidskiosk.tv;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Restricts top-level WebView navigation and native messaging to one origin. */
final class KioskUrlPolicy {

    private final URI kioskUri;
    private final int kioskPort;

    KioskUrlPolicy(String kioskUrl) {
        kioskUri = parse(kioskUrl);
        if (!isHttpScheme(kioskUri.getScheme())
                || kioskUri.getHost() == null
                || kioskUri.getUserInfo() != null) {
            throw new IllegalArgumentException("Kiosk URL must be an HTTP(S) origin without credentials");
        }
        kioskPort = effectivePort(kioskUri);
    }

    boolean isAllowed(String candidateUrl) {
        final URI candidate;
        try {
            candidate = parse(candidateUrl);
        } catch (IllegalArgumentException ignored) {
            return false;
        }

        return candidate.getUserInfo() == null
                && kioskUri.getScheme().equalsIgnoreCase(candidate.getScheme())
                && kioskUri.getHost().equalsIgnoreCase(candidate.getHost())
                && kioskPort == effectivePort(candidate);
    }

    String originRule() {
        String host = kioskUri.getHost();
        if (host.contains(":")) {
            host = "[" + host + "]";
        }
        int defaultPort = defaultPort(kioskUri.getScheme());
        String portSuffix = kioskPort == defaultPort ? "" : ":" + kioskPort;
        return kioskUri.getScheme().toLowerCase(Locale.ROOT) + "://" + host + portSuffix;
    }

    private static URI parse(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException | NullPointerException error) {
            throw new IllegalArgumentException("Invalid kiosk URL", error);
        }
    }

    private static boolean isHttpScheme(String scheme) {
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : defaultPort(uri.getScheme());
    }

    private static int defaultPort(String scheme) {
        if ("http".equalsIgnoreCase(scheme)) return 80;
        if ("https".equalsIgnoreCase(scheme)) return 443;
        return -1;
    }
}
