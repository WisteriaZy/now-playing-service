package com.widdit.nowplaying.util;

/** Detect supported raster formats from bytes: lazer files have no extension/MIME header. */
public final class TosuArtwork {
    public static final String RESOURCE_PATH = "/api/cover/tosu/current";
    private TosuArtwork() { }

    /** Stable per audio/background selection, available before the image has finished loading. */
    public static String reference(String key) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return RESOURCE_PATH + "?v=" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static boolean isReference(String value) {
        return value != null && (value.equals(RESOURCE_PATH) || value.startsWith(RESOURCE_PATH + "?v="));
    }

    public static String detectMime(byte[] data) {
        if (data == null || data.length < 12) return null;
        if (matches(data, 0, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) return "image/png";
        if (matches(data, 0, 0xff, 0xd8, 0xff)) return "image/jpeg";
        if (matches(data, 0, 0x47, 0x49, 0x46, 0x38, 0x37, 0x61)
                || matches(data, 0, 0x47, 0x49, 0x46, 0x38, 0x39, 0x61)) return "image/gif";
        if (matches(data, 0, 0x52, 0x49, 0x46, 0x46) && matches(data, 8, 0x57, 0x45, 0x42, 0x50)) return "image/webp";
        return null;
    }

    private static boolean matches(byte[] data, int offset, int... signature) {
        if (offset + signature.length > data.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((data[offset + i] & 0xff) != signature[i]) return false;
        }
        return true;
    }
}
