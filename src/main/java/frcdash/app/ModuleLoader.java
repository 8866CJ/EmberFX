package frcdash.app;

import frcdash.DashModule;
import frcdash.config.Config;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Finds team modules: every {@link DashModule} listed in META-INF/services/frcdash.DashModule
 * inside the jars in ~/.frcdash/modules/. Problems are reported, never thrown, so one broken
 * jar can't stop the dashboard from opening.
 */
final class ModuleLoader {
    static final Path MODULES_DIR = Config.DIR.resolve("modules");

    private ModuleLoader() {}

    /** The modules found, in jar-name order. Anything that went wrong is added to problems. */
    static List<DashModule> load(List<String> problems) {
        List<URL> jars = findJars(problems);
        List<DashModule> modules = new ArrayList<>();
        if (jars.isEmpty()) return modules;

        ClassLoader loader = new URLClassLoader(jars.toArray(URL[]::new), ModuleLoader.class.getClassLoader());
        Iterator<DashModule> it = ServiceLoader.load(DashModule.class, loader).iterator();
        while (true) {
            try {
                if (!it.hasNext()) break;
                modules.add(it.next());
            } catch (ServiceConfigurationError | LinkageError e) { // a bad jar; ServiceLoader moves on to the next
                problems.add("A module in " + MODULES_DIR + " couldn't load: " + e);
            }
        }
        removeDuplicateIds(modules, problems);
        return modules;
    }

    private static List<URL> findJars(List<String> problems) {
        List<URL> jars = new ArrayList<>();
        try {
            Files.createDirectories(MODULES_DIR);
            try (Stream<Path> files = Files.list(MODULES_DIR)) {
                for (Path p : files.filter(p -> p.toString().toLowerCase().endsWith(".jar")).sorted().toList()) {
                    jars.add(p.toUri().toURL());
                }
            }
        } catch (IOException e) {
            problems.add("Couldn't read " + MODULES_DIR + ": " + e.getMessage());
        }
        return jars;
    }

    private static void removeDuplicateIds(List<DashModule> modules, List<String> problems) {
        Set<String> ids = new HashSet<>();
        modules.removeIf(m -> {
            if (ids.add(m.id())) return false;
            problems.add("Two modules use the id \"" + m.id() + "\"; skipped " + m.getClass().getName());
            return true;
        });
    }
}
