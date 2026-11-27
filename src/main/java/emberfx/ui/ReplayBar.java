package emberfx.ui;

import emberfx.replay.LogIndex;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The controls along the bottom of the window while a log is replayed: play/pause, speed, a timeline
 * tinted by robot mode (auto, teleop, test) where a click or drag jumps to that moment, the time, and Exit.
 * {@link #tick} is called once per frame and returns the moment the tiles should show.
 */
final class ReplayBar extends HBox {
    private static final double[] SPEEDS = {0.25, 0.5, 1, 2, 4};
    private static final String PLAY = "M7 4l13 8-13 8z", PAUSE = "M8 5v14M16 5v14";
    private static final double RAIL = 6, BAND = 4, HEAD = 14; // timeline sizes in pixels

    private final LogIndex index;
    private final Pane track = Style.styled(new Pane(), "replay-track");
    private final Region rail = Style.styled(new Region(), "replay-rail");
    private final Region played = Style.styled(new Region(), "replay-played");
    private final Region head = Style.styled(new Region(), "replay-head");
    private final List<Region> bands = new ArrayList<>();
    private final Label time = Style.styled(new Label(), "replay-time");
    private final Button playButton = new Button();
    private final Button speedButton = new Button("1x");
    private int speedIndex = 2; // 1x
    private boolean playing;
    private double speed = 1;
    private long now;            // replay time, microseconds in the log's time base
    private long lastFrame = -1; // frame time of the previous tick, nanoseconds
    private long shownTenths = -1;

    ReplayBar(LogIndex index, Runnable exit) {
        this.index = index;
        getStyleClass().add("replay-bar");
        setAlignment(Pos.CENTER_LEFT);
        now = index.startMicros();

        playButton.getStyleClass().setAll("nav-button");
        playButton.setOnAction(e -> setPlaying(!playing));
        setPlaying(false);

        speedButton.setTooltip(new Tooltip("Replay speed: click to change"));
        speedButton.setMinWidth(Region.USE_PREF_SIZE);
        speedButton.setOnAction(e -> {
            speedIndex = (speedIndex + 1) % SPEEDS.length;
            speed = SPEEDS[speedIndex];
            speedButton.setText(speedLabel(speed));
        });

        for (LogIndex.Phase p : index.phases()) {
            if (p.mode().equals("Disabled")) continue;
            Region band = Style.styled(new Region(), "replay-phase", "replay-phase-" + p.mode().toLowerCase(Locale.ROOT));
            band.setUserData(p);
            Tooltip.install(band, new Tooltip(p.mode()));
            bands.add(band);
        }
        track.getChildren().add(rail);
        track.getChildren().addAll(bands);
        track.getChildren().addAll(played, head);
        track.getChildren().forEach(n -> n.setManaged(false)); // placed by layoutTrack(), not by the Pane
        track.setPrefHeight(28);
        track.setMinWidth(80);
        HBox.setHgrow(track, Priority.ALWAYS);
        track.widthProperty().addListener((obs, o, w) -> layoutTrack());
        track.heightProperty().addListener((obs, o, h) -> layoutTrack());
        track.setOnMousePressed(e -> seekTo(e.getX() / track.getWidth()));
        track.setOnMouseDragged(e -> seekTo(e.getX() / track.getWidth()));

        Button close = new Button("Exit replay");
        close.setOnAction(e -> exit.run());
        Label source = Style.styled(new Label(String.join(", ", index.sourceNames())), "muted");
        source.setMaxWidth(260);
        source.setTooltip(new Tooltip(String.join("\n", index.sourceNames())));
        getChildren().addAll(playButton, speedButton, track, time, source, close);
        showTime();
    }

    /** Advance by the time since the last frame (when playing) and return the moment to show. */
    long tick(long frameNanos) {
        if (playing && lastFrame >= 0) {
            now += (long) ((frameNanos - lastFrame) / 1000 * speed);
            if (now >= index.endMicros()) {
                now = index.endMicros();
                setPlaying(false); // stop at the end rather than loop
            }
            placeHead();
        }
        lastFrame = frameNanos;
        showTime();
        return now;
    }

    /** Space bar: play or pause. */
    void togglePlaying() { setPlaying(!playing); }

    /** Jump to a fraction (0..1) of the log. */
    private void seekTo(double fraction) {
        double f = Math.max(0, Math.min(1, fraction));
        now = index.startMicros() + (long) (f * (index.endMicros() - index.startMicros()));
        placeHead();
        showTime();
    }

    private void setPlaying(boolean p) {
        if (p && now >= index.endMicros()) now = index.startMicros(); // play again from the start
        playing = p;
        playButton.setGraphic(Style.icon(p ? PAUSE : PLAY));
        playButton.setTooltip(new Tooltip(p ? "Pause (Space)" : "Play (Space)"));
    }

    /** Rail across the middle, mode bands just under it, then the playhead. */
    private void layoutTrack() {
        double w = track.getWidth(), mid = track.getHeight() / 2;
        rail.resizeRelocate(0, mid - RAIL / 2, w, RAIL);
        for (Region band : bands) {
            LogIndex.Phase p = (LogIndex.Phase) band.getUserData();
            double x = fraction(p.startMicros()) * w;
            band.resizeRelocate(x, mid + RAIL / 2 + 2, Math.max(2, fraction(p.endMicros()) * w - x), BAND);
        }
        placeHead();
    }

    private void placeHead() {
        double w = track.getWidth(), mid = track.getHeight() / 2, x = fraction(now) * w;
        played.resizeRelocate(0, mid - RAIL / 2, x, RAIL);
        head.resizeRelocate(x - HEAD / 2, mid - HEAD / 2, HEAD, HEAD);
    }

    private double fraction(long micros) {
        return (double) (micros - index.startMicros()) / Math.max(1, index.endMicros() - index.startMicros());
    }

    private void showTime() {
        long tenths = (now - index.startMicros()) / 100_000;
        if (tenths == shownTenths) return; // the label only changes ten times a second
        shownTenths = tenths;
        time.setText(clock(now - index.startMicros()) + " / " + clock(index.endMicros() - index.startMicros()));
    }

    private static String speedLabel(double s) {
        return (s == Math.rint(s) ? String.valueOf((long) s) : String.valueOf(s)) + "x";
    }

    /** Microseconds as m:ss.t */
    static String clock(long micros) {
        long tenths = Math.max(0, micros) / 100_000;
        return String.format(Locale.ROOT, "%d:%02d.%d", tenths / 600, tenths / 10 % 60, tenths % 10);
    }
}
