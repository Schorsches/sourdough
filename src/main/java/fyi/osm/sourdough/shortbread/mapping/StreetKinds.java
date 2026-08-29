package fyi.osm.sourdough.shortbread.mapping;

import com.onthegomap.planetiler.reader.WithTags;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Classification of the road, rail and aeroway network for the street layers.
 *
 * Note that `streets` and `street_labels` encode link roads differently: `streets` uses
 * the base kind (`motorway`) plus a `link` boolean, while `street_labels` uses
 * `motorway_link` as a distinct kind. Both are produced from the same lookup here so the
 * difference stays in one place.
 */
public final class StreetKinds {

  private StreetKinds() {}

  /**
   * @param kind the base `kind` value for the streets layer
   * @param minZoom lowest zoom for the streets layer
   * @param labelMinZoom lowest zoom for the street_labels layer
   * @param link whether this is a link road
   * @param rail whether this is a railway
   */
  public record Street(String kind, int minZoom, int labelMinZoom, boolean link, boolean rail) {
    /** The kind used by street_labels, which spells link roads out. */
    public String labelKind() {
      return link ? kind + "_link" : kind;
    }
  }

  private static final Map<String, Street> HIGHWAY = buildHighway();
  private static final Map<String, Street> AEROWAY = Map.of(
    "runway", new Street("runway", 11, 11, false, false),
    "taxiway", new Street("taxiway", 13, 13, false, false)
  );
  private static final Map<String, Street> RAILWAY = buildRailway();

  private static Map<String, Street> buildHighway() {
    var map = new LinkedHashMap<String, Street>();
    // Main road classes, each with a matching link class.
    addRoadWithLink(map, "motorway", 5, 10, 13);
    addRoadWithLink(map, "trunk", 6, 12, 13);
    addRoadWithLink(map, "primary", 8, 12, 13);
    addRoadWithLink(map, "secondary", 9, 13, 13);
    addRoadWithLink(map, "tertiary", 10, 13, 14);
    map.put("unclassified", new Street("unclassified", 12, 14, false, false));
    map.put("residential", new Street("residential", 12, 14, false, false));
    map.put("busway", new Street("busway", 12, 14, false, false));
    map.put("bus_guideway", new Street("bus_guideway", 12, 14, false, false));
    map.put("living_street", new Street("living_street", 13, 14, false, false));
    map.put("service", new Street("service", 13, 14, false, false));
    map.put("pedestrian", new Street("pedestrian", 13, 14, false, false));
    map.put("track", new Street("track", 13, 14, false, false));
    map.put("footway", new Street("footway", 13, 14, false, false));
    map.put("steps", new Street("steps", 13, 14, false, false));
    map.put("path", new Street("path", 13, 14, false, false));
    map.put("cycleway", new Street("cycleway", 13, 14, false, false));
    return Map.copyOf(map);
  }

  private static void addRoadWithLink(
    Map<String, Street> map,
    String kind,
    int minZoom,
    int labelMinZoom,
    int linkLabelMinZoom
  ) {
    map.put(kind, new Street(kind, minZoom, labelMinZoom, false, false));
    map.put(kind + "_link", new Street(kind, minZoom, linkLabelMinZoom, true, false));
  }

  private static Map<String, Street> buildRailway() {
    var map = new LinkedHashMap<String, Street>();
    // Rail and narrow gauge appear at zoom 8, or 10 when they are service tracks.
    map.put("rail", new Street("rail", 8, 10, false, true));
    map.put("narrow_gauge", new Street("narrow_gauge", 8, 10, false, true));
    map.put("tram", new Street("tram", 10, 10, false, true));
    map.put("light_rail", new Street("light_rail", 10, 10, false, true));
    map.put("funicular", new Street("funicular", 10, 10, false, true));
    map.put("subway", new Street("subway", 10, 10, false, true));
    map.put("monorail", new Street("monorail", 10, 10, false, true));
    return Map.copyOf(map);
  }

  /** Railways whose minzoom rises to 10 when the way carries a `service` tag. */
  private static final Set<String> SERVICE_SENSITIVE_RAIL = Set.of("rail", "narrow_gauge");

  private static final int SERVICE_RAIL_MIN_ZOOM = 10;

  public static List<String> highwayValues() {
    return List.copyOf(HIGHWAY.keySet());
  }

  public static List<String> aerowayValues() {
    return List.copyOf(AEROWAY.keySet());
  }

  public static List<String> railwayValues() {
    return List.copyOf(RAILWAY.keySet());
  }

  /** Classifies a way, or returns null if it is not part of the street network. */
  public static Street lookup(WithTags sf) {
    var highway = sf.getString("highway");
    if (highway != null) {
      var street = HIGHWAY.get(highway);
      if (street != null) return street;
    }
    var aeroway = sf.getString("aeroway");
    if (aeroway != null) {
      var street = AEROWAY.get(aeroway);
      if (street != null) return street;
    }
    var railway = sf.getString("railway");
    if (railway != null) {
      var street = RAILWAY.get(railway);
      if (street != null) return street;
    }
    return null;
  }

  /** The minzoom for a way, accounting for the service-track rule on railways. */
  public static int minZoom(WithTags sf, Street street) {
    if (street.rail() && SERVICE_SENSITIVE_RAIL.contains(street.kind()) && sf.hasTag("service")) {
      return SERVICE_RAIL_MIN_ZOOM;
    }
    return street.minZoom();
  }
}
