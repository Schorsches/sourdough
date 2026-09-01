package fyi.osm.sourdough.smartmaps.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.WithTags;
import fyi.osm.sourdough.common.Access;
import fyi.osm.sourdough.common.Booleans;
import fyi.osm.sourdough.common.Elevation;
import fyi.osm.sourdough.common.ZOrder;
import fyi.osm.sourdough.common.mapping.StreetKinds;
import fyi.osm.sourdough.smartmaps.SmartMapsConfiguration;
import fyi.osm.sourdough.smartmaps.SmartMapsLayer;
import fyi.osm.sourdough.smartmaps.SmartMapsNames;
import fyi.osm.sourdough.smartmaps.SmartMapsSchema;
import java.util.List;
import java.util.Map;

/**
 * SmartMaps `transport`: the whole movement network in one layer.
 *
 * Where Shortbread has three layers -- `streets` (lines), `street_polygons` (areas) and
 * `public_transport` (stops and airports as points) -- this layout has one, so all three
 * feed it. The `station` and `iata`/`icao` fields in the layout only make sense for the
 * third of those, which is what settles that they belong here rather than in `poi`.
 *
 * Two fields differ from Shortbread's street model and are noted where they are set:
 * access is limited to `bicycle` and `horse`, and `construction` is a string naming what
 * is being built rather than a flag.
 */
public class Transport extends SmartMapsLayer implements ForwardingProfile.LayerPostProcessor {

  public Transport(SmartMapsConfiguration config) {
    super(config);
  }

  // Zooms at which each group of attributes starts being carried, as in Shortbread:
  // low-zoom tiles get only what a small-scale map draws with.
  private static final int DETAIL_MIN_ZOOM = 11;
  private static final int ACCESS_MIN_ZOOM = 13;
  private static final int ONEWAY_MIN_ZOOM = 14;
  private static final int CONSTRUCTION_MIN_ZOOM = 12;

  /** The value both `highway` and `railway` take while a way is being built. */
  private static final String CONSTRUCTION = "construction";

  /** Polygon street kinds and the zoom each appears at, as in Shortbread. */
  private static final Map<String, Integer> HIGHWAY_AREA_KINDS = Map.of(
    "pedestrian", 14,
    "service", 14
  );

  private static final Map<String, Integer> AEROWAY_AREA_KINDS = Map.of(
    "runway", 11,
    "taxiway", 13
  );

  /**
   * Stops and airports, with the zoom each appears at. `kind` names the stop; the layout's
   * separate `station` field carries the OSM `station` tag (`subway`, `light_rail`, ...),
   * which is a refinement of it rather than a synonym.
   */
  private record Stop(String kind, int minZoom) {}

  private static final Map<String, Map<String, Stop>> STOPS = Map.of(
    "aeroway",
    Map.of("aerodrome", new Stop("aerodrome", 11), "helipad", new Stop("helipad", 13)),
    "amenity",
    Map.of(
      "ferry_terminal", new Stop("ferry_terminal", 12),
      "bus_station", new Stop("bus_station", 13)
    ),
    "railway",
    Map.of(
      "station", new Stop("station", 13),
      "halt", new Stop("halt", 13),
      "tram_stop", new Stop("tram_stop", 14)
    ),
    "aerialway",
    Map.of("station", new Stop("aerialway_station", 13)),
    "highway",
    Map.of("bus_stop", new Stop("bus_stop", 14))
  );

  @Override
  public String name() {
    return SmartMapsSchema.TRANSPORT;
  }

  @Override
  public Expression filter() {
    var expressions = new java.util.ArrayList<Expression>();
    expressions.add(Expression.matchAny("highway", StreetKinds.highwayValues()));
    expressions.add(Expression.matchAny("aeroway", StreetKinds.aerowayValues()));
    expressions.add(Expression.matchAny("railway", StreetKinds.railwayValues()));
    expressions.add(Expression.matchAny("area:aeroway", List.copyOf(AEROWAY_AREA_KINDS.keySet())));
    expressions.add(Expression.matchAny("highway", CONSTRUCTION));
    expressions.add(Expression.matchAny("railway", CONSTRUCTION));
    STOPS.forEach((key, values) ->
      expressions.add(Expression.matchAny(key, List.copyOf(values.keySet())))
    );
    return Expression.or(expressions);
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    // One feature can qualify as more than one of these -- a pedestrian square with a bus
    // stop mapped on it is both an area and a stop -- so each is considered in turn,
    // exactly as the three Shortbread handlers this merges would each have run.
    if (sf.canBeLine()) {
      processLine(sf, fc);
    }
    if (sf.canBePolygon()) {
      processArea(sf, fc);
    }
    processStop(sf, fc);
  }

  private void processLine(SourceFeature sf, FeatureCollector fc) {
    boolean underConstruction = isUnderConstruction(sf);
    var street = underConstruction
      ? StreetKinds.byClassName(sf.getString("construction"))
      : StreetKinds.lookup(sf);
    if (street == null) return;

    var line = fc.line(name());
    // A road being built is not a road yet, so it never appears earlier than the class it
    // will become, and never before the zoom at which a map is showing local detail.
    line.setMinZoom(
      underConstruction
        ? Math.max(CONSTRUCTION_MIN_ZOOM, street.minZoom())
        : StreetKinds.minZoom(sf, street)
    );
    line.setMinPixelSize(0);
    line.setBufferPixels(4);
    line.setSortKey(ZOrder.sortKey(sf, street.kind()));

    line.setAttr("kind", street.kind());
    // Present either way round rather than only when true; see SMARTMAPS_SCHEMA.md. The
    // zoom-scoped ones keep their minzoom: below it neither true nor false is emitted,
    // because claiming `tunnel=false` for a tunnel that is merely not drawn yet would be
    // wrong in a way an absent attribute is not.
    line.setAttr("rail", street.rail());
    line.setAttrWithMinzoom("link", street.link(), DETAIL_MIN_ZOOM);
    line.setAttrWithMinzoom("tunnel", Booleans.tunnel(sf), DETAIL_MIN_ZOOM);
    line.setAttrWithMinzoom("bridge", Booleans.bridge(sf), DETAIL_MIN_ZOOM);

    setStringIfPresent(sf, line, "tracktype", DETAIL_MIN_ZOOM);
    setStringIfPresent(sf, line, "surface", DETAIL_MIN_ZOOM);
    setStringIfPresent(sf, line, "service", DETAIL_MIN_ZOOM);

    // `construction` is a string here, not the boolean Shortbread would use: the layout
    // types it as a string, and the useful thing to say is what is being built. Shortbread
    // omits ways under construction entirely; this layout has a field for them, so they
    // are carried, tagged with the class they will become.
    if (underConstruction) {
      setStringIfPresent(sf, line, "construction", 0);
    }

    // Oneway stays a road concept here: a railway never reports oneway=true even where OSM
    // tags one, which railNeverCarriesOneway pins. It still carries the field as false,
    // because every feature in this layer carries every boolean the layer declares.
    boolean road = !street.rail();
    line.setAttrWithMinzoom("oneway", road && Booleans.oneway(sf), ONEWAY_MIN_ZOOM);
    line.setAttrWithMinzoom(
      "oneway_reverse",
      road && Booleans.onewayReverse(sf),
      ONEWAY_MIN_ZOOM
    );

    // The layout has bicycle and horse access and no motorcar or foot, so only those two
    // are evaluated. Access is a highway concept; rail and aeroways never carry it.
    if (sf.hasTag("highway")) {
      setAccess(sf, line, "bicycle", Access.BICYCLE);
      setAccess(sf, line, "horse", Access.HORSE);
    }
  }

  private void processArea(SourceFeature sf, FeatureCollector fc) {
    var kind = areaKind(sf);
    if (kind == null) return;

    var polygon = fc.polygon(name());
    polygon.setMinZoom(areaMinZoom(kind));
    polygon.setMinPixelSize(0.5);
    polygon.setBufferPixels(8);
    polygon.setSortKey(ZOrder.sortKey(sf, kind));
    polygon.setAttr("kind", kind);

    polygon.setAttr("tunnel", Booleans.tunnel(sf));
    polygon.setAttr("bridge", Booleans.bridge(sf));
    // The remaining transport booleans describe a line's direction and role, which an area
    // does not have. They are still emitted, because the layer's contract is that every
    // feature carries every declared boolean.
    polygon.setAttr("rail", false);
    polygon.setAttr("link", false);
    polygon.setAttr("oneway", false);
    polygon.setAttr("oneway_reverse", false);
    setStringIfPresent(sf, polygon, "service", 0);
    setStringIfPresent(sf, polygon, "surface", 0);
    SmartMapsNames.setNames(sf, polygon, config.languages());
  }

  private void processStop(SourceFeature sf, FeatureCollector fc) {
    var stop = lookupStop(sf);
    if (stop == null) return;

    FeatureCollector.Feature point;
    if (sf.isPoint()) {
      point = fc.point(name());
    } else if (sf.canBePolygon()) {
      point = fc.pointOnSurface(name());
    } else {
      return;
    }

    point.setMinZoom(stop.minZoom());
    point.setBufferPixels(16);
    point.setAttr("kind", stop.kind());
    // A stop can genuinely be under a bridge or in a tunnel, so those are read from tags.
    // The rest describe a line and are false for a point.
    point.setAttr("tunnel", Booleans.tunnel(sf));
    point.setAttr("bridge", Booleans.bridge(sf));
    point.setAttr("rail", false);
    point.setAttr("link", false);
    point.setAttr("oneway", false);
    point.setAttr("oneway_reverse", false);
    SmartMapsNames.setNames(sf, point, config.languages());

    setStringIfPresent(sf, point, "iata", 0);
    setStringIfPresent(sf, point, "icao", 0);
    setStringIfPresent(sf, point, "station", 0);

    var meters = Elevation.meters(sf);
    if (meters != null) {
      point.setAttr("ele", meters);
      point.setAttr("ele_ft", Elevation.feet(meters));
    }
  }

  private static boolean isUnderConstruction(WithTags sf) {
    return sf.hasTag("highway", CONSTRUCTION) || sf.hasTag("railway", CONSTRUCTION);
  }

  /** The polygon kind, or null when the feature is not a street or aeroway area. */
  static String areaKind(WithTags sf) {
    var highway = sf.getString("highway");
    if (highway != null && HIGHWAY_AREA_KINDS.containsKey(highway)) return highway;
    var aeroway = sf.getString("area:aeroway");
    if (aeroway != null && AEROWAY_AREA_KINDS.containsKey(aeroway)) return aeroway;
    return null;
  }

  private static int areaMinZoom(String kind) {
    var highway = HIGHWAY_AREA_KINDS.get(kind);
    return highway != null ? highway : AEROWAY_AREA_KINDS.get(kind);
  }

  private static Stop lookupStop(WithTags sf) {
    for (var entry : STOPS.entrySet()) {
      var value = sf.getString(entry.getKey());
      if (value == null) continue;
      var stop = entry.getValue().get(value);
      if (stop != null) return stop;
    }
    return null;
  }

  private static void setAccess(
    SourceFeature sf,
    FeatureCollector.Feature line,
    String attribute,
    List<String> chain
  ) {
    var value = Access.evaluate(sf, chain);
    if (value != null) {
      line.setAttrWithMinzoom(attribute, value, ACCESS_MIN_ZOOM);
    }
  }

  private static void setStringIfPresent(
    SourceFeature sf,
    FeatureCollector.Feature feature,
    String key,
    int minZoom
  ) {
    var value = sf.getString(key);
    if (value != null) {
      feature.setAttrWithMinzoom(key, value, minZoom);
    }
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items)
    throws GeometryException {
    // Never drop short segments: physical detail tags break roads into pieces, and
    // dropping any of them leaves visible gaps in the network.
    return FeatureMerge.mergeLineStrings(items, 0, 0.25, 4);
  }
}
