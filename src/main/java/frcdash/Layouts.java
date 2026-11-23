package frcdash;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Layout files: just the tabs and their tiles, {"tabs": [...]}, without team number or other settings.
 *
 * Teams keep one in their robot project at src/main/deploy/dashboard.json, so it's versioned with the code
 * and deployed to the roboRIO. With WebServer.start(5800, Filesystem.getDeployDirectory().getPath())
 * in the robot code, any laptop running the dashboard can then load it straight from the robot.
 */
public final class Layouts {
    public static final String DEFAULT_FILE = "dashboard.json";
    public static final int ROBOT_PORT = 5800;
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private Layouts() {}

    /** Download a layout from the robot's web server. */
    public static ArrayNode fetchFromRobot(String host, String file) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + host + ":" + ROBOT_PORT + "/" + file))
            .timeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("the robot answered " + response.statusCode() + " for " + file
                + ". Is it in src/main/deploy and is WebServer started on port " + ROBOT_PORT + "?");
        }
        return parse(response.body());
    }

    /** The "tabs" of a layout or full config file. */
    public static ArrayNode parse(String json) throws IOException {
        JsonNode tabs = JSON.readTree(json).path("tabs");
        if (!(tabs instanceof ArrayNode a)) throw new IOException("no \"tabs\" list in the layout");
        return a;
    }

    /**
     * Merge incoming tabs into the current ones, in place.
     * replaceAll: the incoming tabs become the only tabs.
     * Otherwise: a tab with the same name is replaced, new names are added, other tabs are kept.
     */
    public static void merge(ArrayNode current, ArrayNode incoming, boolean replaceAll) {
        if (replaceAll) current.removeAll();
        for (JsonNode tab : incoming) {
            String name = tab.path("name").asText();
            int existing = -1;
            for (int i = 0; i < current.size(); i++) if (current.get(i).path("name").asText().equals(name)) existing = i;
            if (existing >= 0) current.set(existing, tab.deepCopy());
            else current.add(tab.deepCopy());
        }
    }

    /** Write the current tabs to <project>/src/main/deploy/dashboard.json. Returns the file written. */
    public static Path saveToProject(Path project) throws IOException {
        Path file = project.resolve("src").resolve("main").resolve("deploy").resolve(DEFAULT_FILE);
        Files.createDirectories(file.getParent());
        ObjectNode layout = JSON.createObjectNode();
        layout.set("tabs", Config.tabs().deepCopy());
        JSON.writeValue(file.toFile(), layout);
        return file;
    }
}
