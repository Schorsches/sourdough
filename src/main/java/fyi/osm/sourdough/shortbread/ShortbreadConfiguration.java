package fyi.osm.sourdough.shortbread;

import fyi.osm.sourdough.Schema;
import java.util.List;

/**
 * Runtime options for the Shortbread profile.
 *
 * @param schema which Shortbread variant is being built
 * @param languages IETF codes emitted as `name_xx`, already expanded from any preset
 * @param levelHeight assumed height in meters of one above-ground building level
 * @param estimateMissingHeights whether buildings with no dimensions in OpenStreetMap get
 *     a height estimated from their building type. Estimates are always marked with
 *     `height_estimated` so a consumer can tell them from measured values.
 */
public record ShortbreadConfiguration(
  Schema schema,
  List<String> languages,
  double levelHeight,
  boolean estimateMissingHeights
) {

  /** The documented default assumption for one above-ground building level. */
  public static final double DEFAULT_LEVEL_HEIGHT_METERS = 3.0;

  /** Most buildings have no dimensions at all, so a usable map needs an estimate. */
  public static final boolean DEFAULT_ESTIMATE_MISSING_HEIGHTS = true;

  public ShortbreadConfiguration {
    languages = languages == null ? List.of() : List.copyOf(languages);
  }

  public static ShortbreadConfiguration defaults() {
    return defaults(Schema.SHORTBREAD);
  }

  public static ShortbreadConfiguration defaults(Schema schema) {
    return new ShortbreadConfiguration(
      schema,
      List.of(),
      DEFAULT_LEVEL_HEIGHT_METERS,
      DEFAULT_ESTIMATE_MISSING_HEIGHTS
    );
  }

  /** True when the 3D-buildings extension should be emitted. */
  public boolean hasBuildings3d() {
    return schema.hasBuildings3d();
  }
}
