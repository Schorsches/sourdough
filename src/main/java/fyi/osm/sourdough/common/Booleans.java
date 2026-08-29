package fyi.osm.sourdough.common;

import com.onthegomap.planetiler.reader.WithTags;
import java.util.Set;

/** Shortbread 1.1 boolean attribute rules, shared by every layer that uses them. */
public final class Booleans {

  private Booleans() {}

  private static final Set<String> BRIDGE_VALUES = Set.of(
    "yes",
    "viaduct",
    "boardwalk",
    "cantilever",
    "covered",
    "low_water_crossing",
    "movable",
    "trestle"
  );

  private static final Set<String> TUNNEL_VALUES = Set.of("yes", "building_passage");

  private static final Set<String> ONEWAY_VALUES = Set.of("yes", "1", "true", "-1");

  /** true for tunnel=yes|building_passage or covered=yes. */
  public static boolean tunnel(WithTags sf) {
    var tunnel = sf.getString("tunnel");
    if (tunnel != null && TUNNEL_VALUES.contains(tunnel)) return true;
    return sf.hasTag("covered", "yes");
  }

  /** true for the eight bridge values the schema recognizes. */
  public static boolean bridge(WithTags sf) {
    var bridge = sf.getString("bridge");
    return bridge != null && BRIDGE_VALUES.contains(bridge);
  }

  /** true for oneway=yes|1|true|-1. */
  public static boolean oneway(WithTags sf) {
    var oneway = sf.getString("oneway");
    return oneway != null && ONEWAY_VALUES.contains(oneway);
  }

  /** true only for oneway=-1 (reverse, not reversible). */
  public static boolean onewayReverse(WithTags sf) {
    return sf.hasTag("oneway", "-1");
  }
}
