package frcdash.tiles;

import edu.wpi.first.networktables.DoubleSubscriber;
import frcdash.NT;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

/** Plots a number over the last few seconds. Samples 20 times a second while its tab is showing. */
public final class GraphTile extends Tile {
    private static final long SAMPLE_NANOS = 50_000_000;

    private final DoubleSubscriber sub;
    private final NumberAxis time = new NumberAxis();
    private final XYChart.Series<Number, Number> series = new XYChart.Series<>();
    private final long start = System.nanoTime();
    private long lastSample;

    public GraphTile(TileConfig config) {
        super(config);
        sub = track(NT.subDouble(config.topic(), 0.0));

        NumberAxis y = new NumberAxis();
        double min = config.optionDouble("min"), max = config.optionDouble("max");
        if (!Double.isNaN(min) && !Double.isNaN(max) && max > min) {
            y.setAutoRanging(false);
            y.setLowerBound(min);
            y.setUpperBound(max);
            y.setTickUnit((max - min) / 5);
        } else {
            y.setForceZeroInRange(false);
        }
        time.setAutoRanging(false);
        time.setTickLabelsVisible(false);
        time.setTickMarkVisible(false);

        LineChart<Number, Number> chart = new LineChart<>(time, y);
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setLegendVisible(false);
        chart.getData().add(series);
        setCenter(chart);
    }

    @Override
    public void update() {
        long now = System.nanoTime();
        if (now - lastSample < SAMPLE_NANOS || sub.getAtomic().timestamp == 0) return; // nothing published yet
        lastSample = now;

        double t = (now - start) / 1e9, window = Math.max(1, config.optionDouble("seconds"));
        var points = series.getData();
        points.add(new XYChart.Data<>(t, sub.get()));
        while (!points.isEmpty() && points.get(0).getXValue().doubleValue() < t - window) points.remove(0);
        time.setLowerBound(t - window);
        time.setUpperBound(t);
    }
}
