package fyi.osm.sourdough.smartmaps.layers;

import fyi.osm.sourdough.smartmaps.SmartMapsSchema;
import java.util.Set;

/**
 * Which of the two land layers a classified land feature belongs in.
 *
 * Shortbread has one `land` layer; SmartMaps splits the same material into `landcover` for
 * natural ground cover and `landuse` for what people do with it. The split is stated
 * explicitly here rather than inferred from which OSM key the kind came from, because the
 * keys do not line up with the distinction: `landuse=forest` and `natural=wood` are both
 * cover, while `leisure=park` is a use.
 */
public final class LandKindRouting {

  private LandKindRouting() {}

  private static final Set<String> COVER = Set.of(
    "forest",
    "grass",
    "meadow",
    "heath",
    "scrub",
    "grassland",
    "bare_rock",
    "scree",
    "shingle",
    "sand",
    "beach",
    "swamp",
    "bog",
    "string_bog",
    "wet_meadow",
    "marsh"
  );

  /** The layer a land `kind` belongs to. */
  public static String layerFor(String kind) {
    return COVER.contains(kind) ? SmartMapsSchema.LANDCOVER : SmartMapsSchema.LANDUSE;
  }

  public static boolean isCover(String kind) {
    return COVER.contains(kind);
  }
}
