package fyi.osm.sourdough.common.buildings3d;

import com.onthegomap.planetiler.reader.WithTags;
import com.onthegomap.planetiler.util.Parse;

/**
 * Turns OpenStreetMap building tags into normalized 3D dimensions.
 *
 * The pipeline is parse, validate, derive, validate, emit. Every parse step returns null
 * on bad input rather than throwing, because a single malformed object must never be
 * able to abort a planet build.
 *
 * The critical semantic rule: in Simple 3D Buildings, `height` is the total height from
 * the ground to the top of the roof. When an explicit height exists, roof height
 * describes a portion *inside* that total and is never added to it. Adding them would
 * double-count the roof, so a building tagged height=14 + roof:height=2 is 14 m tall,
 * not 16.
 *
 * Heights are taken from the best source available, in order: an explicit tag, a level
 * count, or an estimate from the building type. Only the first is measurement; the other
 * two are marked `height_estimated` so a consumer can tell them apart, and the type-based
 * estimate can be turned off entirely.
 */
public final class BuildingDimensionParser {

  private final double levelHeight;
  private final boolean estimateMissingHeights;
  private final BuildingMetrics metrics;

  public BuildingDimensionParser(
    double levelHeight,
    boolean estimateMissingHeights,
    BuildingMetrics metrics
  ) {
    this.levelHeight = levelHeight;
    this.estimateMissingHeights = estimateMissingHeights;
    this.metrics = metrics;
  }

  /**
   * Upper plausibility bound for a height in meters. The tallest building in the world is
   * under 850 m, and the tallest structure of any kind under 650 m, so values beyond this
   * are tagging errors rather than architecture. Rejections are counted, not clamped, so
   * that a bad value never becomes a plausible-looking one.
   */
  public static final double MAX_PLAUSIBLE_HEIGHT_METERS = 1000.0;

  /** Above this, a level count is a tagging error rather than a building. */
  public static final double MAX_PLAUSIBLE_LEVELS = 200.0;

  /** Emitted heights are rounded to this many decimal places; finer is noise in a tile. */
  private static final int HEIGHT_DECIMALS = 1;

  public BuildingDimensions parse(WithTags sf) {
    Double explicitHeight = meters(first(sf, "height", "building:height"));
    Double explicitMinHeight = meters(first(sf, "min_height", "building:min_height"));
    Double levels = count(sf.getString("building:levels"));
    Double minLevel = count(sf.getString("building:min_level"));
    Double taggedRoofHeight = meters(sf.getString("roof:height"));
    Double roofLevels = count(sf.getString("roof:levels"));

    if (sf.hasTag("height") && explicitHeight == null) {
      metrics.increment(BuildingMetrics.HEIGHT_INVALID);
    }
    if (sf.hasTag("building:levels") && levels == null) {
      metrics.increment(BuildingMetrics.LEVELS_INVALID);
    }

    Double total;
    Double roofHeight;
    boolean estimated;

    if (explicitHeight != null) {
      // The explicit height already includes the roof.
      total = explicitHeight;
      roofHeight = roofWithin(total, taggedRoofHeight, roofLevels);
      estimated = false;
      metrics.increment(BuildingMetrics.HEIGHT_EXPLICIT);
    } else if (levels != null && levels > 0) {
      double facade = levels * levelHeight;
      roofHeight = taggedRoofHeight != null
        ? taggedRoofHeight
        : (roofLevels != null ? roofLevels * levelHeight : null);
      // Here the addition is correct: the level count describes the facade only.
      total = facade + (roofHeight == null ? 0 : roofHeight);
      estimated = true;
      metrics.increment(BuildingMetrics.HEIGHT_FROM_LEVELS);
    } else if (estimateMissingHeights) {
      // Nothing about this building's dimensions is mapped, which is the common case.
      // Fall back to a typical storey count for its type so that it can still be drawn.
      double assumedLevels = BuildingTypeDefaults.levelsFor(buildingType(sf));
      roofHeight = taggedRoofHeight;
      total = assumedLevels * levelHeight + (roofHeight == null ? 0 : roofHeight);
      estimated = true;
      metrics.increment(BuildingMetrics.HEIGHT_FROM_TYPE);
    } else {
      total = null;
      roofHeight = null;
      estimated = false;
      metrics.increment(BuildingMetrics.HEIGHT_ABSENT);
    }

    Double base = explicitMinHeight;
    if (base == null && minLevel != null && minLevel > 0) {
      base = minLevel * levelHeight;
    }

    // A base at or above the top would make the extrusion inside out.
    if (base != null && total != null && base >= total) {
      metrics.increment(BuildingMetrics.MIN_HEIGHT_INVALID);
      base = null;
    }
    if (base != null && base <= 0) {
      // 0 is the renderer's default; emitting it would cost bytes for nothing.
      base = null;
    }

    Integer emittedLevels = levels == null ? null : (int) Math.round(levels);

    return new BuildingDimensions(
      round(total),
      round(base),
      round(roofHeight),
      emittedLevels,
      estimated && total != null
    );
  }

  /**
   * The roof height inside an explicit total. A roof that is at least as tall as the whole
   * building is contradictory tagging, so it is dropped and counted.
   */
  private Double roofWithin(double total, Double taggedRoofHeight, Double roofLevels) {
    if (taggedRoofHeight != null) {
      if (taggedRoofHeight < total) return taggedRoofHeight;
      metrics.increment(BuildingMetrics.ROOF_HEIGHT_INVALID);
      return null;
    }
    if (roofLevels != null) {
      double derived = roofLevels * levelHeight;
      if (derived < total) return derived;
      metrics.increment(BuildingMetrics.ROOF_HEIGHT_INVALID);
    }
    return null;
  }

  /** The building type an estimate is based on: a part's own type, else the building's. */
  private static String buildingType(WithTags sf) {
    var part = sf.getString("building:part");
    if (part != null && !part.equals("yes") && !part.equals("no")) return part;
    return sf.getString("building");
  }

  private static String first(WithTags sf, String... keys) {
    for (var key : keys) {
      var value = sf.getString(key);
      if (value != null) return value;
    }
    return null;
  }

  /**
   * A plain length, optionally with a unit: "12", "12.5", "12 m", "40 ft".
   *
   * The shape is checked before any conversion because Planetiler's Parse.meters is
   * lenient in a way that is dangerous here: it happily reads "12;14" as 14 and "1e9" as
   * 9, turning malformed tagging into a plausible-looking height. Anything that is not
   * unambiguously one number and at most one unit is rejected outright.
   */
  private static final java.util.regex.Pattern SIMPLE_LENGTH = java.util.regex.Pattern.compile(
    "^-?\\d+(?:\\.\\d+)?\\s*(?:m|metre|metres|meter|meters|ft|feet|foot)?$",
    java.util.regex.Pattern.CASE_INSENSITIVE
  );

  /** Imperial building heights are often tagged as feet and inches: "12'", "12'3\"". */
  private static final java.util.regex.Pattern FEET_INCHES = java.util.regex.Pattern.compile(
    "^\\d+(?:\\.\\d+)?'(?:\\s*\\d+(?:\\.\\d+)?\")?$"
  );

  /** A bare count: "4", "3.5". Java's parser would otherwise accept "3d" and "1e9". */
  private static final java.util.regex.Pattern PLAIN_NUMBER = java.util.regex.Pattern.compile(
    "^-?\\d+(?:\\.\\d+)?$"
  );

  /**
   * Parses a length into meters, or null if the value is not a single plausible length.
   * Tolerates a decimal comma, which is common in continental European tagging.
   */
  static Double meters(String value) {
    var normalized = normalizeDecimalSeparator(value);
    if (normalized == null) return null;
    if (!SIMPLE_LENGTH.matcher(normalized).matches() && !FEET_INCHES.matcher(normalized).matches()) {
      return null;
    }
    Double parsed = Parse.meters(normalized);
    if (parsed == null || !Double.isFinite(parsed)) return null;
    if (parsed <= 0 || parsed > MAX_PLAUSIBLE_HEIGHT_METERS) return null;
    return parsed;
  }

  /**
   * Parses a level count. Fractional levels are real ("3.5" storeys exist) so the value
   * stays a double; negative, non-finite and absurd counts are rejected.
   */
  static Double count(String value) {
    var normalized = normalizeDecimalSeparator(value);
    if (normalized == null) return null;
    if (!PLAIN_NUMBER.matcher(normalized).matches()) return null;
    double parsed = Double.parseDouble(normalized);
    if (!Double.isFinite(parsed)) return null;
    if (parsed < 0 || parsed > MAX_PLAUSIBLE_LEVELS) return null;
    return parsed;
  }

  /**
   * Trims the value and turns a lone decimal comma into a point. A value with several
   * commas is a list, not a number, and is left as-is so that it fails validation.
   */
  private static String normalizeDecimalSeparator(String value) {
    if (value == null) return null;
    var trimmed = value.trim();
    if (trimmed.isEmpty()) return null;
    if (trimmed.indexOf('.') < 0 && trimmed.indexOf(',') == trimmed.lastIndexOf(',')) {
      trimmed = trimmed.replace(',', '.');
    }
    return trimmed;
  }

  private static Double round(Double value) {
    if (value == null) return null;
    double factor = Math.pow(10, HEIGHT_DECIMALS);
    return Math.round(value * factor) / factor;
  }
}
