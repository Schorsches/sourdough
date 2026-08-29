package fyi.osm.sourdough.shortbread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.stats.Stats;
import fyi.osm.sourdough.Schema;
import fyi.osm.sourdough.TestSupport;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Checks generated features against the declarative schema in {@link ShortbreadSchema}.
 *
 * The point of driving this from the same table the documentation is written from is
 * that the schema is stated once. A future Shortbread version is adopted by editing the
 * table; this test then reports what is missing or wrong.
 *
 * The fixtures below are a representative corpus rather than exhaustive coverage; the
 * per-layer tests cover selection rules in detail.
 */
class ShortbreadConformanceTest {

  private static final PlanetilerConfig CONFIG = PlanetilerConfig.from(
    Arguments.of("maxzoom", Integer.toString(ShortbreadSchema.MAXZOOM))
  );

  /** Features covering at least one case in every layer that OSM data can produce. */
  private static List<SourceFeature> fixtures() {
    var fixtures = new ArrayList<SourceFeature>();
    fixtures.add(TestSupport.area(Map.of("natural", "water", "name", "Lake"), 0.01));
    fixtures.add(TestSupport.area(Map.of("natural", "glacier"), 0.01));
    fixtures.add(TestSupport.longWay(Map.of("waterway", "river", "name", "River", "layer", "-1")));
    fixtures.add(TestSupport.longWay(Map.of("waterway", "drain", "name", "Drain")));
    fixtures.add(TestSupport.longWay(Map.of("waterway", "dam")));
    fixtures.add(TestSupport.area(Map.of("waterway", "dam"), 0.001));
    fixtures.add(TestSupport.longWay(Map.of("man_made", "pier")));
    fixtures.add(TestSupport.area(Map.of("man_made", "breakwater"), 0.001));
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
          new com.onthegomap.planetiler.reader.osm.OsmReader.RelationMember<>(
            "outer",
            new fyi.osm.sourdough.shortbread.layers.Boundaries.BoundaryRelation(1, 2, false)
          )
        )
      )
    );
    fixtures.add(TestSupport.node(Map.of("place", "city", "name", "City", "population", "500000")));
    fixtures.add(TestSupport.node(Map.of("place", "town", "name", "Capital", "capital", "yes")));
    fixtures.add(TestSupport.area(Map.of("landuse", "forest"), 0.01));
    fixtures.add(TestSupport.area(Map.of("wetland", "marsh"), 0.01));
    fixtures.add(TestSupport.area(Map.of("amenity", "school"), 0.001));
    fixtures.add(TestSupport.area(Map.of("building", "yes", "height", "12"), 0.0005));
    fixtures.add(TestSupport.node(Map.of("addr:housenumber", "12", "addr:housename", "Villa")));
    fixtures.add(
      TestSupport.longWay(
        Map.of(
          "highway", "motorway",
          "name", "A1",
          "ref", "A1;E15",
          "oneway", "yes",
          "bridge", "yes",
          "surface", "asphalt",
          "motorcar", "yes"
        )
      )
    );
    fixtures.add(TestSupport.longWay(Map.of("railway", "rail", "service", "siding")));
    fixtures.add(TestSupport.area(Map.of("highway", "pedestrian", "name", "Square"), 0.001));
    fixtures.add(TestSupport.node(Map.of("highway", "motorway_junction", "ref", "12", "name", "Exit")));
    fixtures.add(TestSupport.area(Map.of("man_made", "bridge"), 0.001));
    fixtures.add(TestSupport.longWay(Map.of("aerialway", "rope_tow")));
    fixtures.add(TestSupport.longWay(Map.of("route", "ferry", "name", "Ferry")));
    fixtures.add(TestSupport.node(Map.of("railway", "station", "name", "Station")));
    fixtures.add(
      TestSupport.node(
        Map.of("amenity", "restaurant", "name", "Cafe", "cuisine", "italian", "addr:housenumber", "3")
      )
    );
    fixtures.add(TestSupport.node(Map.of("amenity", "recycling", "recycling:paper", "yes")));
    return fixtures;
  }

  private static List<FeatureCollector.Feature> generate(Schema schema) {
    var profile = new ShortbreadProfile(ShortbreadConfiguration.defaults(schema));
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
  void everyEmittedFeatureBelongsToALayerTheSchemaDefines() {
    var known = new HashSet<>(ShortbreadSchema.layerNames());
    for (var feature : generate(Schema.SHORTBREAD)) {
      assertTrue(
        known.contains(feature.getLayer()),
        "layer '" + feature.getLayer() + "' is not part of Shortbread " +
        ShortbreadSchema.SPEC_VERSION
      );
    }
  }

  @Test
  void everyEmittedFeatureUsesItsLayersGeometryType() {
    for (var feature : generate(Schema.SHORTBREAD)) {
      var spec = ShortbreadSchema.layer(feature.getLayer());
      assertEquals(
        spec.geometry(),
        geometryOf(feature),
        "wrong geometry type in layer " + feature.getLayer()
      );
    }
  }

  @Test
  void everyEmittedAttributeIsDefinedWithTheRightType() {
    var problems = new ArrayList<String>();
    for (var feature : generate(Schema.SHORTBREAD)) {
      var spec = ShortbreadSchema.layer(feature.getLayer());
      for (int zoom = spec.minZoom(); zoom <= ShortbreadSchema.MAXZOOM; zoom++) {
        feature.getAttrsAtZoom(zoom).forEach((key, value) -> {
          var type = spec.attributes().get(key);
          if (type == null) {
            problems.add(feature.getLayer() + "." + key + " is not defined by the schema");
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

  @Test
  void noFeatureAppearsBelowItsLayersMinimumZoom() {
    for (var feature : generate(Schema.SHORTBREAD)) {
      var spec = ShortbreadSchema.layer(feature.getLayer());
      assertTrue(
        feature.getMinZoom() >= spec.minZoom(),
        feature.getLayer() + " emitted at zoom " + feature.getMinZoom() +
        " but the schema's minimum is " + spec.minZoom()
      );
    }
  }

  @Test
  void noFeatureIsBuiltBeyondTheSchemaMaximumZoom() {
    for (var feature : generate(Schema.SHORTBREAD)) {
      assertTrue(
        feature.getMaxZoom() <= ShortbreadSchema.MAXZOOM,
        feature.getLayer() + " is built to zoom " + feature.getMaxZoom()
      );
    }
  }

  @Test
  void theBaseSchemaNeverEmitsTheThreeDExtension() {
    for (var feature : generate(Schema.SHORTBREAD)) {
      assertTrue(
        !feature.getLayer().equals(ShortbreadSchema.BUILDING_PARTS),
        "building_parts must not exist in base Shortbread"
      );
      var attrs = feature.getAttrsAtZoom(ShortbreadSchema.MAXZOOM);
      for (var key : ShortbreadSchema.BUILDINGS_3D_ATTRIBUTES.keySet()) {
        assertTrue(
          !attrs.containsKey(key),
          "3D attribute " + key + " leaked into base Shortbread layer " + feature.getLayer()
        );
      }
    }
  }

  @Test
  void theThreeDSchemaStillSatisfiesTheBaseSchema() {
    var known = new HashSet<>(ShortbreadSchema.layerNames());
    known.add(ShortbreadSchema.BUILDING_PARTS);
    for (var feature : generate(Schema.SHORTBREAD_3D)) {
      assertTrue(known.contains(feature.getLayer()), "unexpected layer " + feature.getLayer());
      if (feature.getLayer().equals(ShortbreadSchema.BUILDING_PARTS)) continue;
      var spec = ShortbreadSchema.layer(feature.getLayer());
      assertEquals(spec.geometry(), geometryOf(feature));
      assertTrue(feature.getMinZoom() >= spec.minZoom());
    }
  }

  @Test
  void theFixtureCorpusReachesMostOfTheSchema() {
    var produced = new HashSet<String>();
    for (var feature : generate(Schema.SHORTBREAD)) {
      produced.add(feature.getLayer());
    }
    // The ocean comes from the coastline shapefile rather than OSM, so it cannot appear
    // in a corpus of OSM fixtures.
    var expected = new TreeSet<>(ShortbreadSchema.layerNames());
    expected.remove(ShortbreadSchema.OCEAN);
    var missing = new TreeSet<>(expected);
    missing.removeAll(produced);
    assertEquals(Set.of(), missing, "no fixture produces these layers");
  }

  /**
   * The feature's geometry class, read from the geometry itself. Planetiler's own
   * getGeometryType() is package-private, and the JTS type is what actually ends up in
   * the tile anyway.
   */
  private static fyi.osm.sourdough.common.SchemaDescription.Geometry geometryOf(FeatureCollector.Feature feature) {
    var geometry = feature.getGeometry();
    if (geometry instanceof org.locationtech.jts.geom.Puntal) return fyi.osm.sourdough.common.SchemaDescription.Geometry.POINT;
    if (geometry instanceof org.locationtech.jts.geom.Lineal) return fyi.osm.sourdough.common.SchemaDescription.Geometry.LINE;
    if (geometry instanceof org.locationtech.jts.geom.Polygonal) {
      return fyi.osm.sourdough.common.SchemaDescription.Geometry.POLYGON;
    }
    throw new AssertionError("unexpected geometry " + geometry.getGeometryType());
  }

  private static boolean matches(fyi.osm.sourdough.common.SchemaDescription.AttrType type, Object value) {
    return switch (type) {
      case STRING -> value instanceof String;
      case INTEGER -> value instanceof Integer || value instanceof Long;
      case FLOAT -> value instanceof Double || value instanceof Float;
      case BOOLEAN -> value instanceof Boolean;
    };
  }
}
