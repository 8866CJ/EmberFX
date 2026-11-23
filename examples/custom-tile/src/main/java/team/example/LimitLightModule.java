package team.example;

import edu.wpi.first.networktables.DoubleSubscriber;
import frcdash.DashModule;
import frcdash.ModuleContext;
import frcdash.NT;
import frcdash.Setting;
import frcdash.tiles.Tile;
import frcdash.tiles.TileConfig;
import frcdash.tiles.TileType;
import frcdash.ui.Style;
import javafx.scene.control.Label;

import java.util.List;
import java.util.Set;

/**
 * Example team module: adds a "Limit light" tile type, a number that turns red above a limit.
 * After installing, it shows up in Add tile next to the built-in types.
 */
public final class LimitLightModule implements DashModule {
    @Override public String id() { return "limit-light"; }

    @Override public String name() { return "Limit light tile (example)"; }

    @Override
    public void start(ModuleContext ctx) {
        ctx.addTileType(new TileType(
            "limit-light", "Limit light", "A number that turns red when it goes over a limit.",
            List.of(Setting.number("limit", "Turn red above", 10)),
            200, 120, Set.of(), LimitLightTile::new));
    }

    /** The tile itself: subscribe in the constructor, refresh in update(). */
    static final class LimitLightTile extends Tile {
        private final DoubleSubscriber value;
        private final Label label = new Label("--");

        LimitLightTile(TileConfig config) {
            super(config);
            value = track(NT.subDouble(config.topic(), 0.0));
            setCenter(label);
        }

        @Override
        public void update() {
            double v = value.get();
            label.setText(String.format("%.1f", v));
            label.setStyle(Style.text(v > config.optionDouble("limit") ? Style.RED : Style.GREEN, 36));
        }
    }
}
