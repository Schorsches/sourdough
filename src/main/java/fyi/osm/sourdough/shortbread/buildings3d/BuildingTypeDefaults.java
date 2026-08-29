package fyi.osm.sourdough.shortbread.buildings3d;

import java.util.Map;

/**
 * Assumed storey counts by building type, used only when OpenStreetMap says nothing about
 * a building's dimensions.
 *
 * Three quarters of buildings in even a well-mapped city carry neither a height nor a
 * level count, so a renderer needs some number for them. A single global constant would
 * make a garden shed the same height as an apartment block; the building type is the one
 * piece of information that is essentially always present, and it separates those cases
 * cheaply.
 *
 * These are deliberately conservative, round estimates of typical construction, not data.
 * A building whose type is not listed falls back to {@link #DEFAULT_LEVELS}. Counts are in
 * storeys rather than metres so they compose with the configured level height.
 */
public final class BuildingTypeDefaults {

  private BuildingTypeDefaults() {}

  /**
   * Used for `building=yes`, which is the most common value by a wide margin and says
   * nothing, and for any type not listed below. Two storeys is a deliberately modest
   * guess: too-tall defaults across a whole city read worse than too-short ones.
   */
  public static final double DEFAULT_LEVELS = 2;

  private static final Map<String, Double> LEVELS_BY_TYPE = Map.ofEntries(
    // Single-storey outbuildings and small structures.
    Map.entry("garage", 1.0),
    Map.entry("garages", 1.0),
    Map.entry("shed", 1.0),
    Map.entry("hut", 1.0),
    Map.entry("carport", 1.0),
    Map.entry("roof", 1.0),
    Map.entry("cabin", 1.0),
    Map.entry("kiosk", 1.0),
    Map.entry("greenhouse", 1.0),
    Map.entry("stable", 1.0),
    Map.entry("sty", 1.0),
    Map.entry("cowshed", 1.0),
    Map.entry("container", 1.0),
    Map.entry("boathouse", 1.0),
    Map.entry("allotment_house", 1.0),
    Map.entry("service", 1.0),
    Map.entry("bungalow", 1.0),
    Map.entry("static_caravan", 1.0),

    // Houses and farm buildings.
    Map.entry("house", 2.0),
    Map.entry("detached", 2.0),
    Map.entry("semidetached_house", 2.0),
    Map.entry("terrace", 2.0),
    Map.entry("farm", 2.0),
    Map.entry("farm_auxiliary", 1.0),
    Map.entry("barn", 2.0),

    // Low commercial and civic buildings.
    Map.entry("retail", 3.0),
    Map.entry("supermarket", 2.0),
    Map.entry("commercial", 3.0),
    Map.entry("industrial", 2.0),
    Map.entry("warehouse", 2.0),
    Map.entry("school", 3.0),
    Map.entry("kindergarten", 2.0),
    Map.entry("civic", 3.0),
    Map.entry("public", 3.0),
    Map.entry("train_station", 2.0),
    Map.entry("sports_hall", 2.0),

    // Multi-storey buildings.
    Map.entry("apartments", 4.0),
    Map.entry("residential", 3.0),
    Map.entry("dormitory", 4.0),
    Map.entry("office", 4.0),
    Map.entry("hotel", 4.0),
    Map.entry("hospital", 4.0),
    Map.entry("university", 4.0),
    Map.entry("college", 3.0),
    Map.entry("government", 4.0),

    // Places of worship, which are tall for their storey count.
    Map.entry("church", 4.0),
    Map.entry("cathedral", 6.0),
    Map.entry("chapel", 2.0),
    Map.entry("mosque", 3.0),
    Map.entry("synagogue", 3.0),
    Map.entry("temple", 3.0)
  );

  /**
   * The assumed storey count for a building or building-part value. Never returns null:
   * an unrecognized or absent type falls back to {@link #DEFAULT_LEVELS}.
   */
  public static double levelsFor(String buildingValue) {
    if (buildingValue == null) return DEFAULT_LEVELS;
    return LEVELS_BY_TYPE.getOrDefault(buildingValue, DEFAULT_LEVELS);
  }

  /** True when the type carries its own estimate rather than the global fallback. */
  public static boolean hasSpecificEstimate(String buildingValue) {
    return buildingValue != null && LEVELS_BY_TYPE.containsKey(buildingValue);
  }
}
