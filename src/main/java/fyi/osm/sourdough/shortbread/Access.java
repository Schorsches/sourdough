package fyi.osm.sourdough.shortbread;

import com.onthegomap.planetiler.reader.WithTags;
import java.util.List;
import java.util.Map;

/**
 * Shortbread 1.1 access-tag normalization for the `streets` layer.
 *
 * OSM access values are collapsed to exactly `yes`, `limited` or `no`. Values outside
 * the mapping are "removed and not considered", meaning evaluation falls through to the
 * next key in the priority chain rather than yielding nothing.
 */
public final class Access {

  private Access() {}

  private static final Map<String, String> VALUES = Map.ofEntries(
    Map.entry("yes", "yes"),
    Map.entry("designated", "yes"),
    Map.entry("permissive", "yes"),
    Map.entry("customers", "limited"),
    Map.entry("destination", "limited"),
    Map.entry("agricultural", "limited"),
    Map.entry("forestry", "limited"),
    Map.entry("delivery", "limited"),
    Map.entry("discouraged", "limited"),
    Map.entry("permit", "limited"),
    Map.entry("dismount", "no"),
    Map.entry("military", "no"),
    Map.entry("private", "no"),
    Map.entry("no", "no")
  );

  public static final List<String> MOTORCAR = List.of("motorcar", "motor_vehicle", "vehicle", "access");
  public static final List<String> BICYCLE = List.of("bicycle", "vehicle", "access");
  public static final List<String> FOOT = List.of("foot", "access");
  public static final List<String> HORSE = List.of("horse", "access");

  /**
   * Returns the normalized access value for the given priority chain, or null when no
   * key in the chain carries a recognized value.
   */
  public static String evaluate(WithTags sf, List<String> chain) {
    for (var key : chain) {
      var value = sf.getString(key);
      if (value == null) continue;
      var mapped = VALUES.get(value);
      if (mapped != null) return mapped;
      // Unrecognized value: "removed and not considered", so keep looking.
    }
    return null;
  }
}
