package fyi.osm.sourdough.shortbread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fyi.osm.sourdough.Schema;
import fyi.osm.sourdough.TestSupport;
import fyi.osm.sourdough.shortbread.buildings3d.BuildingDimensionParser;
import fyi.osm.sourdough.shortbread.buildings3d.BuildingMetrics;
import fyi.osm.sourdough.shortbread.layers.BuildingParts;
import fyi.osm.sourdough.shortbread.layers.Buildings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Layer tests for `buildings` and the 3D extension's `building_parts`. */
class BuildingsLayerTest {

  private static Buildings layer(Schema schema) {
    var config = ShortbreadConfiguration.defaults(schema);
    return new Buildings(config, parser(config));
  }

  private static BuildingDimensionParser parser(ShortbreadConfiguration config) {
    return new BuildingDimensionParser(config.levelHeight(), new BuildingMetrics());
  }

  @Test
  void buildingsAppearAtZoom14WithTheDummyProperty() {
    var feature = TestSupport.processOne(
      layer(Schema.SHORTBREAD),
      TestSupport.area(Map.of("building", "yes"))
    );
    assertEquals(ShortbreadSchema.BUILDINGS, feature.getLayer());
    assertEquals(14, feature.getMinZoom());
    assertEquals(Map.of("dummy", 1), feature.getAttrsAtZoom(14));
  }

  @Test
  void anyBuildingValueIsIncluded() {
    for (var value : List.of("yes", "house", "apartments", "retail", "church")) {
      assertEquals(
        1,
        TestSupport.process(layer(Schema.SHORTBREAD), TestSupport.area(Map.of("building", value)))
          .size(),
        "building=" + value + " should be included"
      );
    }
  }

  @Test
  void buildingNoIsExcluded() {
    assertTrue(
      TestSupport.process(layer(Schema.SHORTBREAD), TestSupport.area(Map.of("building", "no")))
        .isEmpty()
    );
  }

  @Test
  void buildingNodesAreNotIncluded() {
    // The schema says polygons only.
    assertTrue(
      TestSupport.process(layer(Schema.SHORTBREAD), TestSupport.node(Map.of("building", "yes")))
        .isEmpty()
    );
  }

  @Test
  void theBaseSchemaCarriesNoThreeDAttributes() {
    var feature = TestSupport.processOne(
      layer(Schema.SHORTBREAD),
      TestSupport.area(Map.of("building", "yes", "height", "12", "roof:shape", "gabled"))
    );
    var attrs = feature.getAttrsAtZoom(14);
    assertFalse(attrs.containsKey("height"));
    assertFalse(attrs.containsKey("roof_shape"));
  }

  @Test
  void theThreeDSchemaAddsHeightWhileKeepingDummy() {
    var feature = TestSupport.processOne(
      layer(Schema.SHORTBREAD_3D),
      TestSupport.area(Map.of("building", "yes", "height", "12", "roof:shape", "gabled"))
    );
    var attrs = feature.getAttrsAtZoom(14);
    assertEquals(1, attrs.get("dummy"));
    assertEquals(12.0, attrs.get("height"));
    assertEquals("gabled", attrs.get("roof_shape"));
    assertFalse(attrs.containsKey("height_estimated"), "an explicit height is not estimated");
  }

  @Test
  void levelDerivedHeightsAreMarked() {
    var feature = TestSupport.processOne(
      layer(Schema.SHORTBREAD_3D),
      TestSupport.area(Map.of("building", "yes", "building:levels", "4"))
    );
    var attrs = feature.getAttrsAtZoom(14);
    assertEquals(12.0, attrs.get("height"));
    assertEquals(true, attrs.get("height_estimated"));
    assertEquals(4, attrs.get("building_levels"));
  }

  @Test
  void aBuildingWithNoDimensionsGetsNoHeightAttribute() {
    var feature = TestSupport.processOne(
      layer(Schema.SHORTBREAD_3D),
      TestSupport.area(Map.of("building", "yes"))
    );
    assertNull(feature.getAttrsAtZoom(14).get("height"));
  }

  @Test
  void zeroMinHeightIsNotEmitted() {
    var feature = TestSupport.processOne(
      layer(Schema.SHORTBREAD_3D),
      TestSupport.area(Map.of("building", "yes", "height", "10"))
    );
    assertFalse(feature.getAttrsAtZoom(14).containsKey("min_height"));
  }

  // --- building_parts -------------------------------------------------------

  private static BuildingParts parts(Schema schema) {
    var config = ShortbreadConfiguration.defaults(schema);
    return new BuildingParts(config, parser(config), new BuildingMetrics());
  }

  @Test
  void partsAreNotEmittedByTheBaseSchema() {
    assertTrue(
      TestSupport.process(parts(Schema.SHORTBREAD), TestSupport.area(Map.of("building:part", "yes")))
        .isEmpty(),
      "building_parts must not exist in base Shortbread"
    );
  }

  @Test
  void partsGoToTheirOwnLayerNotIntoBuildings() {
    var feature = TestSupport.processOne(
      parts(Schema.SHORTBREAD_3D),
      TestSupport.area(Map.of("building:part", "yes", "height", "20", "min_height", "8"))
    );
    assertEquals(ShortbreadSchema.BUILDING_PARTS, feature.getLayer());
    var attrs = feature.getAttrsAtZoom(14);
    assertEquals(20.0, attrs.get("height"));
    assertEquals(8.0, attrs.get("min_height"));
    assertEquals("yes", attrs.get("building_part"));
  }

  @Test
  void aBuildingPartIsNotAlsoABuilding() {
    assertTrue(
      TestSupport.process(
        layer(Schema.SHORTBREAD_3D),
        TestSupport.area(Map.of("building:part", "yes", "height", "20"))
      ).isEmpty(),
      "a part without a building tag must not appear in the buildings layer"
    );
  }

  @Test
  void buildingPartNoIsExcluded() {
    assertTrue(
      TestSupport.process(
        parts(Schema.SHORTBREAD_3D),
        TestSupport.area(Map.of("building:part", "no"))
      ).isEmpty()
    );
  }

  @Test
  void elevatedPartsDeriveTheirBaseFromMinLevel() {
    var feature = TestSupport.processOne(
      parts(Schema.SHORTBREAD_3D),
      TestSupport.area(Map.of("building:part", "yes", "building:levels", "6", "building:min_level", "2"))
    );
    var attrs = feature.getAttrsAtZoom(14);
    assertEquals(18.0, attrs.get("height"));
    assertEquals(6.0, attrs.get("min_height"));
  }
}
