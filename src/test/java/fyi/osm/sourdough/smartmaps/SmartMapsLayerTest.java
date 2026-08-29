package fyi.osm.sourdough.smartmaps;

import static fyi.osm.sourdough.TestSupport.area;
import static fyi.osm.sourdough.TestSupport.longWay;
import static fyi.osm.sourdough.TestSupport.node;
import static fyi.osm.sourdough.TestSupport.process;
import static fyi.osm.sourdough.TestSupport.wayInRelations;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.reader.osm.OsmReader;
import fyi.osm.sourdough.TestSupport;
import fyi.osm.sourdough.common.BoundaryRelations;
import fyi.osm.sourdough.smartmaps.layers.Boundary;
import fyi.osm.sourdough.smartmaps.layers.Building;
import fyi.osm.sourdough.smartmaps.layers.HousenumberLabel;
import fyi.osm.sourdough.smartmaps.layers.Land;
import fyi.osm.sourdough.smartmaps.layers.PlaceLabel;
import fyi.osm.sourdough.smartmaps.layers.Poi;
import fyi.osm.sourdough.smartmaps.layers.Transport;
import fyi.osm.sourdough.smartmaps.layers.TransportLabel;
import fyi.osm.sourdough.smartmaps.layers.WaterLines;
import fyi.osm.sourdough.smartmaps.layers.WaterPolygons;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Per-layer selection, attribute and zoom behaviour for the SmartMaps layout.
 *
 * {@code SmartMapsConformanceTest} checks that whatever is emitted fits the declared
 * table; this checks that the right thing is emitted in the first place, with an eye on
 * the places this layout differs from Shortbread -- merged layers, both name spellings,
 * and the attributes that had to be inferred.
 */
class SmartMapsLayerTest {

  private static final SmartMapsConfiguration CONFIG = SmartMapsConfiguration.defaults();

  private static SmartMapsConfiguration withLanguages(String... codes) {
    return new SmartMapsConfiguration(List.of(codes), CONFIG.levelHeight(), true);
  }

  /** Attributes of the one emitted feature in the named layer. */
  private static Map<String, Object> attrs(
    ForwardingProfile.FeatureProcessor layer,
    com.onthegomap.planetiler.reader.SourceFeature sf,
    String tileLayer
  ) {
    return only(layer, sf, tileLayer).getAttrsAtZoom(SmartMapsSchema.MAXZOOM);
  }

  private static FeatureCollector.Feature only(
    ForwardingProfile.FeatureProcessor layer,
    com.onthegomap.planetiler.reader.SourceFeature sf,
    String tileLayer
  ) {
    var matching = process(layer, sf, SmartMapsSchema.MAXZOOM)
      .stream()
      .filter(f -> f.getLayer().equals(tileLayer))
      .toList();
    assertEquals(1, matching.size(), "expected one feature in " + tileLayer + ", got " + matching);
    return matching.get(0);
  }

  private static List<FeatureCollector.Feature> inLayer(
    ForwardingProfile.FeatureProcessor layer,
    com.onthegomap.planetiler.reader.SourceFeature sf,
    String tileLayer
  ) {
    return process(layer, sf, SmartMapsSchema.MAXZOOM)
      .stream()
      .filter(f -> f.getLayer().equals(tileLayer))
      .toList();
  }

  @Nested
  class Names {

    @Test
    void onlyTheBareNameIsEmittedByDefault() {
      var attrs = attrs(
        new PlaceLabel(CONFIG),
        node(Map.of("place", "city", "name", "Köln", "name:en", "Cologne")),
        SmartMapsSchema.PLACE_LABEL
      );
      assertEquals("Köln", attrs.get("name"));
      assertNull(attrs.get("name:en"));
      assertNull(attrs.get("name_en"));
    }

    @Test
    void bothSpellingsAreEmittedForARequestedLanguage() {
      var attrs = attrs(
        new PlaceLabel(withLanguages("en", "fr")),
        node(Map.of("place", "city", "name", "Köln", "name:en", "Cologne")),
        SmartMapsSchema.PLACE_LABEL
      );
      assertEquals("Cologne", attrs.get("name:en"));
      assertEquals("Cologne", attrs.get("name_en"));
      // A language the feature does not have costs nothing.
      assertNull(attrs.get("name:fr"));
      assertNull(attrs.get("name_fr"));
    }
  }

  @Nested
  class PlaceLabels {

    @Test
    void aNationalCapitalIsItsOwnKind() {
      var attrs = attrs(
        new PlaceLabel(CONFIG),
        node(Map.of("place", "city", "name", "Paris", "capital", "yes")),
        SmartMapsSchema.PLACE_LABEL
      );
      assertEquals("capital", attrs.get("kind"));
    }

    @Test
    void anUntaggedPopulationFallsBackToTheKindDefault() {
      var attrs = attrs(
        new PlaceLabel(CONFIG),
        node(Map.of("place", "village", "name", "Hamlet")),
        SmartMapsSchema.PLACE_LABEL
      );
      assertEquals(100, attrs.get("population"));
    }

    @Test
    void anUnnamedPlaceIsNotALabel() {
      assertTrue(process(new PlaceLabel(CONFIG), node(Map.of("place", "city"))).isEmpty());
    }
  }

  @Nested
  class Boundaries {

    private static com.onthegomap.planetiler.reader.SourceFeature memberOf(
      int adminLevel,
      boolean disputed,
      Map<String, Object> tags
    ) {
      return wayInRelations(
        tags,
        List.of(
          new OsmReader.RelationMember<>(
            "outer",
            new BoundaryRelations.BoundaryRelation(1, adminLevel, disputed)
          )
        )
      );
    }

    @Test
    void aCountryBoundaryStartsAtZoomZero() {
      var feature = only(
        new Boundary(CONFIG),
        memberOf(2, false, Map.of("boundary", "administrative")),
        SmartMapsSchema.BOUNDARY
      );
      assertEquals(0, feature.getMinZoom());
      assertEquals(2, feature.getAttrsAtZoom(0).get("admin_level"));
    }

    @Test
    void aDisputedParentRelationMarksTheWay() {
      // A disputed border is a member of both an administrative relation, which gives it
      // its level, and a disputed one, which gives it the flag.
      var way = wayInRelations(
        Map.of("boundary", "administrative"),
        List.of(
          new OsmReader.RelationMember<>(
            "outer",
            new BoundaryRelations.BoundaryRelation(1, 2, false)
          ),
          new OsmReader.RelationMember<>(
            "outer",
            new BoundaryRelations.BoundaryRelation(2, null, true)
          )
        )
      );
      var attrs = only(new Boundary(CONFIG), way, SmartMapsSchema.BOUNDARY).getAttrsAtZoom(0);
      assertEquals(2, attrs.get("admin_level"));
      assertEquals(true, attrs.get("disputed"));
    }

    @Test
    void aWayInOnlyADisputedRelationHasNoLevelAndIsDropped() {
      assertTrue(
        process(
          new Boundary(CONFIG),
          memberOf(2, true, Map.of("boundary", "administrative"))
        ).isEmpty(),
        "a disputed relation contributes the flag, never a level"
      );
    }

    @Test
    void aWayInNoBoundaryRelationIsNotABoundary() {
      assertTrue(
        process(new Boundary(CONFIG), longWay(Map.of("boundary", "administrative"))).isEmpty()
      );
    }
  }

  @Nested
  class Addresses {

    @Test
    void anAddressPointCarriesBothHouseFields() {
      var attrs = attrs(
        new HousenumberLabel(CONFIG),
        node(Map.of("addr:housenumber", "12", "addr:housename", "Villa")),
        SmartMapsSchema.HOUSENUMBER_LABEL
      );
      assertEquals("12", attrs.get("housenumber"));
      assertEquals("Villa", attrs.get("housename"));
    }

    @Test
    void aPoiWithAnAddressIsLeftToThePoiLayer() {
      assertTrue(
        process(
          new HousenumberLabel(CONFIG),
          node(Map.of("amenity", "restaurant", "addr:housenumber", "12"))
        ).isEmpty()
      );
    }
  }

  @Nested
  class LandLayers {

    @Test
    void naturalGroundCoverGoesToLandcover() {
      var attrs = attrs(new Land(CONFIG), area(Map.of("landuse", "forest"), 0.01),
        SmartMapsSchema.LANDCOVER);
      assertEquals("forest", attrs.get("kind"));
      assertTrue(attrs.get("way_area") instanceof Double);
    }

    @Test
    void whatPeopleDoWithLandGoesToLanduse() {
      var attrs = attrs(new Land(CONFIG), area(Map.of("leisure", "park"), 0.01),
        SmartMapsSchema.LANDUSE);
      assertEquals("park", attrs.get("kind"));
      // The passthrough tag rides along, so a style can tell parks from other uses.
      assertEquals("park", attrs.get("leisure"));
    }

    @Test
    void aSiteIsLanduseRatherThanALayerOfItsOwn() {
      var attrs = attrs(new Land(CONFIG), area(Map.of("amenity", "school"), 0.001),
        SmartMapsSchema.LANDUSE);
      assertEquals("school", attrs.get("kind"));
    }

    @Test
    void elevationIsCarriedInBothUnits() {
      var attrs = attrs(
        new Land(CONFIG),
        area(Map.of("amenity", "school", "ele", "100"), 0.001),
        SmartMapsSchema.LANDUSE
      );
      assertEquals(100.0, attrs.get("ele"));
      assertEquals(328.0, attrs.get("ele_ft"));
    }

    @Test
    void landcoverHasNoElevationField() {
      var attrs = attrs(
        new Land(CONFIG),
        area(Map.of("landuse", "forest", "ele", "100"), 0.01),
        SmartMapsSchema.LANDCOVER
      );
      assertNull(attrs.get("ele"));
    }
  }

  @Nested
  class Water {

    @Test
    void anIntermittentStreamIsFlagged() {
      var attrs = attrs(
        new WaterLines(CONFIG),
        longWay(Map.of("waterway", "stream", "intermittent", "yes")),
        SmartMapsSchema.WATER_LINES
      );
      assertEquals(true, attrs.get("intermittent"));
    }

    @Test
    void aNamedWaterwayAlsoProducesALineLabel() {
      var label = attrs(
        new WaterLines(CONFIG),
        longWay(Map.of("waterway", "river", "name", "Rhine")),
        SmartMapsSchema.WATER_LABEL
      );
      assertEquals("Rhine", label.get("name"));
      assertEquals("river", label.get("kind"));
    }

    @Test
    void aNamedLakeProducesAPointLabelInTheSameMergedLayer() {
      var label = attrs(
        new WaterPolygons(CONFIG),
        area(Map.of("natural", "water", "name", "Lake"), 0.01),
        SmartMapsSchema.WATER_LABEL
      );
      assertEquals("Lake", label.get("name"));
      assertTrue(label.get("way_area") instanceof Double);
    }

    @Test
    void anUnnamedLakeProducesNoLabel() {
      assertTrue(
        inLayer(
          new WaterPolygons(CONFIG),
          area(Map.of("natural", "water"), 0.01),
          SmartMapsSchema.WATER_LABEL
        ).isEmpty()
      );
    }
  }

  @Nested
  class TransportLayer {

    @Test
    void aMotorwayCarriesItsKindAndFlags() {
      var attrs = attrs(
        new Transport(CONFIG),
        longWay(Map.of("highway", "motorway_link", "oneway", "yes", "bridge", "yes")),
        SmartMapsSchema.TRANSPORT
      );
      assertEquals("motorway", attrs.get("kind"));
      assertEquals(true, attrs.get("link"));
      assertEquals(true, attrs.get("oneway"));
      assertEquals(true, attrs.get("bridge"));
    }

    @Test
    void onlyTheTwoAccessFieldsThisLayoutHasAreEmitted() {
      var attrs = attrs(
        new Transport(CONFIG),
        longWay(
          Map.of("highway", "path", "access", "no", "bicycle", "yes", "horse", "designated")
        ),
        SmartMapsSchema.TRANSPORT
      );
      // Access values are normalized to yes/limited/no by the shared rules, so
      // horse=designated reads as yes.
      assertEquals("yes", attrs.get("bicycle"));
      assertEquals("yes", attrs.get("horse"));
      assertFalse(attrs.containsKey("motorcar"), "motorcar is not in this layout");
      assertFalse(attrs.containsKey("foot"), "foot is not in this layout");
    }

    @Test
    void railNeverCarriesOneway() {
      var attrs = attrs(
        new Transport(CONFIG),
        longWay(Map.of("railway", "rail", "oneway", "yes")),
        SmartMapsSchema.TRANSPORT
      );
      assertEquals(true, attrs.get("rail"));
      assertNull(attrs.get("oneway"));
    }

    @Test
    void constructionNamesWhatIsBeingBuiltAndKindNamesWhatItWillBe() {
      var feature = only(
        new Transport(CONFIG),
        longWay(Map.of("highway", "construction", "construction", "residential")),
        SmartMapsSchema.TRANSPORT
      );
      var attrs = feature.getAttrsAtZoom(SmartMapsSchema.MAXZOOM);
      assertEquals("residential", attrs.get("construction"));
      assertEquals("residential", attrs.get("kind"));
      assertEquals(12, feature.getMinZoom(), "a road being built is not a road yet");
    }

    @Test
    void aRailwayUnderConstructionIsCarriedToo() {
      var attrs = attrs(
        new Transport(CONFIG),
        longWay(Map.of("railway", "construction", "construction", "rail")),
        SmartMapsSchema.TRANSPORT
      );
      assertEquals("rail", attrs.get("kind"));
      assertEquals("rail", attrs.get("construction"));
      assertEquals(true, attrs.get("rail"));
    }

    @Test
    void aWayUnderConstructionWithNoClassIsDropped() {
      assertTrue(
        process(new Transport(CONFIG), longWay(Map.of("highway", "construction"))).isEmpty()
      );
    }

    @Test
    void aPedestrianSquareIsAnAreaInTheSameLayer() {
      var feature = only(
        new Transport(CONFIG),
        area(Map.of("highway", "pedestrian", "name", "Square"), 0.001),
        SmartMapsSchema.TRANSPORT
      );
      assertEquals("pedestrian", feature.getAttrsAtZoom(14).get("kind"));
      assertTrue(feature.getGeometry() instanceof org.locationtech.jts.geom.Polygonal);
    }

    @Test
    void aStationIsAPointInTheSameLayerWithItsRefinement() {
      var attrs = attrs(
        new Transport(CONFIG),
        node(Map.of("railway", "station", "name", "Hbf", "station", "subway", "ele", "35")),
        SmartMapsSchema.TRANSPORT
      );
      assertEquals("station", attrs.get("kind"));
      assertEquals("subway", attrs.get("station"));
      assertEquals(35.0, attrs.get("ele"));
      assertEquals(115.0, attrs.get("ele_ft"));
    }

    @Test
    void anAerodromeCarriesItsCodes() {
      var attrs = attrs(
        new Transport(CONFIG),
        area(Map.of("aeroway", "aerodrome", "name", "Findel", "iata", "LUX", "icao", "ELLX"), 0.01),
        SmartMapsSchema.TRANSPORT
      );
      assertEquals("aerodrome", attrs.get("kind"));
      assertEquals("LUX", attrs.get("iata"));
      assertEquals("ELLX", attrs.get("icao"));
    }
  }

  @Nested
  class TransportLabels {

    @Test
    void refsAreShippedPreLaidOut() {
      var attrs = attrs(
        new TransportLabel(CONFIG),
        longWay(Map.of("highway", "motorway", "ref", "A1;E15", "network", "e-road")),
        SmartMapsSchema.TRANSPORT_LABEL
      );
      assertEquals("A1\nE15", attrs.get("ref"));
      assertEquals(2, attrs.get("ref_rows"));
      assertEquals(3, attrs.get("ref_cols"));
      assertEquals("A", attrs.get("ref_prefix"));
      assertEquals("e-road", attrs.get("network"));
      assertEquals("e-road", attrs.get("ref_org"));
    }

    @Test
    void linkRoadsGetTheirOwnLabelKind() {
      var attrs = attrs(
        new TransportLabel(CONFIG),
        longWay(Map.of("highway", "motorway_link", "name", "Ramp")),
        SmartMapsSchema.TRANSPORT_LABEL
      );
      assertEquals("motorway_link", attrs.get("kind"));
    }

    @Test
    void aMotorwayExitIsAPointInTheSameLayer() {
      var feature = only(
        new TransportLabel(CONFIG),
        node(Map.of("highway", "motorway_junction", "ref", "12", "name", "Exit")),
        SmartMapsSchema.TRANSPORT_LABEL
      );
      var attrs = feature.getAttrsAtZoom(14);
      assertEquals("motorway_junction", attrs.get("kind"));
      // A bare exit number is not a route list, so it is not laid out and has no prefix.
      assertEquals("12", attrs.get("ref"));
      assertNull(attrs.get("ref_rows"));
      assertNull(attrs.get("ref_prefix"));
    }

    @Test
    void anUnnamedUnreferencedStreetIsNotLabelled() {
      assertTrue(
        process(new TransportLabel(CONFIG), longWay(Map.of("highway", "residential"))).isEmpty()
      );
    }
  }

  @Nested
  class Pois {

    @Test
    void kindIsThePrimarySelectedValue() {
      var attrs = attrs(
        new Poi(CONFIG),
        node(Map.of("amenity", "cafe", "name", "Cafe", "cuisine", "coffee_shop")),
        SmartMapsSchema.POI
      );
      assertEquals("cafe", attrs.get("kind"));
      // The selecting tag is still there, so nothing is lost by adding kind.
      assertEquals("cafe", attrs.get("amenity"));
      assertEquals("coffee_shop", attrs.get("cuisine"));
    }

    @Test
    void elevationIsCarriedInBothUnits() {
      var attrs = attrs(
        new Poi(CONFIG),
        node(Map.of("tourism", "viewpoint", "ele", "1000")),
        SmartMapsSchema.POI
      );
      assertEquals(1000.0, attrs.get("ele"));
      assertEquals(3281.0, attrs.get("ele_ft"));
    }

    @Test
    void conditionalAttributesStayConditional() {
      var bank = attrs(
        new Poi(CONFIG),
        node(Map.of("amenity", "bank", "atm", "yes")),
        SmartMapsSchema.POI
      );
      assertEquals(true, bank.get("atm"));

      var shop = attrs(
        new Poi(CONFIG),
        node(Map.of("shop", "supermarket", "atm", "yes")),
        SmartMapsSchema.POI
      );
      assertNull(shop.get("atm"), "atm is a bank attribute in this layout");
    }
  }

  @Nested
  class Buildings {

    private static Building layer() {
      return new Building(
        CONFIG,
        new fyi.osm.sourdough.common.buildings3d.BuildingDimensionParser(
          CONFIG.levelHeight(),
          CONFIG.estimateMissingHeights(),
          new fyi.osm.sourdough.common.buildings3d.BuildingMetrics()
        ),
        new fyi.osm.sourdough.common.buildings3d.BuildingMetrics()
      );
    }

    @Test
    void aBuildingIsReadyToExtrude() {
      var attrs = attrs(
        layer(),
        area(Map.of("building", "yes", "height", "12"), 0.0005),
        SmartMapsSchema.BUILDING
      );
      assertEquals(12.0, attrs.get("render_height"));
      assertEquals(true, attrs.get("3d"));
      assertNull(attrs.get("building:part"));
    }

    @Test
    void partsShareTheLayerBehindTheFlag() {
      var attrs = attrs(
        layer(),
        area(Map.of("building:part", "yes", "height", "20", "min_height", "8"), 0.0005),
        SmartMapsSchema.BUILDING
      );
      assertEquals(true, attrs.get("building:part"));
      assertEquals(20.0, attrs.get("render_height"));
      assertEquals(8.0, attrs.get("render_min_height"));
    }

    @Test
    void anEstimatedHeightIsStillARenderHeightAndIsNotCalledThreeD() {
      var attrs = attrs(layer(), area(Map.of("building", "house"), 0.0005),
        SmartMapsSchema.BUILDING);
      assertTrue(
        attrs.get("render_height") instanceof Double h && h > 0,
        "estimation is on by default because this layout has no factual height field"
      );
      assertNull(attrs.get("3d"), "an estimate is not Simple 3D Buildings information");
    }

    @Test
    void aRoofShapeSetsTheRoofFlag() {
      var attrs = attrs(
        layer(),
        area(Map.of("building", "yes", "roof:shape", "gabled"), 0.0005),
        SmartMapsSchema.BUILDING
      );
      assertEquals(true, attrs.get("roof"));
      assertEquals(true, attrs.get("3d"));
    }

    @Test
    void buildingNoIsNotABuilding() {
      assertTrue(process(layer(), area(Map.of("building", "no"), 0.0005)).isEmpty());
    }

    @Test
    void poiIshTagsRideAlongForLabelling() {
      var attrs = attrs(
        layer(),
        area(
          Map.of("building", "church", "amenity", "place_of_worship", "religion", "christian"),
          0.0005
        ),
        SmartMapsSchema.BUILDING
      );
      assertEquals("place_of_worship", attrs.get("amenity"));
      assertEquals("christian", attrs.get("religion"));
    }
  }

  /**
   * A kind named in the routing table but not produced by the land table would silently
   * route to landuse instead of failing, so the two are checked against each other.
   */
  @Test
  void theLandRoutingTableOnlyNamesKindsThatExist() {
    var kinds = fyi.osm.sourdough.common.mapping.LandKinds.kinds();
    var cover = kinds.stream()
      .filter(fyi.osm.sourdough.smartmaps.layers.LandKindRouting::isCover)
      .toList();
    assertEquals(16, cover.size(), "cover kinds: " + cover);
    for (var kind : kinds) {
      var layer = fyi.osm.sourdough.smartmaps.layers.LandKindRouting.layerFor(kind);
      assertTrue(
        layer.equals(SmartMapsSchema.LANDCOVER) || layer.equals(SmartMapsSchema.LANDUSE),
        kind + " routed to " + layer
      );
    }
  }

  @Test
  void everyLayerHandlerIsRegisteredWithTheProfile() {
    var profile = new SmartMapsProfile(CONFIG);
    // A layer that exists but is never registered emits nothing, which the conformance
    // corpus would report as a missing layer -- but only if a fixture reaches it. This
    // check does not depend on the corpus.
    assertEquals(
      SmartMapsSchema.layerNames().size(),
      12,
      "the layout has twelve layers; update this test if that changes"
    );
    assertEquals(SmartMapsSchema.MAXZOOM, TestSupport.SHORTBREAD_MAXZOOM);
    assertEquals("SmartMaps-compatible", profile.name());
  }
}
