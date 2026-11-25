package emberfx.autos;

import emberfx.autos.AutoPreview.Pose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoLibraryTest {
    @TempDir Path project;

    private AutoLibrary library() throws IOException {
        Path src = Path.of("src", "test", "resources", "emberfx", "autos");
        Path dst = project.resolve("src").resolve("main").resolve("deploy");
        try (Stream<Path> s = Files.walk(src)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path t = dst.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(t);
                else Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return new AutoLibrary(project);
    }

    @Test
    void namesSkipBrokenAndOldFiles() throws IOException {
        assertEquals(List.of("Two Leg", "Line"), library().autoNames());
    }

    @Test
    void pathPlannerAuto() throws IOException {
        AutoPreview p = library().preview("Two Leg").orElseThrow();
        assertEquals(2, p.segments().size()); // Leg1 twice, Missing skipped
        List<Pose> seg = p.segments().get(0);
        assertTrue(seg.size() > 40 && seg.size() < 100, "points: " + seg.size()); // 4 m at ~7 cm
        assertEquals(2.0, seg.get(0).x(), 1e-9);
        assertEquals(6.0, seg.get(seg.size() - 1).x(), 1e-9);
        assertEquals(4.0, seg.get(seg.size() - 1).y(), 1e-9);
        assertEquals(90.0, seg.get(seg.size() - 1).headingDeg(), 1e-9);
        assertEquals(0.0, p.start().headingDeg(), 1e-9);
        assertEquals(2.0, p.start().x(), 1e-9);
        assertEquals("Intake", p.markers().get(0).name());
        assertEquals(4.0, p.markers().get(0).point().x(), 1e-6); // halfway along a straight 2..6
        assertEquals(45.0, p.markers().get(0).point().headingDeg(), 1e-6);
    }

    @Test
    void choreoTrajectory() throws IOException {
        AutoPreview p = library().preview("Line").orElseThrow();
        assertEquals(1, p.segments().size());
        List<Pose> seg = p.segments().get(0);
        assertEquals(3, seg.size());
        assertEquals(1.0, p.start().x(), 1e-9);
        assertEquals(90.0, seg.get(1).headingDeg(), 1e-6);
        assertEquals(180.0, seg.get(2).headingDeg(), 1e-6);
        assertEquals("Score", p.markers().get(0).name());
        assertEquals(2.0, p.markers().get(0).point().x(), 1e-9);
    }

    @Test
    void flipping() throws IOException {
        AutoPreview p = library().preview("Line").orElseThrow();
        AutoPreview r = p.flipped(FieldSymmetry.ROTATIONAL, 16.5, 8.0);
        assertEquals(15.5, r.start().x(), 1e-9);
        assertEquals(6.0, r.start().y(), 1e-9);
        assertEquals(180.0, r.start().headingDeg(), 1e-9);
        AutoPreview m = p.flipped(FieldSymmetry.MIRRORED, 16.5, 8.0);
        assertEquals(15.5, m.start().x(), 1e-9);
        assertEquals(2.0, m.start().y(), 1e-9);
        assertEquals(180.0, m.start().headingDeg(), 1e-9);
        assertEquals(90.0, m.segments().get(0).get(1).headingDeg(), 1e-6); // 180 - 90
        assertEquals(-90.0, r.segments().get(0).get(1).headingDeg(), 1e-6); // 90 + 180 wraps
    }

    @Test
    void refreshPicksUpChanges() throws IOException {
        AutoLibrary lib = library();
        Path f = project.resolve("src/main/deploy/pathplanner/paths/Leg1.path");
        AutoPreview before = lib.preview("Two Leg").orElseThrow();
        Files.writeString(f, Files.readString(f).replace("\"x\": 6.0", "\"x\": 8.0"));
        f.toFile().setLastModified(System.currentTimeMillis() + 5000);
        lib.refresh();
        AutoPreview after = lib.preview("Two Leg").orElseThrow();
        assertEquals(6.0, before.segments().get(0).get(before.segments().get(0).size() - 1).x(), 1e-9);
        assertEquals(8.0, after.segments().get(0).get(after.segments().get(0).size() - 1).x(), 1e-9);
    }
}
