package emberfx.replay;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import edu.wpi.first.networktables.NetworkTableInstance;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Converts, loads and replays real hoot logs from one match, when they are on this machine
 * (C:\Users\Admin\Downloads\SCCOL_E2, or the folder in EMBERFX_HOOT_DIR). Skipped elsewhere.
 * Prints load time, heap, entries, time span, phases and seek speed.
 */
@Tag("realdata")
class RealHootLogTest {
    @Test
    void convertsLoadsAndReplaysAMatch() throws Exception {
        String env = System.getenv("EMBERFX_HOOT_DIR");
        Path folder = Path.of(env != null ? env : "C:\\Users\\Admin\\Downloads\\SCCOL_E2");
        assumeTrue(Files.isDirectory(folder), "no real hoot logs here");
        List<Path> hoots;
        try (Stream<Path> s = Files.list(folder)) {
            hoots = s.filter(p -> p.toString().endsWith(".hoot")).sorted().toList();
        }
        List<Path> owlets = HootConverter.findOwlets(null);
        assumeTrue(!hoots.isEmpty() && !owlets.isEmpty(), "no hoot files or no owlet");

        List<Path> logs = new ArrayList<>();
        for (Path h : hoots) {
            long t0 = System.nanoTime();
            Path w = HootConverter.toWpilog(h, owlets, p -> {});
            System.out.printf("converted %s -> %s (%d MB) in %d ms%n", h.getFileName(), w.getFileName(),
                Files.size(w) >> 20, (System.nanoTime() - t0) / 1_000_000);
            logs.add(w);
        }
        for (Path w : logs) {
            long l0 = System.nanoTime();
            LogIndex one = LogIndex.load(List.of(w), p -> {});
            System.out.printf("load %d ms (max heap %d MB)%n", (System.nanoTime() - l0) / 1_000_000, Runtime.getRuntime().maxMemory() >> 20);
            System.out.printf("%s: %d records, %d topics, %d values, %d..%d us, pose %s, phases %s%n", w.getFileName(), one.recordCount(),
                one.entryCount(), one.valueCount(), one.startMicros(), one.endMicros(),
                firstLast(one, "/DriveState/Pose"), one.phases());
        }

        System.gc();
        long heap0 = usedHeap();
        long t0 = System.nanoTime();
        LogIndex index = LogIndex.load(logs, p -> {});
        long loadMs = (System.nanoTime() - t0) / 1_000_000;
        System.gc();
        long heap = usedHeap() - heap0;
        System.out.printf("merged: load %d ms, heap %d MB, %d topics, %d values, %d schemas, %.1f s (%d..%d)%n",
            loadMs, heap >> 20, index.entryCount(), index.valueCount(), index.schemas().size(),
            (index.endMicros() - index.startMicros()) / 1e6, index.startMicros(), index.endMicros());
        System.out.println("phases: " + index.phases());
        Map<String, Integer> groups = new TreeMap<>();
        for (String t : index.topics()) {
            int i = t.indexOf('/', 1);
            if (t.startsWith("/Phoenix") && i > 0) i = t.indexOf('/', i + 1);
            groups.merge(i < 0 ? t : t.substring(0, i), 1, Integer::sum);
        }
        System.out.println("groups: " + groups);
        assertTrue(index.entryCount() > 100);
        assertFalse(index.phases().isEmpty());

        NetworkTableInstance inst = NetworkTableInstance.create();
        try (Replayer r = new Replayer(index, inst)) {
            long frame = 16_667, n = 0, total = 0, worst = 0;
            for (long t = index.startMicros(); t <= index.endMicros(); t += frame, n++) {
                long s = System.nanoTime();
                r.seek(t);
                long d = System.nanoTime() - s;
                total += d;
                if (n > 60) worst = Math.max(worst, d);
            }
            long s = System.nanoTime();
            r.seek(index.startMicros() + (index.endMicros() - index.startMicros()) / 3);
            long back = System.nanoTime() - s;
            System.out.printf("seek per frame: avg %.3f ms, worst %.3f ms over %d frames; jump back %.3f ms%n",
                total / 1e6 / n, worst / 1e6, n, back / 1e6);
            assertTrue(inst.getTopic("/DriveState/Pose").exists());
        } finally {
            inst.close();
        }
    }

    private static String firstLast(LogIndex index, String topic) {
        for (LogIndex.Column c : index.columns) {
            if (c.topic.equals(topic)) return c.ts[0] + ".." + c.ts[c.size - 1] + " (" + c.size + ")";
        }
        return "none";
    }

    private static long usedHeap() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }
}
