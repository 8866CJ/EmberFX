package emberfx.autos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import emberfx.autos.AutoPreview.Marker;
import emberfx.autos.AutoPreview.Pose;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads a robot project's autos from src/main/deploy: PathPlanner ({@code pathplanner/autos/*.auto},
 * {@code pathplanner/paths/*.path}) and Choreo ({@code choreo/*.traj}), for previewing on the field.
 *
 * Formats (checked against PathPlannerLib and ChoreoLib sources):
 * <ul>
 * <li>.path/.auto: "version" is a string "2025.x" (PathPlannerLib requires major 2025; "2026.x" is also accepted
 *     here in case the GUI bumps it). Other versions are skipped.</li>
 * <li>.traj: integer "version" 1..3 (PathPlannerLib accepts at most 3).</li>
 * <li>Auto command tree: types sequential, parallel, race, deadline (all with data.commands), path (data.pathName),
 *     wait, named. Paths are previewed in tree order; parallel branches are simply listed in order.
 *     With "choreoAuto": true the pathName is a Choreo trajectory, optionally "name.N" for split N.</li>
 * </ul>
 * Simplifications: a PathPlanner path is sampled along its cubic Beziers (anchor, nextControl, prevControl of
 * the next waypoint, anchor) about every 7 cm. Heading is the start rotation (idealStartingState), rotation targets
 * (waypointRelativePos, rotationDegrees) and end rotation (goalEndState), interpolated linearly along waypoint
 * position by the shortest turn. The real follower spreads rotation by distance and speed, so this is a preview.
 * Choreo samples are thinned to about 7 cm spacing.
 *
 * Parsed files are cached by lastModified; {@link #refresh()} is cheap enough to call once a second.
 */
public final class AutoLibrary {
    private static final System.Logger LOG = System.getLogger(AutoLibrary.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final double SPACING = 0.07;

    private record Cached(long mtime, JsonNode json) {}
    private record Source(Path file, boolean choreo) {}
    private record Entry(AutoPreview preview, Map<Path, Long> deps) {}

    private final Path autos, paths, choreo;
    private final Map<Path, Cached> files = new HashMap<>();
    private final Map<String, Source> sources = new LinkedHashMap<>();
    private final Map<String, Entry> previews = new HashMap<>();

    public AutoLibrary(Path robotProject) {
        Path deploy = robotProject.resolve("src").resolve("main").resolve("deploy");
        autos = deploy.resolve("pathplanner").resolve("autos");
        paths = deploy.resolve("pathplanner").resolve("paths");
        choreo = deploy.resolve("choreo");
        refresh();
    }

    /** PathPlanner autos, then Choreo trajectories not shadowed by an auto of the same name. */
    public synchronized List<String> autoNames() { return new ArrayList<>(sources.keySet()); }

    /** Rescans the folders and re-reads only files whose lastModified changed. */
    public synchronized void refresh() {
        sources.clear();
        scan(autos, ".auto", false);
        scan(choreo, ".traj", true);
        files.keySet().removeIf(p -> !p.toFile().isFile());
        previews.keySet().removeIf(n -> !sources.containsKey(n));
    }

    private void scan(Path dir, String ext, boolean isChoreo) {
        File[] list = dir.toFile().listFiles((d, n) -> n.endsWith(ext));
        if (list == null) return;
        Arrays.sort(list);
        for (File f : list) {
            String name = f.getName().substring(0, f.getName().length() - ext.length());
            if (sources.containsKey(name)) continue;
            JsonNode j = load(f.toPath());
            if (j == null) continue;
            if (isChoreo ? choreoVersionOk(j) : ppVersionOk(j)) sources.put(name, new Source(f.toPath(), isChoreo));
            else LOG.log(System.Logger.Level.WARNING, "Skipping " + f + ": unsupported version");
        }
    }

    /** The preview for a name from {@link #autoNames()}, or empty if it has nothing drawable. */
    public synchronized Optional<AutoPreview> preview(String name) {
        Source src = sources.get(name);
        if (src == null) return Optional.empty();
        Entry e = previews.get(name);
        if (e != null && fresh(e.deps)) return Optional.ofNullable(e.preview);
        Map<Path, Long> deps = new HashMap<>();
        AutoPreview p = src.choreo ? choreoPreview(name, deps) : autoPreview(name, src.file, deps);
        previews.put(name, new Entry(p, deps));
        return Optional.ofNullable(p);
    }

    private static boolean fresh(Map<Path, Long> deps) {
        for (Map.Entry<Path, Long> d : deps.entrySet()) if (d.getKey().toFile().lastModified() != d.getValue()) return false;
        return true;
    }

    // ---- file loading

    private JsonNode load(Path p) {
        File f = p.toFile();
        long m = f.lastModified();
        Cached c = files.get(p);
        if (c != null && c.mtime == m) return c.json;
        JsonNode j = null;
        try {
            j = JSON.readTree(f);
            if (j != null && !j.isObject()) j = null;
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Skipping " + p + ": " + e.getMessage());
        }
        files.put(p, new Cached(m, j));
        return j;
    }

    private static boolean ppVersionOk(JsonNode j) {
        String v = j.path("version").asText("");
        return v.startsWith("2025") || v.startsWith("2026");
    }

    private static boolean choreoVersionOk(JsonNode j) {
        int v = j.path("version").asInt(0);
        return v >= 1 && v <= 3;
    }

    // ---- PathPlanner

    private AutoPreview autoPreview(String name, Path file, Map<Path, Long> deps) {
        deps.put(file, file.toFile().lastModified());
        JsonNode root = load(file);
        if (root == null) return null;
        boolean fromChoreo = root.path("choreoAuto").asBoolean(false);
        List<String> pathNames = new ArrayList<>();
        collect(root.path("command"), pathNames, 0);
        List<List<Pose>> segs = new ArrayList<>();
        List<Marker> marks = new ArrayList<>();
        for (String pn : pathNames) {
            if (fromChoreo) addChoreo(pn, segs, marks, deps);
            else addPath(pn, segs, marks, deps);
        }
        return build(name, segs, marks);
    }

    private static void collect(JsonNode cmd, List<String> out, int depth) {
        if (depth > 32 || !cmd.isObject()) return;
        JsonNode data = cmd.path("data");
        if (cmd.path("type").asText("").equals("path")) {
            String n = data.path("pathName").asText("");
            if (!n.isEmpty()) out.add(n);
        } else if (data.path("commands").isArray()) {
            for (JsonNode c : data.path("commands")) collect(c, out, depth + 1);
        }
    }

    private void addPath(String pathName, List<List<Pose>> segs, List<Marker> marks, Map<Path, Long> deps) {
        Path f = paths.resolve(pathName + ".path");
        deps.put(f, f.toFile().lastModified());
        JsonNode j = load(f);
        if (j == null || !ppVersionOk(j) || !j.path("waypoints").isArray() || j.path("waypoints").size() < 2) {
            LOG.log(System.Logger.Level.WARNING, "Skipping path " + f + ": missing, unreadable or unsupported version");
            return;
        }
        List<Pose> pts = new ArrayList<>();
        sample(j, pts, marks);
        segs.add(pts);
    }

    private static double[] pt(JsonNode n, double[] fallback) {
        return n.isObject() ? new double[] {n.path("x").asDouble(), n.path("y").asDouble()} : fallback;
    }

    /** Samples every Bezier of a path file into pts and resolves its event markers. */
    private static void sample(JsonNode j, List<Pose> pts, List<Marker> marks) {
        JsonNode w = j.path("waypoints");
        int n = w.size();
        double[][] a = new double[n][], pc = new double[n][], nc = new double[n][];
        for (int i = 0; i < n; i++) {
            a[i] = pt(w.get(i).path("anchor"), new double[] {0, 0});
            pc[i] = pt(w.get(i).path("prevControl"), a[i]);
            nc[i] = pt(w.get(i).path("nextControl"), a[i]);
        }
        // heading keyframes: start, rotation targets, end
        List<double[]> keys = new ArrayList<>();
        keys.add(new double[] {0, j.path("idealStartingState").path("rotation").asDouble(0)});
        for (JsonNode r : j.path("rotationTargets"))
            keys.add(new double[] {r.path("waypointRelativePos").asDouble(), r.path("rotationDegrees").asDouble()});
        keys.add(new double[] {n - 1, j.path("goalEndState").path("rotation").asDouble(0)});
        keys.sort((p, q) -> Double.compare(p[0], q[0]));

        for (int i = 0; i < n - 1; i++) {
            double[][] c = {a[i], nc[i], pc[i + 1], a[i + 1]};
            double len = Math.hypot(c[1][0] - c[0][0], c[1][1] - c[0][1]) + Math.hypot(c[2][0] - c[1][0], c[2][1] - c[1][1])
                + Math.hypot(c[3][0] - c[2][0], c[3][1] - c[2][1]);
            int steps = Math.max(2, (int) Math.ceil(len / SPACING));
            for (int k = (i == 0 ? 0 : 1); k <= steps; k++) {
                pts.add(at(c, (double) k / steps, heading(keys, i + (double) k / steps)));
            }
        }
        for (JsonNode m : j.path("eventMarkers")) {
            double pos = Math.max(0, Math.min(n - 1, m.path("waypointRelativePos").asDouble()));
            int seg = Math.min((int) pos, n - 2);
            double[][] c = {a[seg], nc[seg], pc[seg + 1], a[seg + 1]};
            marks.add(new Marker(m.path("name").asText(""), at(c, pos - seg, heading(keys, pos))));
        }
    }

    private static Pose at(double[][] c, double t, double headingDeg) {
        double u = 1 - t, b0 = u * u * u, b1 = 3 * u * u * t, b2 = 3 * u * t * t, b3 = t * t * t;
        return new Pose(b0 * c[0][0] + b1 * c[1][0] + b2 * c[2][0] + b3 * c[3][0],
            b0 * c[0][1] + b1 * c[1][1] + b2 * c[2][1] + b3 * c[3][1], AutoPreview.norm(headingDeg));
    }

    private static double heading(List<double[]> keys, double pos) {
        for (int i = 0; i + 1 < keys.size(); i++) {
            double[] k0 = keys.get(i), k1 = keys.get(i + 1);
            if (pos <= k1[0] || i + 2 == keys.size()) {
                double span = k1[0] - k0[0];
                double f = span <= 1e-9 ? 1 : Math.max(0, Math.min(1, (pos - k0[0]) / span));
                return k0[1] + AutoPreview.norm(k1[1] - k0[1]) * f; // shortest turn
            }
        }
        return keys.get(0)[1];
    }

    // ---- Choreo

    private AutoPreview choreoPreview(String name, Map<Path, Long> deps) {
        List<List<Pose>> segs = new ArrayList<>();
        List<Marker> marks = new ArrayList<>();
        addChoreo(name, segs, marks, deps);
        return build(name, segs, marks);
    }

    private static Pose pose(JsonNode s) {
        return new Pose(s.path("x").asDouble(), s.path("y").asDouble(), AutoPreview.norm(Math.toDegrees(s.path("heading").asDouble())));
    }

    /** ref is a trajectory name, or "name.N" for split N. */
    private void addChoreo(String ref, List<List<Pose>> segs, List<Marker> marks, Map<Path, Long> deps) {
        String base = ref;
        int split = -1;
        int dot = ref.lastIndexOf('.');
        if (dot > 0 && ref.substring(dot + 1).matches("\\d{1,6}")) {
            base = ref.substring(0, dot);
            split = Integer.parseInt(ref.substring(dot + 1));
        }
        Path f = choreo.resolve(base + ".traj");
        deps.put(f, f.toFile().lastModified());
        JsonNode j = load(f);
        JsonNode samples = j == null ? null : j.path("trajectory").path("samples");
        if (samples == null || !choreoVersionOk(j) || !samples.isArray() || samples.isEmpty()) {
            LOG.log(System.Logger.Level.WARNING, "Skipping trajectory " + f + ": missing, unreadable or unsupported version");
            return;
        }
        int from = 0, to = samples.size();
        if (split >= 0) {
            List<Integer> sp = new ArrayList<>();
            for (JsonNode s : j.path("trajectory").path("splits")) sp.add(s.asInt());
            if (sp.isEmpty() || sp.get(0) != 0) sp.add(0, 0);
            if (split >= sp.size()) {
                LOG.log(System.Logger.Level.WARNING, "Skipping " + ref + ": no such split");
                return;
            }
            from = Math.max(0, Math.min(sp.get(split), samples.size() - 1));
            to = split + 1 < sp.size() ? Math.max(from + 1, Math.min(sp.get(split + 1), samples.size())) : samples.size();
        }
        List<Pose> pts = new ArrayList<>();
        Pose last = null;
        for (int i = from; i < to; i++) {
            Pose p = pose(samples.get(i));
            if (last == null || i == to - 1 || Math.hypot(p.x() - last.x(), p.y() - last.y()) >= SPACING) {
                pts.add(p);
                last = p;
            }
        }
        segs.add(pts);
        // events: time = from.targetTimestamp + from.offset.val; marker sits at the first sample at or after that time
        for (JsonNode ev : j.path("events")) {
            double t = ev.path("from").path("targetTimestamp").asDouble(-1) + ev.path("from").path("offset").path("val").asDouble(0);
            String n = ev.path("name").asText("");
            if (t < 0 || n.isEmpty()) continue;
            for (int i = from; i < to; i++) {
                if (samples.get(i).path("t").asDouble() >= t) {
                    marks.add(new Marker(n, pose(samples.get(i))));
                    break;
                }
            }
        }
    }

    private static AutoPreview build(String name, List<List<Pose>> segs, List<Marker> marks) {
        segs.removeIf(List::isEmpty);
        if (segs.isEmpty()) return null;
        return new AutoPreview(name, List.copyOf(segs), segs.get(0).get(0), List.copyOf(marks));
    }
}
