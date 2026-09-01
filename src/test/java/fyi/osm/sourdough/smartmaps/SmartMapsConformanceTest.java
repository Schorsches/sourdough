package fyi.osm.sourdough.smartmaps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.osm.OsmReader;
import com.onthegomap.planetiler.stats.Stats;
import fyi.osm.sourdough.TestSupport;
import fyi.osm.sourdough.common.BoundaryRelations;
import fyi.osm.sourdough.common.SchemaDescription.AttrType;
import fyi.osm.sourdough.common.SchemaDescription.Geometry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Lineal;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.Puntal;

/**
 * Checks generated features against the declarative table in {@link SmartMapsSchema}, the
 * same way {@code ShortbreadConformanceTest} does for Shortbread.
 *
 * Two separate things are being verified across the two SmartMaps suites, and it is worth
 * keeping them apart. {@code TileJsonConformanceTest} checks the table against the source
 * TileJSON -- that the layout as transcribed is the layout as published. This checks the
 * profile against the table -- that what the code emits is the layout as transcribed. Only
 * both together say the output is shape-compatible.
 *
 * The fixtures are a representative corpus, not exhaustive coverage; per-layer behaviour
 * is covered by {@code SmartMapsLayerTest}.
 */
class SmartMapsConformanceTest {

  private static final PlanetilerConfig CONFIG = PlanetilerConfig.from(
    Arguments.of("maxzoom", Integer.toString(SmartMapsSchema.MAXZOOM))
  );

  /** Features covering at least one case in every layer OSM data can produce. */
  private static List<SourceFeature> fixtures() {
    var fixtures = new ArrayList<SourceFeature>();
    fixtures.add(TestSupport.area(Map.of("natural", "water", "name", "Lake"), 0.01));
    fixtures.add(
      TestSupport.longWay(Map.of("waterway", "river", "name", "River", "intermittent", "yes"))
    );
    fixtures.add(TestSupport.longWay(Map.of("waterway", "drain", "tunnel", "yes")));
    fixtures.add(
      TestSupport.area(
        Map.of("boundary", "administrative", "admin_level", "2", "name", "Country"),
        0.3
      )
    );
    fixtures.add(
      TestSupport.wayInRelations(
        Map.of("boundary", "administrative"),
        List.of(
          new OsmReader.RelationMember<>(
            "outer",
            new BoundaryRelations.BoundaryRelation(1, 2, false)
          )
        )
      )
    );
    fixtures.add(TestSupport.node(Map.of("place", "city", "name", "City", "population", "500000")));
    fixtures.add(TestSupport.area(Map.of("landuse", "forest"), 0.01));
    fixtures.add(TestSupport.area(Map.of("natural", "sand"), 0.01));
    fixtures.add(
      TestSupport.area(Map.of("amenity", "school", "name", "School", "ele", "120"), 0.001)
    );
    fixtures.add(
      TestSupport.area(
        Map.of(
          "building", "yes",
          "height", "12",
          "roof:shape", "gabled",
          "name", "Hall",
          "addr:housenumber", "1"
        ),
        0.0005
      )
    );
    fixtures.add(
      TestSupport.area(Map.of("building:part", "yes", "height", "20", "min_height", "8"), 0.0005)
    );
    fixtures.add(TestSupport.node(Map.of("addr:housenumber", "12", "addr:housename", "Villa")));
    fixtures.add(
      TestSupport.longWay(
        Map.of(
          "highway", "motorway",
          "name", "A1",
          "ref", "A1;E15",
          "network", "e-road",
          "oneway", "yes",
          "bridge", "yes",
          "surface", "asphalt",
          "bicycle", "no"
        )
      )
    );
    fixtures.add(TestSupport.longWay(Map.of("railway", "rail", "service", "siding")));
    fixtures.add(
      TestSupport.longWay(Map.of("highway", "construction", "construction", "residential"))
    );
    fixtures.add(TestSupport.area(Map.of("highway", "pedestrian", "name", "Square"), 0.001));
    fixtures.add(
      TestSupport.node(Map.of("highway", "motorway_junction", "ref", "12", "name", "Exit"))
    );
    fixtures.add(
      TestSupport.node(
        Map.of("railway", "station", "name", "Station", "station", "subway", "ele", "35")
      )
    );
    fixtures.add(
      TestSupport.area(Map.of("aeroway", "aerodrome", "name", "Airport", "iata", "LUX"), 0.01)
    );
    fixtures.add(
      TestSupport.node(
        Map.of(
          "amenity", "restaurant",
          "name", "Cafe",
          "cuisine", "italian",
          "addr:housenumber", "3"
        )
      )
    );
    fixtures.add(TestSupport.node(Map.of("amenity", "recycling", "recycling:paper", "yes")));
    fixtures.add(TestSupport.node(Map.of("amenity", "bank", "atm", "yes")));
    return fixtures;
  }

  private static List<FeatureCollector.Feature> generate() {
    var profile = new SmartMapsProfile(SmartMapsConfiguration.defaults());
    var factory = new FeatureCollector.Factory(CONFIG, Stats.inMemory());
    var features = new ArrayList<FeatureCollector.Feature>();
    for (var fixture : fixtures()) {
      var collector = factory.get(fixture);
      profile.processFeature(fixture, collector);
      collector.forEach(features::add);
    }
    return features;
  }

  @Test
  void everyEmittedFeatureBelongsToALayerTheLayoutDefines() {
    var known = new HashSet<>(SmartMapsSchema.layerNames());
    for (var feature : generate()) {
      assertTrue(
        known.contains(feature.getLayer()),
        "layer '" + feature.getLayer() + "' is not part of the SmartMaps layout"
      );
    }
  }

  @Test
  void everyEmittedFeatureUsesAGeometryItsLayerAllows() {
    for (var feature : generate()) {
      var spec = SmartMapsSchema.layer(feature.getLayer());
      assertTrue(
        spec.geometries().contains(geometryOf(feature)),
        feature.getLayer() + " emitted a " + geometryOf(feature) + " but allows " +
        spec.geometries()
      );
    }
  }

  @Test
  void everyEmittedAttributeIsDefinedWithTheRightType() {
    var problems = new ArrayList<String>();
    for (var feature : generate()) {
      var spec = SmartMapsSchema.layer(feature.getLayer());
      for (int zoom = spec.minZoom(); zoom <= SmartMapsSchema.MAXZOOM; zoom++) {
        feature.getAttrsAtZoom(zoom).forEach((key, value) -> {
          var type = spec.attributes().get(key);
          if (type == null) {
            problems.add(feature.getLayer() + "." + key + " is not defined by the layout");
          } else if (value != null && !matches(type, value)) {
            problems.add(
              feature.getLayer() + "." + key + " should be " + type + " but was " +
              value.getClass().getSimpleName()
            );
          }
        });
      }
    }
    if (!problems.isEmpty()) {
      fail(String.join("\n", new TreeSet<>(problems)));
    }
  }

  /**
   * The reverse of the check above, and the one that was missing.
   *
   * {@code everyEmittedAttributeIsDefinedWithTheRightType} proves emitted is a subset of
   * declared. Nothing proved the other direction, so an attribute could be declared in the
   * table, pass TileJsonConformanceTest against the source document, and never be produced
   * by any handler -- with no failure anywhere. That is the same silent-success shape as
   * the post-processing bug recorded in CLAUDE.md.
   *
   * Booleans are checked rather than every attribute because they are the ones with a
   * total contract: a string is absent when the OSM tag is absent, but a boolean is always
   * either true or false, so the layout emits it on every feature in the layer. Checked at
   * MAXZOOM, where the zoom-scoped ones on `transport` are present.
   */
  @Test
  void everyDeclaredBooleanIsPresentOnEveryFeature() {
    var problems = new ArrayList<String>();
    for (var feature : generate()) {
      var attrs = feature.getAttrsAtZoom(SmartMapsSchema.MAXZOOM);
      for (var key : SmartMapsSchema.booleanAttributes(feature.getLayer())) {
        if (!attrs.containsKey(key)) {
          problems.add(feature.getLayer() + "." + key + " is declared but not emitted");
        }
      }
    }
    if (!problems.isEmpty()) {
      fail(String.join("\n", new TreeSet<>(problems)));
    }
  }

  @Test
  void noFeatureAppearsBelowItsLayersMinimumZoom() {
    for (var feature : generate()) {
      var spec = SmartMapsSchema.layer(feature.getLayer());
      assertTrue(
        feature.getMinZoom() >= spec.minZoom(),
        feature.getLayer() + " emitted at zoom " + feature.getMinZoom() +
        " but the layout's minimum is " + spec.minZoom()
      );
    }
  }

  @Test
  void noFeatureIsBuiltBeyondTheLayoutMaximumZoom() {
    for (var feature : generate()) {
      assertTrue(
        feature.getMaxZoom() <= SmartMapsSchema.MAXZOOM,
        feature.getLayer() + " is built to zoom " + feature.getMaxZoom()
      );
    }
  }

  @Test
  void theFixtureCorpusReachesEveryLayer() {
    var produced = new HashSet<String>();
    for (var feature : generate()) {
      produced.add(feature.getLayer());
    }
    var missing = new TreeSet<>(SmartMapsSchema.layerNames());
    missing.removeAll(produced);
    assertEquals(Set.of(), missing, "no fixture produces these layers");
  }

  /**
   * Ocean is the one layer no OSM fixture can reach: it comes from the preprocessed
   * coastline shapefile. It is still reachable through the source handler, and
   * water_polygons is where this layout has to put it.
   */
  @Test
  void oceanFromTheCoastlineShapefileLandsInWaterPolygons() {
    var layer = new fyi.osm.sourdough.smartmaps.layers.WaterPolygons(
      SmartMapsConfiguration.defaults()
    );
    var source = TestSupport.area(Map.of(), 0.5);
    var collector = new FeatureCollector.Factory(CONFIG, Stats.inMemory()).get(source);
    layer.processPreparedOcean(source, collector);

    var features = new ArrayList<FeatureCollector.Feature>();
    collector.forEach(features::add);
    assertEquals(1, features.size());
    var ocean = features.get(0);
    assertEquals(SmartMapsSchema.WATER_POLYGONS, ocean.getLayer());
    assertEquals(0, ocean.getMinZoom());
    assertEquals(
      fyi.osm.sourdough.smartmaps.layers.WaterPolygons.OCEAN_KIND,
      ocean.getAttrsAtZoom(0).get("kind")
    );
    // Ocean is also the one path everyDeclaredBooleanIsPresentOnEveryFeature cannot see,
    // because it is not reached from an OSM fixture, so the layer's boolean contract is
    // checked here instead.
    for (var key : SmartMapsSchema.booleanAttributes(SmartMapsSchema.WATER_POLYGONS)) {
      assertTrue(
        ocean.getAttrsAtZoom(0).containsKey(key),
        "ocean is missing the declared boolean " + key
      );
    }
  }

  /**
   * The feature's geometry class, read from the geometry itself: Planetiler's own
   * getGeometryType() is package-private, and the JTS type is what ends up in the tile.
   */
  private static Geometry geometryOf(FeatureCollector.Feature feature) {
    var geometry = feature.getGeometry();
    if (geometry instanceof Puntal) return Geometry.POINT;
    if (geometry instanceof Lineal) return Geometry.LINE;
    if (geometry instanceof Polygonal) return Geometry.POLYGON;
    throw new AssertionError("unexpected geometry " + geometry.getGeometryType());
  }

  private static boolean matches(AttrType type, Object value) {
    return switch (type) {
      case STRING -> value instanceof String;
      case INTEGER -> value instanceof Integer || value instanceof Long;
      case FLOAT -> value instanceof Double || value instanceof Float;
      case BOOLEAN -> value instanceof Boolean;
    };
  }
}
