package emberfx.tiles;

import edu.wpi.first.networktables.StringArraySubscriber;
import emberfx.NT;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows an MJPEG camera stream: from CameraServer (pick the /CameraPublisher/<camera> topic),
 * or any stream URL such as PhotonVision's.
 *
 * Frames are read on a background thread. Streaming pauses while the tile's tab is hidden,
 * so cameras don't use bandwidth nobody is looking at.
 */
public final class CameraTile extends Tile {
    private static final long HIDDEN_AFTER_MS = 1000;

    private final StringArraySubscriber streams;
    private final ImageView view = new ImageView();
    private final Label status = new Label("Waiting for camera...");
    private final Thread reader;
    private volatile boolean closed;
    private volatile long lastShown;           // updated every frame the tab is showing
    private volatile InputStream current;      // closed to stop a blocking read

    public CameraTile(TileConfig config) {
        super(config);
        String topic = config.topic().endsWith("/streams") ? config.topic() : config.topic() + "/streams";
        streams = track(NT.instance().getStringArrayTopic(topic).subscribe(new String[0]));

        view.setPreserveRatio(true);
        view.fitWidthProperty().bind(widthProperty().subtract(28));
        view.fitHeightProperty().bind(heightProperty().subtract(50));
        status.getStyleClass().add("muted");
        setCenter(new StackPane(view, status));

        reader = new Thread(this::readLoop, "camera " + config.title());
        reader.setDaemon(true);
        reader.start();
    }

    @Override
    public void update() {
        lastShown = System.currentTimeMillis();
    }

    @Override
    public void close() {
        closed = true;
        closeQuietly(current);
        super.close();
    }

    /** Keep connecting to the first working stream URL and showing its frames until the tile closes. */
    private void readLoop() {
        while (!closed) {
            if (System.currentTimeMillis() - lastShown > HIDDEN_AFTER_MS) {
                sleep(200);
                continue;
            }
            String url = streamUrl();
            if (url == null) {
                show(null, "No stream yet. Is the camera started on the robot?");
                sleep(1000);
                continue;
            }
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(2000);
                c.setReadTimeout(3000);
                current = new BufferedInputStream(c.getInputStream());
                show(null, "");
                byte[] frame;
                while (!closed && System.currentTimeMillis() - lastShown < HIDDEN_AFTER_MS && (frame = nextJpeg(current)) != null) {
                    Image img = new Image(new ByteArrayInputStream(frame));
                    if (!img.isError()) show(img, "");
                }
            } catch (IOException e) {
                if (!closed) show(null, "Can't reach " + url);
            } finally {
                closeQuietly(current);
            }
            sleep(500);
        }
    }

    /** The stream URL: the "url" option if set, else the first one CameraServer publishes, with the quality options added. */
    private String streamUrl() {
        String url = config.option("url").trim();
        if (url.isEmpty()) {
            String[] all = streams.get();
            if (all.length == 0) return null;
            url = all[0].startsWith("mjpg:") ? all[0].substring(5) : all[0];
        }
        // CameraServer reads these from the URL and sends a smaller stream.
        List<String> params = new ArrayList<>();
        if (!config.option("resolution").isBlank()) params.add("resolution=" + config.option("resolution").trim());
        if (config.optionDouble("fps") > 0) params.add("fps=" + (int) config.optionDouble("fps"));
        if (config.optionDouble("compression") > 0) params.add("compression=" + (int) config.optionDouble("compression"));
        if (params.isEmpty()) return url;
        return url + (url.contains("?") ? "&" : "?") + String.join("&", params);
    }

    /**
     * The next JPEG in an MJPEG stream: bytes from a start marker (FF D8) to an end marker (FF D9).
     * Scanning for the markers works for every MJPEG server without parsing its multipart headers.
     */
    static byte[] nextJpeg(InputStream in) throws IOException {
        int prev = -1, b;
        while ((b = in.read()) != -1) {
            if (prev == 0xFF && b == 0xD8) break;
            prev = b;
        }
        if (b == -1) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        out.write(0xFF);
        out.write(0xD8);
        prev = -1;
        while ((b = in.read()) != -1) {
            out.write(b);
            if (prev == 0xFF && b == 0xD9) return out.toByteArray();
            prev = b;
        }
        return null;
    }

    private void show(Image img, String message) {
        Platform.runLater(() -> {
            if (img != null) view.setImage(img);
            status.setText(message);
        });
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(InputStream in) {
        if (in == null) return;
        try {
            in.close();
        } catch (IOException ignored) { // closing a dropped stream; nothing to recover
        }
    }
}
