package fyi.osm.sourdough.shortbread;

import fyi.osm.sourdough.common.SchemaDescription.AttrType;
import fyi.osm.sourdough.common.SchemaDescription.Geometry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The declarative description of Shortbread 1.1.
 *
 * This table is the single source of truth for the schema: the conformance test asserts
 * generated tiles against it, and the documentation is written from it. Adopting a
 * future Shortbread 1.x means re-pinning the revision below and editing this table,
 * after which the conformance test reports what still needs implementing.
 *
 * Pinned specification revision:
 *   https://github.com/shortbread-tiles/shortbread-docs
 *   commit fc5c602c84a48cc6189b3bdbb36b9ed8abf57924 (2026-08-18)
 *   retrieved 2026-08-28
 *
 * Note that the specification has no v1.1 git tag, so the commit hash is the only
 * immutable identifier available.
 */
public final class ShortbreadSchema {

  private ShortbreadSchema() {}

  public static final String SPEC_VERSION = "1.1";
  public static final String SPEC_REVISION = "fc5c602c84a48cc6189b3bdbb36b9ed8abf57924";
  public static final String SPEC_URL = "https://shortbread-tiles.org/schema/1.1/";

  /** Every Shortbread layer is built to this zoom; clients overzoom above it. */
  public static final int MAXZOOM = 14;


  /**
   * @param name the tile layer name
   * @param geometry the only geometry type features in this layer may have
   * @param minZoom the lowest zoom at which any feature of this layer can appear
   * @param attributes attribute name to expected type
   */
  public record LayerSpec(
    String name,
    Geometry geometry,
    int minZoom,
    Map<String, AttrType> attributes
  ) {
    public LayerSpec {
      attributes = Map.copyOf(attributes);
    }
  }

  // Layer names, so that layer classes and tests never spell them as loose strings.
  public static final String OCEAN = "ocean";
  public static final String WATER_POLYGONS = "water_polygons";
  public static final String WATER_POLYGONS_LABELS = "water_polygons_labels";
  public static final String WATER_LINES = "water_lines";
  public static final String WATER_LINES_LABELS = "water_lines_labels";
  public static final String DAM_LINES = "dam_lines";
  public static final String DAM_POLYGONS = "dam_polygons";
  public static final String PIER_LINES = "pier_lines";
  public static final String PIER_POLYGONS = "pier_polygons";
  public static final String BOUNDARIES = "boundaries";
  public static final String BOUNDARY_LABELS = "boundary_labels";
  public static final String PLACE_LABELS = "place_labels";
  public static final String LAND = "land";
  public static final String SITES = "sites";
  public static final String BUILDINGS = "buildings";
  public static final String ADDRESSES = "addresses";
  public static final String STREETS = "streets";
  public static final String STREET_POLYGONS = "street_polygons";
  public static final String STREET_LABELS = "street_labels";
  public static final String STREETS_POLYGONS_LABELS = "streets_polygons_labels";
  public static final String STREET_LABELS_POINTS = "street_labels_points";
  public static final String BRIDGES = "bridges";
  public static final String AERIALWAYS = "aerialways";
  public static final String FERRIES = "ferries";
  public static final String PUBLIC_TRANSPORT = "public_transport";
  public static final String POIS = "pois";

  /** The layer added by the 3D extension. It is not part of Shortbread 1.1. */
  public static final String BUILDING_PARTS = "building_parts";

  private static Map<String, AttrType> attrs(Object... pairs) {
    var map = new LinkedHashMap<String, AttrType>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put((String) pairs[i], (AttrType) pairs[i + 1]);
    }
    return map;
  }

  private static final AttrType S = AttrType.STRING;
  private static final AttrType I = AttrType.INTEGER;
  private static final AttrType F = AttrType.FLOAT;
  private static final AttrType B = AttrType.BOOLEAN;

  /** Every layer defined by Shortbread 1.1, in specification order. */
  public static final List<LayerSpec> LAYERS = List.of(
    new LayerSpec(OCEAN, Geometry.POLYGON, 0, Map.of()),
    new LayerSpec(WATER_POLYGONS, Geometry.POLYGON, 4, attrs("kind", S, "way_area", F)),
    new LayerSpec(
      WATER_POLYGONS_LABELS,
      Geometry.POINT,
      4,
      attrs("kind", S, "way_area", F, "name", S)
    ),
    new LayerSpec(WATER_LINES, Geometry.LINE, 9, attrs("kind", S, "tunnel", B, "bridge", B)),
    new LayerSpec(
      WATER_LINES_LABELS,
      Geometry.LINE,
      12,
      attrs("kind", S, "name", S, "tunnel", B, "bridge", B)
    ),
    new LayerSpec(DAM_LINES, Geometry.LINE, 12, attrs("kind", S)),
    new LayerSpec(DAM_POLYGONS, Geometry.POLYGON, 12, attrs("kind", S)),
    new LayerSpec(PIER_LINES, Geometry.LINE, 12, attrs("kind", S)),
    new LayerSpec(PIER_POLYGONS, Geometry.POLYGON, 12, attrs("kind", S)),
    new LayerSpec(
      BOUNDARIES,
      Geometry.LINE,
      0,
      attrs("admin_level", I, "maritime", B, "disputed", B)
    ),
    new LayerSpec(
      BOUNDARY_LABELS,
      Geometry.POINT,
      2,
      attrs("admin_level", I, "way_area", F, "name", S)
    ),
    new LayerSpec(PLACE_LABELS, Geometry.POINT, 4, attrs("kind", S, "name", S, "population", I)),
    new LayerSpec(LAND, Geometry.POLYGON, 7, attrs("kind", S)),
    new LayerSpec(SITES, Geometry.POLYGON, 14, attrs("kind", S)),
    new LayerSpec(BUILDINGS, Geometry.POLYGON, 14, attrs("dummy", I)),
    new LayerSpec(ADDRESSES, Geometry.POINT, 14, attrs("housename", S, "housenumber", S)),
    new LayerSpec(
      STREETS,
      Geometry.LINE,
      5,
      attrs(
        "kind", S,
        "link", B,
        "rail", B,
        "tunnel", B,
        "bridge", B,
        "oneway", B,
        "oneway_reverse", B,
        "tracktype", S,
        "surface", S,
        "service", S,
        "motorcar", S,
        "bicycle", S,
        "foot", S,
        "horse", S
      )
    ),
    new LayerSpec(
      STREET_POLYGONS,
      Geometry.POLYGON,
      11,
      attrs(
        "kind", S,
        "bridge", B,
        "rail", B,
        "service", S,
        "surface", S,
        "tunnel", B
      )
    ),
    new LayerSpec(
      STREET_LABELS,
      Geometry.LINE,
      10,
      attrs(
        "kind", S,
        "ref", S,
        "ref_rows", I,
        "ref_cols", I,
        "name", S,
        "tunnel", B
      )
    ),
    new LayerSpec(STREETS_POLYGONS_LABELS, Geometry.POINT, 14, attrs("kind", S, "name", S)),
    new LayerSpec(
      STREET_LABELS_POINTS,
      Geometry.POINT,
      12,
      attrs("kind", S, "ref", S, "name", S)
    ),
    new LayerSpec(BRIDGES, Geometry.POLYGON, 12, attrs("kind", S)),
    new LayerSpec(AERIALWAYS, Geometry.LINE, 12, attrs("kind", S)),
    new LayerSpec(FERRIES, Geometry.LINE, 10, attrs("kind", S, "name", S)),
    new LayerSpec(
      PUBLIC_TRANSPORT,
      Geometry.POINT,
      11,
      attrs("kind", S, "name", S, "iata", S)
    ),
    new LayerSpec(POIS, Geometry.POINT, 14, poiAttributes())
  );

  private static Map<String, AttrType> poiAttributes() {
    var map = new LinkedHashMap<String, AttrType>();
    // Key properties: set only when the OSM value is one the schema selects.
    for (var key : List.of(
      "amenity",
      "leisure",
      "tourism",
      "shop",
      "man_made",
      "historic",
      "emergency",
      "highway",
      "office"
    )) {
      map.put(key, S);
    }
    map.put("name", S);
    map.put("housename", S);
    map.put("housenumber", S);
    // Conditional string properties.
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
    // Conditional boolean properties.
    for (var key : List.of(
      "recycling:glass_bottles",
      "recycling:paper",
      "recycling:clothes",
      "recycling:scrap_metal",
      "atm"
    )) {
      map.put(key, B);
    }
    return map;
  }

  /** The 3D extension's attributes on `buildings` and `building_parts`. */
  public static final Map<String, AttrType> BUILDINGS_3D_ATTRIBUTES = Map.ofEntries(
    Map.entry("height", AttrType.FLOAT),
    Map.entry("min_height", AttrType.FLOAT),
    Map.entry("height_estimated", AttrType.BOOLEAN),
    Map.entry("building_levels", AttrType.INTEGER),
    Map.entry("roof_height", AttrType.FLOAT),
    Map.entry("roof_shape", AttrType.STRING),
    Map.entry("roof_direction", AttrType.FLOAT),
    Map.entry("roof_orientation", AttrType.STRING),
    Map.entry("building_colour", AttrType.STRING),
    Map.entry("building_material", AttrType.STRING),
    Map.entry("roof_colour", AttrType.STRING),
    Map.entry("roof_material", AttrType.STRING)
  );

  public static LayerSpec layer(String name) {
    return LAYERS.stream()
      .filter(l -> l.name().equals(name))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("no such Shortbread layer: " + name));
  }

  public static List<String> layerNames() {
    return LAYERS.stream().map(LayerSpec::name).toList();
  }
}
