package fyi.osm.sourdough.shortbread;

import fyi.osm.sourdough.Schema;
import java.util.List;

/**
 * Runtime options for the Shortbread profile.
 *
 * @param schema which Shortbread variant is being built
 * @param languages IETF codes emitted as `name_xx`, already expanded from any preset
 * @param levelHeight assumed height in meters of one above-ground building level
 */
public record ShortbreadConfiguration(Schema schema, List<String> languages, double levelHeight) {

  /** The documented default assumption for one above-ground building level. */
  public static final double DEFAULT_LEVEL_HEIGHT_METERS = 3.0;

  public ShortbreadConfiguration {
    languages = languages == null ? List.of() : List.copyOf(languages);
  }

  public static ShortbreadConfiguration defaults() {
    return new ShortbreadConfiguration(Schema.SHORTBREAD, List.of(), DEFAULT_LEVEL_HEIGHT_METERS);
  }

  public static ShortbreadConfiguration defaults(Schema schema) {
    return new ShortbreadConfiguration(schema, List.of(), DEFAULT_LEVEL_HEIGHT_METERS);
  }

  /** True when the 3D-buildings extension should be emitted. */
  public boolean hasBuildings3d() {
    return schema.hasBuildings3d();
  }
}
