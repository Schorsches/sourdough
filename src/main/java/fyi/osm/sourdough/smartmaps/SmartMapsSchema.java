package fyi.osm.sourdough.smartmaps;

import fyi.osm.sourdough.common.SchemaDescription.AttrType;
import fyi.osm.sourdough.common.SchemaDescription.Geometry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The declarative description of the SmartMaps layer layout.
 *
 * Derived from the TileJSON for the "SmartMaps Planet" tileset, vendored at
 * src/test/resources/smartmaps/tiles.json and asserted against by
 * TileJsonConformanceTest.
 *
 * Two things about that source document govern how it is used here.
 *
 * It gives layer names, field names and field types, and nothing else: no OSM selection
 * rules, no `kind` vocabularies. So this implementation is shape-compatible -- a SmartMaps
 * style finds every layer and field it expects -- while the classification behind those
 * fields is this project's own. Individual `kind` values may differ from the published
 * tileset.
 *
 * And its contents are observed from sampled tiles rather than declared. Language coverage
 * varies per layer in a way nobody configures by hand (41 codes on place_label, 8 on
 * building, 2 on landcover), and its zoom ranges contradict each other: water_polygons is
 * z14 only while water_label, which labels those polygons, runs z0-14. The field lists and
 * zoom ranges are therefore a lower bound, and the zooms here follow the Shortbread rules
 * instead. Every such deviation is listed in SMARTMAPS_SCHEMA.md.
 */
public final class SmartMapsSchema {

  private SmartMapsSchema() {}

  /** Version of this layout as implemented here, not a SmartMaps release number. */
  public static final String SCHEMA_VERSION = "1.0";

  /** SmartMaps tiles stop at zoom 14; clients overzoom above it. */
  public static final int MAXZOOM = 14;

  /**
   * @param name the tile layer name
   * @param geometries every geometry type features in this layer may have. SmartMaps
   *     merges layers Shortbread keeps apart, so several carry more than one.
   * @param minZoom the lowest zoom at which any feature of this layer can appear
   * @param attributes attribute name to expected type, excluding name variants
   */
  public record LayerSpec(
    String name,
    Set<Geometry> geometries,
    int minZoom,
    Map<String, AttrType> attributes
  ) {
    public LayerSpec {
      geometries = Set.copyOf(geometries);
      attributes = Map.copyOf(attributes);
    }
  }

  public static final String PLACE_LABEL = "place_label";
  public static final String BOUNDARY = "boundary";
  public static final String TRANSPORT = "transport";
  public static final String TRANSPORT_LABEL = "transport_label";
  public static final String HOUSENUMBER_LABEL = "housenumber_label";
  public static final String WATER_LINES = "water_lines";
  public static final String WATER_POLYGONS = "water_polygons";
  public static final String WATER_LABEL = "water_label";
  public static final String LANDCOVER = "landcover";
  public static final String LANDUSE = "landuse";
  public static final String BUILDING = "building";
  public static final String POI = "poi";

  private static final AttrType S = AttrType.STRING;
  private static final AttrType I = AttrType.INTEGER;
  private static final AttrType F = AttrType.FLOAT;
  private static final AttrType B = AttrType.BOOLEAN;

  private static final Set<Geometry> POINT = Set.of(Geometry.POINT);
  private static final Set<Geometry> LINE = Set.of(Geometry.LINE);
  private static final Set<Geometry> POLYGON = Set.of(Geometry.POLYGON);

  private static Map<String, AttrType> attrs(Object... pairs) {
    var map = new LinkedHashMap<String, AttrType>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put((String) pairs[i], (AttrType) pairs[i + 1]);
    }
    return map;
  }

  /** Every layer in the SmartMaps layout. */
  public static final List<LayerSpec> LAYERS = List.of(
    new LayerSpec(PLACE_LABEL, POINT, 4, attrs("kind", S, "name", S, "population", I)),
    new LayerSpec(
      BOUNDARY,
      LINE,
      0,
      attrs("admin_level", I, "disputed", B, "maritime", B)
    ),
    // Merges Shortbread's streets, street_polygons and public_transport, so all three
    // geometries occur. The stops are what the station, iata and icao fields below are
    // for, which is what settles that they belong in this layer and not in poi.
    new LayerSpec(
      TRANSPORT,
      Set.of(Geometry.LINE, Geometry.POLYGON, Geometry.POINT),
      5,
      attrs(
        "kind", S,
        "link", B,
        "rail", B,
        "tunnel", B,
        "bridge", B,
        "oneway", B,
        "oneway_reverse", B,
        "service", S,
        "surface", S,
        "tracktype", S,
        "construction", S,
        "bicycle", S,
        "horse", S,
        "ele", F,
        "ele_ft", F,
        "iata", S,
        "icao", S,
        "station", S,
        "name", S
      )
    ),
    // Merges Shortbread's street_labels and street_labels_points.
    new LayerSpec(
      TRANSPORT_LABEL,
      Set.of(Geometry.LINE, Geometry.POINT),
      10,
      attrs(
        "kind", S,
        "name", S,
        "network", S,
        "ref", S,
        "ref_rows", I,
        "ref_cols", I,
        "ref_prefix", S,
        "ref_org", S,
        "tunnel", B
      )
    ),
    new LayerSpec(
      HOUSENUMBER_LABEL,
      POINT,
      14,
      attrs("housename", S, "housenumber", S)
    ),
    new LayerSpec(
      WATER_LINES,
      LINE,
      9,
      attrs("kind", S, "tunnel", B, "bridge", B, "intermittent", B)
    ),
    // Ocean lands here as kind=ocean: the TileJSON has no ocean layer, and dropping
    // coastlines would leave the map broken at low zoom.
    new LayerSpec(
      WATER_POLYGONS,
      POLYGON,
      0,
      attrs("kind", S, "name", S, "intermittent", B, "way_area", F)
    ),
    // Merges Shortbread's water_polygons_labels and water_lines_labels.
    new LayerSpec(
      WATER_LABEL,
      Set.of(Geometry.POINT, Geometry.LINE),
      4,
      attrs("kind", S, "name", S, "tunnel", B, "bridge", B, "way_area", F)
    ),
    new LayerSpec(
      LANDCOVER,
      POLYGON,
      7,
      attrs("kind", S, "name", S, "boundary", S, "maritime", B, "way_area", F)
    ),
    new LayerSpec(
      LANDUSE,
      POLYGON,
      10,
      attrs(
        "kind", S,
        "name", S,
        "boundary", S,
        "maritime", B,
        "amenity", S,
        "landuse", S,
        "leisure", S,
        "tourism", S,
        "sport", S,
        "housenumber", S,
        "ele", F,
        "ele_ft", F,
        "way_area", F
      )
    ),
    // Merges Shortbread's buildings and building_parts behind a building:part flag, which
    // is what this layout specifies. Shortbread keeps them apart for a different reason;
    // see BUILDINGS_3D.md.
    new LayerSpec(
      BUILDING,
      POLYGON,
      14,
      attrs(
        "render_height", F,
        "render_min_height", F,
        "3d", B,
        "roof", B,
        "building:part", B,
        "name", S,
        "housename", S,
        "housenumber", S,
        "amenity", S,
        "shop", S,
        "leisure", S,
        "tourism", S,
        "historic", S,
        "man_made", S,
        "information", S,
        "religion", S,
        "denomination", S,
        "cuisine", S,
        "sport", S,
        "atm", B
      )
    ),
    new LayerSpec(POI, POINT, 14, poiAttributes())
  );

  private static Map<String, AttrType> poiAttributes() {
    var map = new LinkedHashMap<String, AttrType>();
    map.put("kind", S);
    map.put("name", S);
    for (var key : List.of(
      "amenity",
      "leisure",
      "tourism",
      "shop",
      "man_made",
      "historic",
      "emergency",
      "highway",
      "office",
      "landuse"
    )) {
      map.put(key, S);
    }
    map.put("housename", S);
    map.put("housenumber", S);
    for (var key : List.of(
      "cuisine",
      "sport",
      "vending",
      "information",
      "tower:type",
      "religion",
      "denomination"
    )) {
      map.put(key, S);
    }
    map.put("ele", F);
    map.put("ele_ft", F);
    for (var key : List.of(
      "recycling:glass_bottles",
      "recycling:paper",
      "recycling:clothes",
      "recycling:scrap_metal"
    )) {
      map.put(key, B);
    }
    map.put("atm", B);
    return map;
  }

  /**
   * The boolean attributes a layer declares.
   *
   * These are emitted on every feature in the layer, true or false, rather than only where
   * true -- the one deviation from this repository's omit-rather-than-default convention,
   * recorded with its reason in SMARTMAPS_SCHEMA.md. Derived from the table above so there
   * is no second list to keep in step, and driven off by the conformance test that holds
   * the handlers to it.
   */
  public static List<String> booleanAttributes(String name) {
    return layer(name)
      .attributes()
      .entrySet()
      .stream()
      .filter(e -> e.getValue() == AttrType.BOOLEAN)
      .map(Map.Entry::getKey)
      .sorted()
      .toList();
  }

  public static LayerSpec layer(String name) {
    return LAYERS.stream()
      .filter(l -> l.name().equals(name))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("no such SmartMaps layer: " + name));
  }

  public static List<String> layerNames() {
    return LAYERS.stream().map(LayerSpec::name).toList();
  }
}
