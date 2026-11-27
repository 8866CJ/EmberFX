package emberfx.replay;

import emberfx.config.Config;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Converts CTRE .hoot logs to .wpilog with CTRE's owlet tool, which AdvantageScope downloads to
 * %APPDATA%\AdvantageScope\owlet (one exe per hoot format, named like owlet-26.3.0-C19.exe, where
 * C19 is the "compliancy": the hoot format version it reads).
 *
 * <p>Results are cached in {@code Config.DIR/replay-cache}, keyed by the hoot's file name, size and
 * modified time, so opening the same log again is instant.
 */
public final class HootConverter {
    private static final System.Logger LOG = System.getLogger(HootConverter.class.getName());
    private static final Pattern NAME = Pattern.compile("(?i)owlet-([0-9.]+)-C(\\d+)(\\.exe)?");
    private static final Pattern PROGRESS = Pattern.compile("Progress: \\[\\s*(\\d+)%\\]");
    private static final Pattern OWLET_LEVEL = Pattern.compile("owlet: (\\d+)");
    private static final Pattern HOOT_LEVEL = Pattern.compile("hoot log: (\\d+)");
    private static final long CONVERT_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10);
    private static final long QUERY_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(30);
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    private HootConverter() {}

    /** Where owlet was found, or empty. Looks in: an explicit path (may be null), %APPDATA%\AdvantageScope\owlet\owlet-*-C*.exe (AdvantageScope downloads them there), Phoenix Tuner X / WPILib install folders if they contain owlet, and PATH. Newest version first, after the explicit one. */
    public static List<Path> findOwlets(Path explicitOwlet) {
        Set<Path> found = new LinkedHashSet<>();
        String appData = System.getenv("APPDATA");
        String localAppData = System.getenv("LOCALAPPDATA");
        String programFiles = System.getenv("ProgramFiles");
        if (appData != null) scan(Paths.get(appData, "AdvantageScope", "owlet"), 1, found);
        if (localAppData != null) scanMatching(Paths.get(localAppData, "Packages"), "CTRElectronics", 5, found);
        if (programFiles != null) scanMatching(Paths.get(programFiles, "WindowsApps"), "CTRElectronics", 5, found);
        scan(Paths.get(WINDOWS ? "C:\\Users\\Public\\wpilib" : System.getProperty("user.home") + "/wpilib"), 3, found);
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                if (!dir.isBlank()) scan(Paths.get(dir.trim()), 1, found);
            }
        }
        List<Path> sorted = new ArrayList<>(found);
        sorted.sort(Comparator.comparing(HootConverter::versionKey).reversed());
        List<Path> out = new ArrayList<>();
        if (explicitOwlet != null && Files.isRegularFile(explicitOwlet)) out.add(explicitOwlet.toAbsolutePath());
        for (Path p : sorted) if (!out.contains(p)) out.add(p);
        return out;
    }

    /** Convert a .hoot to a cached .wpilog (cache: Config.DIR/replay-cache, keyed by file name + size + mtime; reuse if present). Picks the owlet whose compliancy matches the hoot's. Progress 0..1, reported on the calling thread. */
    public static Path toWpilog(Path hoot, List<Path> owlets, DoubleConsumer progress) throws IOException, InterruptedException {
        return toWpilog(hoot, owlets, progress, Config.DIR.resolve("replay-cache"));
    }

    static Path toWpilog(Path hoot, List<Path> owlets, DoubleConsumer progress, Path cacheDir) throws IOException, InterruptedException {
        String file = hoot.getFileName().toString();
        if (!Files.isRegularFile(hoot)) throw new IOException("Can't find " + hoot);
        String stem = file.toLowerCase().endsWith(".hoot") ? file.substring(0, file.length() - 5) : file;
        Path out = cacheDir.resolve(stem + "_" + Files.size(hoot) + "_" + Files.getLastModifiedTime(hoot).toMillis() + ".wpilog");
        if (Files.isRegularFile(out) && Files.size(out) > 0) {
            progress.accept(1);
            return out;
        }
        if (owlets.isEmpty()) {
            throw new IOException("Reading .hoot files needs CTRE's owlet tool, and none was found. Install AdvantageScope "
                + "(open a .hoot in it once so it downloads owlet) or Phoenix Tuner X, or pick owlet.exe.");
        }
        int need = hootCompliancy(hoot, owlets);
        Path owlet = null;
        List<String> have = new ArrayList<>();
        for (Path o : owlets) {
            int c = owletCompliancy(o, hoot);
            have.add(o.getFileName() + (c < 0 ? " (unknown)" : " (" + c + ")"));
            if (c == need) {
                owlet = o;
                break;
            }
        }
        if (owlet == null) {
            throw new IOException(file + " needs owlet compliancy " + need + ", but the owlets found are: "
                + String.join(", ", have) + ". Open the file once in AdvantageScope so it downloads the right owlet, or pick it.");
        }

        Files.createDirectories(cacheDir);
        Path tmp = cacheDir.resolve("partial-" + out.getFileName());
        Files.deleteIfExists(tmp);
        Run run;
        try {
            run = run(List.of(owlet.toString(), hoot.toAbsolutePath().toString(), tmp.toString(), "-f", "wpilog"),
                CONVERT_TIMEOUT_MS, progress);
        } catch (IOException | InterruptedException e) {
            deleteQuietly(tmp);
            throw e;
        }
        if (!Files.isRegularFile(tmp) || Files.size(tmp) == 0) {
            deleteQuietly(tmp);
            throw new IOException("owlet couldn't convert " + file + " (exit code " + run.exit + "): " + run.summary());
        }
        // A log cut off at power-down ends with "Could not read to end of input file" and a non-zero
        // exit code, but everything before the cut is in the output.
        if (run.exit != 0 || !run.messages.isEmpty()) {
            LOG.log(System.Logger.Level.WARNING, "owlet converted " + file + " with exit code " + run.exit + ": " + run.summary());
        }
        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        progress.accept(1);
        return out;
    }

    /** The hoot format version of a log, asked of the newest owlet that answers. */
    private static int hootCompliancy(Path hoot, List<Path> owlets) throws IOException, InterruptedException {
        IOException last = null;
        for (Path o : owlets) {
            try {
                Run r = run(List.of(o.toString(), hoot.toAbsolutePath().toString(), "--compliancy"), QUERY_TIMEOUT_MS, p -> {});
                int c = level(HOOT_LEVEL, r.text);
                if (c >= 0) return c;
                last = new IOException(o.getFileName() + " didn't report the hoot compliancy: " + r.summary());
            } catch (IOException e) {
                last = e;
            }
        }
        throw new IOException("Can't read the format of " + hoot.getFileName() + ": " + last.getMessage(), last);
    }

    /** An owlet's compliancy from its file name, or by asking it; -1 if unknown. */
    private static int owletCompliancy(Path owlet, Path hoot) throws InterruptedException {
        int c = compliancyFromName(owlet.getFileName().toString());
        if (c >= 0) return c;
        try {
            return level(OWLET_LEVEL, run(List.of(owlet.toString(), hoot.toAbsolutePath().toString(), "--compliancy"),
                QUERY_TIMEOUT_MS, p -> {}).text);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Can't run " + owlet, e);
            return -1;
        }
    }

    static int compliancyFromName(String fileName) {
        Matcher m = NAME.matcher(fileName);
        return m.matches() ? Integer.parseInt(m.group(2)) : -1;
    }

    /** Sort key: the version in owlet-26.3.0-C19.exe as zero-padded numbers; "" for other names. */
    static String versionKey(Path owlet) {
        Matcher m = NAME.matcher(owlet.getFileName().toString());
        if (!m.matches()) return "";
        StringBuilder key = new StringBuilder();
        for (String part : m.group(1).split("\\.")) {
            if (!part.isEmpty()) key.append(String.format("%08d", Long.parseLong(part.length() > 8 ? part.substring(0, 8) : part)));
        }
        return key.toString();
    }

    /** Percent from an owlet "Progress: [ 42%]" line, or -1. */
    static int progressPercent(String line) {
        Matcher m = PROGRESS.matcher(line);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static int level(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static boolean isOwlet(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        return n.startsWith("owlet") && (WINDOWS ? n.endsWith(".exe") : !n.contains(".")) && Files.isRegularFile(p);
    }

    private static void scan(Path dir, int depth, Set<Path> found) {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> s = Files.find(dir, depth, (p, a) -> isOwlet(p))) {
            s.forEach(p -> found.add(p.toAbsolutePath()));
        } catch (IOException | RuntimeException e) { // e.g. a folder we may not list
            LOG.log(System.Logger.Level.DEBUG, "Can't search " + dir + " for owlet", e);
        }
    }

    private static void scanMatching(Path parent, String prefix, int depth, Set<Path> found) {
        if (!Files.isDirectory(parent)) return;
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(parent, prefix + "*")) {
            for (Path d : dirs) scan(d, depth, found);
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.DEBUG, "Can't list " + parent, e);
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Can't delete " + p, e);
        }
    }

    /** What a finished owlet run printed: exit code, all text, and its non-progress lines with counts. */
    private record Run(int exit, String text, Map<String, Integer> messages) {
        String summary() {
            if (messages.isEmpty()) return "no output";
            List<String> parts = new ArrayList<>();
            messages.forEach((m, n) -> parts.add(n > 1 ? m + " (x" + n + ")" : m));
            return String.join("; ", parts);
        }
    }

    /** Run a command, reading its output on a helper thread; killed on timeout or interruption. */
    private static Run run(List<String> cmd, long timeoutMs, DoubleConsumer progress) throws IOException, InterruptedException {
        Process p;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        } catch (IOException e) {
            throw new IOException("Can't start " + cmd.get(0) + ": " + e.getMessage(), e);
        }
        StringBuilder text = new StringBuilder();
        Map<String, Integer> messages = new LinkedHashMap<>();
        double[] pct = {-1};
        Thread reader = new Thread(() -> readLines(p, text, messages, pct), "owlet-output");
        reader.setDaemon(true);
        reader.start();
        long deadline = System.currentTimeMillis() + timeoutMs;
        try {
            while (!p.waitFor(100, TimeUnit.MILLISECONDS)) {
                double v;
                synchronized (pct) { v = pct[0]; }
                if (v >= 0) progress.accept(Math.min(0.99, v / 100));
                if (System.currentTimeMillis() > deadline) {
                    p.destroyForcibly();
                    throw new IOException(cmd.get(0) + " took over " + timeoutMs / 1000 + " s and was stopped");
                }
            }
        } catch (InterruptedException e) {
            p.destroyForcibly();
            throw e;
        }
        reader.join(2000);
        synchronized (pct) {
            return new Run(p.exitValue(), text.toString(), new LinkedHashMap<>(messages));
        }
    }

    /** Lines end in \n or \r (owlet redraws its progress bar with \r). */
    private static void readLines(Process p, StringBuilder text, Map<String, Integer> messages, double[] pct) {
        StringBuilder line = new StringBuilder();
        try (Reader in = new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)) {
            int ch;
            while ((ch = in.read()) >= 0) {
                if (ch != '\r' && ch != '\n') {
                    line.append((char) ch);
                } else {
                    addLine(line.toString().trim(), text, messages, pct);
                    line.setLength(0);
                }
            }
            addLine(line.toString().trim(), text, messages, pct);
        } catch (IOException e) { // the process was killed while we read
            LOG.log(System.Logger.Level.DEBUG, "owlet output ended", e);
        }
    }

    private static void addLine(String l, StringBuilder text, Map<String, Integer> messages, double[] pct) {
        if (l.isEmpty()) return;
        synchronized (pct) {
            int percent = progressPercent(l);
            if (percent >= 0) {
                pct[0] = percent;
            } else {
                if (text.length() < 64 * 1024) text.append(l).append('\n');
                if (!l.startsWith("Extracting ") && !l.startsWith("Output has been written") && (messages.size() < 20 || messages.containsKey(l))) messages.merge(l, 1, Integer::sum);
            }
        }
    }
}
