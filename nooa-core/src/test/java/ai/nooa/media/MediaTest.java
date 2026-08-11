package ai.nooa.media;

import org.junit.jupiter.api.*;

import java.nio.file.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Media Types")
class MediaTest {

    @Test
    @DisplayName("Image from bytes produces valid data URL")
    void imageFromBytes() {
        byte[] data = new byte[]{0x01, 0x02, 0x03};
        var img = Media.imageFromBytes(data, "image/png");
        assertThat(img.dataUrl()).startsWith("data:image/png;base64,");
        assertThat(img.mimeType()).isEqualTo("image/png");
        assertThat(img.sizeBytes()).isGreaterThan(0);
        assertThat(img.contentHash()).isNotEmpty();
    }

    @Test
    @DisplayName("Image from file reads and encodes")
    void imageFromFile() throws Exception {
        Path tmp = Files.createTempFile("test", ".png");
        Files.write(tmp, new byte[]{0x01, 0x02, 0x03, 0x04});
        try {
            var img = Media.imageFromFile(tmp);
            assertThat(img.dataUrl()).contains("base64,");
            assertThat(img.mimeType()).isEqualTo("image/png");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    @DisplayName("File type with custom name")
    void fileWithName() {
        var file = new Media.File("data:text/plain;base64,SGVsbG8=",
            "text/plain", "readme.txt");
        assertThat(file.fileName()).isEqualTo("readme.txt");
        assertThat(file.mimeType()).isEqualTo("text/plain");
    }

    @Test
    @DisplayName("contentHash is deterministic")
    void contentHashDeterministic() {
        byte[] data = "hello".getBytes();
        var a = Media.imageFromBytes(data, "image/png");
        var b = Media.imageFromBytes(data, "image/png");
        assertThat(a.contentHash()).isEqualTo(b.contentHash());
    }

    @Test
    @DisplayName("toString renders type and size")
    void toStringRenders() {
        var img = Media.imageFromBytes(new byte[100], "image/jpeg");
        assertThat(img.toString()).contains("Image").contains("bytes");
    }
}
