package fyi.osm.sourdough.smartmaps;

import fyi.osm.sourdough.Schema;
import fyi.osm.sourdough.common.buildings3d.BuildingDimensionParser;
import java.util.List;

/**
 * Runtime options for the SmartMaps profile.
 *
 * @param languages IETF codes emitted as both `name:xx` and `name_xx`, already expanded
 *     from any preset
 * @param levelHeight assumed height in meters of one above-ground building level
 * @param estimateMissingHeights whether buildings with no dimensions in OpenStreetMap get
 *     a height estimated from their building type. SmartMaps has no factual height field
 *     at all -- only render_height -- so this is on by default.
 */
public record SmartMapsConfiguration(
  List<String> languages,
  double levelHeight,
  boolean estimateMissingHeights
) {

  public SmartMapsConfiguration {
    languages = languages == null ? List.of() : List.copyOf(languages);
  }

  public static SmartMapsConfiguration defaults() {
    return new SmartMapsConfiguration(
      List.of(),
      BuildingDimensionParser.DEFAULT_LEVEL_HEIGHT_METERS,
      true
    );
  }

  /** The schema this configuration builds. */
  public Schema schema() {
    return Schema.SMARTMAPS;
  }
}
