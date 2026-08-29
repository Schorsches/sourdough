package fyi.osm.sourdough.shortbread.buildings3d;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Counters for building-dimension data quality.
 *
 * Planet-scale OSM contains a lot of malformed dimension tagging, and silently dropping
 * it would make the output impossible to reason about. These counters are plain atomic
 * longs so that counting costs nothing measurable in the feature-processing hot path;
 * the profile logs them once at the end of a run.
 */
public final class BuildingMetrics {

  private final Map<String, AtomicLong> counters = new LinkedHashMap<>();

  public static final String HEIGHT_EXPLICIT = "buildings.height.explicit";
  public static final String HEIGHT_FROM_LEVELS = "buildings.height.from_levels";
  public static final String HEIGHT_ABSENT = "buildings.height.absent";
  public static final String HEIGHT_INVALID = "buildings.height.invalid";
  public static final String MIN_HEIGHT_INVALID = "buildings.min_height.invalid";
  public static final String ROOF_HEIGHT_INVALID = "buildings.roof_height.invalid";
  public static final String LEVELS_INVALID = "buildings.levels.invalid";
  public static final String BUILDING_PARTS_EMITTED = "building_parts.emitted";

  public BuildingMetrics() {
    for (var name : new String[] {
      HEIGHT_EXPLICIT,
      HEIGHT_FROM_LEVELS,
      HEIGHT_ABSENT,
      HEIGHT_INVALID,
      MIN_HEIGHT_INVALID,
      ROOF_HEIGHT_INVALID,
      LEVELS_INVALID,
      BUILDING_PARTS_EMITTED
    }) {
      counters.put(name, new AtomicLong());
    }
  }

  public void increment(String counter) {
    var value = counters.get(counter);
    if (value != null) {
      value.incrementAndGet();
    }
  }

  public long get(String counter) {
    var value = counters.get(counter);
    return value == null ? 0 : value.get();
  }

  /** A one-line summary for the end-of-run log. */
  public String summary() {
    var builder = new StringBuilder("building dimensions:");
    counters.forEach((name, value) -> builder.append(' ').append(name).append('=').append(value.get()));
    return builder.toString();
  }
}
