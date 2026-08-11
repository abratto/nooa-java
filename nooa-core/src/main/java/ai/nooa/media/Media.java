package ai.nooa.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Base class for media attachments passed to multimodal LLMs.
 */
public sealed class Media permits Media.Image, Media.Audio, Media.Video, Media.File {

    private final String dataUrl;
    private final String mimeType;
    private final String contentHash;
    private final long sizeBytes;

    protected Media(String dataUrl, String mimeType) {
        this.dataUrl = dataUrl;
        this.mimeType = mimeType;
        this.sizeBytes = estimateSize(dataUrl);
        this.contentHash = computeHash(dataUrl);
    }

    public String dataUrl() { return dataUrl; }
    public String mimeType() { return mimeType; }
    public String contentHash() { return contentHash; }
    public long sizeBytes() { return sizeBytes; }

    /** Create from a local file path. */
    public static Image imageFromFile(Path path) throws IOException {
        String mime = guessMime(path, "image/jpeg");
        return new Image(encodeToDataUrl(path, mime), mime);
    }

    public static Audio audioFromFile(Path path) throws IOException {
        String mime = guessMime(path, "audio/wav");
        return new Audio(encodeToDataUrl(path, mime), mime);
    }

    public static Video videoFromFile(Path path) throws IOException {
        String mime = guessMime(path, "video/mp4");
        return new Video(encodeToDataUrl(path, mime), mime);
    }

    public static File fileFromPath(Path path) throws IOException {
        String mime = guessMime(path, "application/pdf");
        return new File(encodeToDataUrl(path, mime), mime);
    }

    /** Create from raw bytes with explicit MIME type. */
    public static Image imageFromBytes(byte[] data, String mimeType) {
        return new Image(encodeBytes(data, mimeType), mimeType);
    }

    private static String encodeToDataUrl(Path path, String mimeType) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return encodeBytes(bytes, mimeType);
    }

    private static String encodeBytes(byte[] bytes, String mimeType) {
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private static String guessMime(Path path, String fallback) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".mp3")) return "audio/mpeg";
        if (name.endsWith(".wav")) return "audio/wav";
        if (name.endsWith(".ogg")) return "audio/ogg";
        if (name.endsWith(".mp4")) return "video/mp4";
        if (name.endsWith(".webm")) return "video/webm";
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".txt")) return "text/plain";
        return fallback;
    }

    private static long estimateSize(String dataUrl) {
        int idx = dataUrl.indexOf("base64,");
        if (idx < 0) return dataUrl.length();
        return Math.round((dataUrl.length() - idx - 7) * 0.75);
    }

    private static String computeHash(String dataUrl) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(dataUrl.getBytes()));
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + mimeType + ", " + sizeBytes + " bytes)";
    }

    // ---- Subtypes ----

    public static final class Image extends Media {
        public Image(String dataUrl) { super(dataUrl, "image/jpeg"); }
        public Image(String dataUrl, String mimeType) { super(dataUrl, mimeType); }
    }

    public static final class Audio extends Media {
        public Audio(String dataUrl) { super(dataUrl, "audio/wav"); }
        public Audio(String dataUrl, String mimeType) { super(dataUrl, mimeType); }
    }

    public static final class Video extends Media {
        public Video(String dataUrl) { super(dataUrl, "video/mp4"); }
        public Video(String dataUrl, String mimeType) { super(dataUrl, mimeType); }
    }

    public static final class File extends Media {
        private final String fileName;
        public File(String dataUrl) { this(dataUrl, "application/pdf", "file"); }
        public File(String dataUrl, String mimeType) { this(dataUrl, mimeType, "file"); }
        public File(String dataUrl, String mimeType, String fileName) {
            super(dataUrl, mimeType);
            this.fileName = fileName;
        }
        public String fileName() { return fileName; }
    }
}
