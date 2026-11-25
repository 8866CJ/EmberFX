package emberfx.tiles;

import edu.wpi.first.networktables.DoublePublisher;
import edu.wpi.first.networktables.DoubleSubscriber;
import emberfx.NT;
import emberfx.ui.Style;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.VBox;

/** Drag to send a number to the robot. When not dragging, it follows the robot's value. */
public final class SliderTile extends Tile {
    private final Slider slider;
    private final Label value = Style.value("--", 18);
    private final DoubleSubscriber sub;
    private final LazyPublisher<DoublePublisher> pub = track(new LazyPublisher<>(() -> NT.pubDouble(config.topic())));
    private boolean following;   // true while the slider is being moved to match the robot, so it doesn't send

    public SliderTile(TileConfig config) {
        super(config);
        sub = track(NT.subDouble(config.topic(), 0.0));
        double min = config.optionDouble("min"), max = config.optionDouble("max"), step = config.optionDouble("step");
        if (!(max > min)) { min = -1; max = 1; }
        slider = new Slider(min, max, Math.max(min, Math.min(max, 0)));
        if (step > 0) {
            slider.setBlockIncrement(step);
            slider.setMajorTickUnit(step);
            slider.setMinorTickCount(0);
            slider.setSnapToTicks(true);
        }
        slider.valueProperty().addListener((obs, o, v) -> {
            if (following) return;
            pub.get().set(v.doubleValue());
        });
        setCenter(new VBox(6, value, slider));
    }

    @Override
    public void update() {
        double robot = sub.get();
        value.setText(TextTile.number(slider.isValueChanging() ? slider.getValue() : robot, config.option("format")));
        if (!slider.isValueChanging() && !slider.isPressed()) {
            following = true;
            slider.setValue(robot);
            following = false;
        }
    }
}
