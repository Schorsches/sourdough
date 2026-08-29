package fyi.osm.sourdough.shortbread.buildings3d;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.reader.SourceFeature;
import java.util.List;
import java.util.Map;

/**
 * The 3D attributes of the shortbread-1.1-3d extension.
 *
 * These are NOT part of Shortbread 1.1. They are additive, so a style written for base
 * Shortbread is unaffected by their presence. See BUILDINGS_3D.md.
 *
 * Attributes are omitted rather than set to a default: every attribute here is
 * multiplied by millions of buildings, and an absent attribute and one set to the
 * renderer's default mean the same thing to a consumer.
 */
public final class Buildings3d {

  private Buildings3d() {}

  /**
   * Appearance attributes copied straight from OSM. These are sparse in the data, so
   * their cost is small, but they are the first candidates to drop if the zoom-14 tile
   * size budget is ever exceeded.
   */
  private static final Map<String, String> APPEARANCE = Map.of(
    "building:colour", "building_colour",
    "building:material", "building_material",
    "roof:colour", "roof_colour",
    "roof:material", "roof_material"
  );

  /** Roof shape values a renderer can act on; anything else is noise in a tile. */
  private static final List<String> ROOF_ORIENTATIONS = List.of("along", "across");

  /** Sets the normalized 3D attributes on a building or building part. */
  public static void setAttributes(
    SourceFeature sf,
    FeatureCollector.Feature feature,
    BuildingDimensions dimensions
  ) {
    if (dimensions.height() != null) {
      feature.setAttr("height", dimensions.height());
      if (dimensions.estimated()) {
        // Marks the derived subset only, so explicit heights cost nothing extra.
        feature.setAttr("height_estimated", true);
      }
    }
    if (dimensions.minHeight() != null) {
      feature.setAttr("min_height", dimensions.minHeight());
    }
    if (dimensions.roofHeight() != null) {
      feature.setAttr("roof_height", dimensions.roofHeight());
    }
    if (dimensions.levels() != null) {
      feature.setAttr("building_levels", dimensions.levels());
    }

    var roofShape = sf.getString("roof:shape");
    if (roofShape != null) {
      feature.setAttr("roof_shape", roofShape);
    }
    var roofOrientation = sf.getString("roof:orientation");
    if (roofOrientation != null && ROOF_ORIENTATIONS.contains(roofOrientation)) {
      feature.setAttr("roof_orientation", roofOrientation);
    }
    var roofDirection = direction(sf.getString("roof:direction"));
    if (roofDirection != null) {
      feature.setAttr("roof_direction", roofDirection);
    }

    APPEARANCE.forEach((osmKey, attribute) -> {
      var value = sf.getString(osmKey);
      if (value != null) {
        feature.setAttr(attribute, value);
      }
    });
  }

  /**
   * A compass bearing in degrees. Cardinal names are common in OSM but a renderer wants a
   * number, and anything outside 0-360 is a tagging error.
   */
  static Double direction(String value) {
    if (value == null) return null;
    var trimmed = value.trim();
    if (trimmed.isEmpty()) return null;
    var cardinal = CARDINALS.get(trimmed.toUpperCase(java.util.Locale.ROOT));
    if (cardinal != null) return cardinal;
    double parsed;
    try {
      parsed = Double.parseDouble(trimmed);
    } catch (NumberFormatException e) {
      return null;
    }
    if (!Double.isFinite(parsed) || parsed < 0 || parsed > 360) return null;
    return Math.round(parsed * 10) / 10.0;
  }

  private static final Map<String, Double> CARDINALS = Map.ofEntries(
    Map.entry("N", 0.0),
    Map.entry("NNE", 22.5),
    Map.entry("NE", 45.0),
    Map.entry("ENE", 67.5),
    Map.entry("E", 90.0),
    Map.entry("ESE", 112.5),
    Map.entry("SE", 135.0),
    Map.entry("SSE", 157.5),
    Map.entry("S", 180.0),
    Map.entry("SSW", 202.5),
    Map.entry("SW", 225.0),
    Map.entry("WSW", 247.5),
    Map.entry("W", 270.0),
    Map.entry("WNW", 292.5),
    Map.entry("NW", 315.0),
    Map.entry("NNW", 337.5)
  );
}
