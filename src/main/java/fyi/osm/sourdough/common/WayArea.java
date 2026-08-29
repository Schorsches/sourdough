package fyi.osm.sourdough.common;

import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;

/**
 * Computation of the `way_area` attribute.
 *
 * The units genuinely differ between layers and must not be unified: `water_polygons`
 * and `water_polygons_labels` are specified in square meters of the Mercator
 * projection, while `boundary_labels` is specified in hectares.
 *
 * Areas are measured on the original source geometry, before tile simplification, which
 * is what the projected area of the OSM object means.
 */
public final class WayArea {

  private WayArea() {}

  /** Circumference of the earth at the equator, the side length of the Mercator square. */
  public static final double EARTH_CIRCUMFERENCE_METERS = 40_075_016.6855785;

  private static final double WORLD_AREA_SQUARE_METERS =
    EARTH_CIRCUMFERENCE_METERS * EARTH_CIRCUMFERENCE_METERS;

  /**
   * Upper endpoint for log-scale area sort keys. Comfortably larger than any single OSM
   * water body or country, so real features spread across the scale rather than
   * saturating at the top of it.
   */
  public static final double MAX_PLAUSIBLE_AREA = 1e13;

  /** Projected area in square meters, or null if the geometry cannot be measured. */
  public static Double squareMeters(SourceFeature sf) {
    try {
      // SourceFeature.area() is in world coordinates, where the whole Mercator square
      // has an area of 1.
      double worldArea = sf.area();
      if (!Double.isFinite(worldArea) || worldArea <= 0) return null;
      return worldArea * WORLD_AREA_SQUARE_METERS;
    } catch (GeometryException e) {
      return null;
    }
  }

  /** Projected area in hectares, or null if the geometry cannot be measured. */
  public static Double hectares(SourceFeature sf) {
    var squareMeters = squareMeters(sf);
    return squareMeters == null ? null : squareMeters / 10_000.0;
  }
}
