package frcdash.tiles;

import frcdash.Setting;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * A kind of tile the user can add: "Text", "Slider", "Button", ...
 *
 * @param id          saved in config.json, so keep it stable. Lowercase, e.g. "slider".
 * @param name        shown in the Add tile dialog
 * @param description one sentence shown under the name
 * @param options     per-tile options, edited in the tile's dialog (format, min/max, colors, ...)
 * @param width       starting size in pixels
 * @param height      starting size in pixels
 * @param dataTypes   NetworkTables types this tile suits, used to pick a tile for a topic automatically.
 *                    Plain types ("double", "boolean", "string", "double[]") or Sendable types
 *                    ("String Chooser", "Command"). Empty means it isn't suggested automatically.
 * @param factory     makes the tile, usually a constructor reference like SliderTile::new
 */
public record TileType(String id, String name, String description, List<Setting> options,
                       double width, double height, Set<String> dataTypes, Function<TileConfig, Tile> factory) {
}
