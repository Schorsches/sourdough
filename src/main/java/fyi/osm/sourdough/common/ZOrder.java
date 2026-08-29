package fyi.osm.sourdough.common;

import com.onthegomap.planetiler.reader.WithTags;
import com.onthegomap.planetiler.util.Parse;
import com.onthegomap.planetiler.util.SortKey;
import java.util.List;
import java.util.Map;

/**
 * Feature ordering for the `streets` and `street_polygons` layers.
 *
 * Shortbread 1.1: "Features are ordered by the so-called z-order value which is computed
 * from road class, OSM layer, bridge and tunnel tags. More important roads are sorted
 * before less important roads, tunnels before bridges."
 *
 * Planetiler emits features in ascending sort-key order, so the ordering here runs from
 * the lowest structure and most important class upward: OSM `layer` first, then
 * tunnel before ground level before bridge, then road class with motorways first.
 */
public final class ZOrder {

  private ZOrder() {}

  /** OSM `layer` values outside this range are clamped; they are not meaningful data. */
  private static final int MIN_LAYER = -5;
  private static final int MAX_LAYER = 5;

  private static final int STRUCTURE_TUNNEL = 0;
  private static final int STRUCTURE_GROUND = 1;
  private static final int STRUCTURE_BRIDGE = 2;

  /**
   * Road classes in descending importance. The index is the rank, so motorways sort
   * first. Anything unlisted sorts last.
   */
  private static final List<String> CLASS_ORDER = List.of(
    "motorway",
    "trunk",
    "primary",
    "secondary",
    "tertiary",
    "unclassified",
    "residential",
    "busway",
    "bus_guideway",
    "living_street",
    "pedestrian",
    "service",
    "track",
    "cycleway",
    "footway",
    "steps",
    "path",
    "runway",
    "taxiway",
    "rail",
    "narrow_gauge",
    "tram",
    "light_rail",
    "funicular",
    "subway",
    "monorail"
  );

  private static final Map<String, Integer> CLASS_RANK = buildRanks();

  private static Map<String, Integer> buildRanks() {
    var ranks = new java.util.HashMap<String, Integer>();
    for (int i = 0; i < CLASS_ORDER.size(); i++) {
      ranks.put(CLASS_ORDER.get(i), i);
    }
    return Map.copyOf(ranks);
  }

  /** The rank of a `kind` value; unknown kinds sort after every known one. */
  public static int classRank(String kind) {
    return CLASS_RANK.getOrDefault(kind, CLASS_ORDER.size());
  }

  static int layerValue(WithTags sf) {
    Integer layer = Parse.parseIntOrNull(sf.getString("layer"));
    if (layer == null) return 0;
    return Math.clamp(layer, MIN_LAYER, MAX_LAYER);
  }

  static int structure(WithTags sf) {
    if (Booleans.tunnel(sf)) return STRUCTURE_TUNNEL;
    if (Booleans.bridge(sf)) return STRUCTURE_BRIDGE;
    return STRUCTURE_GROUND;
  }

  /** The Planetiler sort key for a street feature of the given `kind`. */
  public static int sortKey(WithTags sf, String kind) {
    return SortKey
      .orderByInt(layerValue(sf), MIN_LAYER, MAX_LAYER)
      .thenByInt(structure(sf), STRUCTURE_TUNNEL, STRUCTURE_BRIDGE)
      .thenByInt(classRank(kind), 0, CLASS_ORDER.size())
      .get();
  }
}
