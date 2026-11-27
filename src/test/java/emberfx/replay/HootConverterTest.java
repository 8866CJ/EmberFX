package emberfx.replay;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HootConverterTest {
    @TempDir
    Path dir;

    @Test
    void parsesNamesAndProgress() {
        assertEquals(19, HootConverter.compliancyFromName("owlet-26.3.0-C19.exe"));
        assertEquals(6, HootConverter.compliancyFromName("owlet-1.0.1.1-C6.exe"));
        assertEquals(-1, HootConverter.compliancyFromName("owlet.exe"));
        assertTrue(HootConverter.versionKey(Path.of("owlet-26.70.0-C21.exe"))
            .compareTo(HootConverter.versionKey(Path.of("owlet-26.3.0-C19.exe"))) > 0);
        assertTrue(HootConverter.versionKey(Path.of("owlet-25.90.2-C20.exe"))
            .compareTo(HootConverter.versionKey(Path.of("owlet-26.0.0-C18.exe"))) < 0);
        assertEquals("", HootConverter.versionKey(Path.of("owlet.exe")));
        assertEquals(42, HootConverter.progressPercent("Progress: [ 42%] [#####.....]"));
        assertEquals(100, HootConverter.progressPercent("Progress: [100%] [###]"));
        assertEquals(-1, HootConverter.progressPercent("Could not read to end of input file"));
    }

    @Test
    void explicitOwletComesFirst() throws IOException {
        Path mine = Files.createFile(dir.resolve("owlet-custom.exe"));
        List<Path> found = HootConverter.findOwlets(mine);
        assertEquals(mine.toAbsolutePath(), found.get(0));
        assertFalse(HootConverter.findOwlets(dir.resolve("missing.exe")).contains(dir.resolve("missing.exe")));
    }

    @Test
    void usesTheCacheAndExplainsMissingOwlet() throws Exception {
        Path hoot = Files.writeString(dir.resolve("match.hoot"), "x");
        Path cache = dir.resolve("cache");
        IOException e = assertThrows(IOException.class, () -> HootConverter.toWpilog(hoot, List.of(), p -> {}, cache));
        assertTrue(e.getMessage().contains("AdvantageScope") && e.getMessage().contains("Tuner X"), e.getMessage());

        Files.createDirectories(cache);
        Path cached = cache.resolve("match_" + Files.size(hoot) + "_" + Files.getLastModifiedTime(hoot).toMillis() + ".wpilog");
        Files.writeString(cached, "converted");
        assertEquals(cached, HootConverter.toWpilog(hoot, List.of(), p -> {}, cache), "no owlet needed when cached");
    }
}
