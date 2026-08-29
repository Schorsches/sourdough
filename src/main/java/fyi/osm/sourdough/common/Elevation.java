package fyi.osm.sourdough.common;

import com.onthegomap.planetiler.reader.WithTags;
import com.onthegomap.planetiler.util.Parse;

/**
 * Elevation from the OSM `ele` tag, in metres and in feet.
 *
 * Carrying both spares a client from unit conversion in a style expression, which is why
 * the SmartMaps layout has `ele` and `ele_ft` side by side. Implausible values are
 * dropped rather than clamped: the deepest point on land is around -430 m and the highest
 * around 8850 m, so anything outside a generous band is a tagging error.
 */
public final class Elevation {

  private Elevation() {}

  private static final double MIN_PLAUSIBLE_METERS = -500;
  private static final double MAX_PLAUSIBLE_METERS = 9000;
  private static final double FEET_PER_METER = 3.280839895;

  /** Elevation in metres, or null when absent or implausible. */
  public static Double meters(WithTags sf) {
    var raw = sf.getString("ele");
    if (raw == null) return null;
    Double parsed = Parse.meters(raw.trim());
    if (parsed == null || !Double.isFinite(parsed)) return null;
    if (parsed < MIN_PLAUSIBLE_METERS || parsed > MAX_PLAUSIBLE_METERS) return null;
    return Math.round(parsed * 10) / 10.0;
  }

  /** The same elevation in feet, rounded to whole feet, or null. */
  public static Double feet(Double meters) {
    if (meters == null) return null;
    return (double) Math.round(meters * FEET_PER_METER);
  }
}
