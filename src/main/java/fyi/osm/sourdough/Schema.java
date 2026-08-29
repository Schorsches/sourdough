package fyi.osm.sourdough;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The tile schemas this builder can produce.
 *
 * Each schema carries its own default maximum zoom. Sourdough builds to zoom 15;
 * Shortbread fixes the tileset maxzoom at 14 and expects clients to overzoom beyond
 * that. Keeping both defaults here means neither schema can inherit the other's by
 * accident.
 */
public enum Schema {
  /** The original Sourdough schema. Unchanged, and still the default. */
  SOURDOUGH("sourdough", 15),

  /** Shortbread 1.1 exactly as specified. */
  SHORTBREAD("shortbread-1.1", 14),

  /** Shortbread 1.1 plus the documented 3D-buildings extension. */
  SHORTBREAD_3D("shortbread-1.1-3d", 14),

  /**
   * The SmartMaps layer layout. No version suffix: unlike Shortbread there is no
   * published specification to pin, so a version number would imply a fidelity this
   * does not claim. See SMARTMAPS_SCHEMA.md.
   */
  SMARTMAPS("smartmaps", 14);

  private final String id;
  private final int defaultMaxzoom;

  Schema(String id, int defaultMaxzoom) {
    this.id = id;
    this.defaultMaxzoom = defaultMaxzoom;
  }

  public String id() {
    return id;
  }

  public int defaultMaxzoom() {
    return defaultMaxzoom;
  }

  /** True for the Shortbread schemas, which share a profile and a specification. */
  public boolean isShortbread() {
    return this == SHORTBREAD || this == SHORTBREAD_3D;
  }

  /**
   * True when the schema's maximum zoom is part of its definition rather than a
   * preference, so a higher --maxzoom is refused instead of quietly honoured. Both
   * Shortbread and SmartMaps stop at zoom 14 and expect the client to overzoom.
   */
  public boolean maxzoomIsFixed() {
    return this != SOURDOUGH;
  }

  /**
   * True when `name` is the OpenStreetMap name tag itself, so Sourdough's --language
   * substitution does not apply.
   */
  public boolean usesVerbatimNames() {
    return this != SOURDOUGH;
  }

  /** True when the 3D-buildings extension is enabled. */
  public boolean hasBuildings3d() {
    return this == SHORTBREAD_3D;
  }

  public static Schema fromId(String id) {
    for (var schema : values()) {
      if (schema.id.equals(id)) return schema;
    }
    throw new IllegalArgumentException(
      "Unknown schema '" + id + "'. Valid values are: " + ids()
    );
  }

  public static String ids() {
    return Arrays.stream(values()).map(Schema::id).collect(Collectors.joining(", "));
  }
}
