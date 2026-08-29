package fyi.osm.sourdough.shortbread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onthegomap.planetiler.reader.osm.OsmReader;
import fyi.osm.sourdough.TestSupport;
import fyi.osm.sourdough.common.BoundaryRelations;
import fyi.osm.sourdough.shortbread.layers.Addresses;
import fyi.osm.sourdough.shortbread.layers.Boundaries;
import fyi.osm.sourdough.shortbread.layers.Ferries;
import fyi.osm.sourdough.shortbread.layers.PlaceLabels;
import fyi.osm.sourdough.shortbread.layers.Pois;
import fyi.osm.sourdough.shortbread.layers.Streets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Selection, attribute and ordering rules that the specification states explicitly. */
class LayerBehaviourTest {

  private static final ShortbreadConfiguration CONFIG = ShortbreadConfiguration.defaults();

  // --- streets --------------------------------------------------------------

  @ParameterizedTest
  @CsvSource({
    "motorway, motorway, 5",
    "trunk, trunk, 6",
    "primary, primary, 8",
    "secondary, secondary, 9",
    "tertiary, tertiary, 10",
    "residential, residential, 12",
    "service, service, 13",
    "footway, footway, 13"
  })
  void roadClassesGetTheirSpecifiedKindAndZoom(String highway, String kind, int minZoom) {
    var feature = TestSupport.processOne(
      new Streets(CONFIG),
      TestSupport.longWay(Map.of("highway", highway))
    );
    assertEquals(kind, feature.getAttrsAtZoom(14).get("kind"));
    assertEquals(minZoom, feature.getMinZoom());
  }

  @Test
  void linkRoadsKeepTheBaseKindAndSetTheLinkFlag() {
    var feature = TestSupport.processOne(
      new Streets(CONFIG),
      TestSupport.longWay(Map.of("highway", "motorway_link"))
    );
    assertEquals("motorway", feature.getAttrsAtZoom(14).get("kind"));
    assertEquals(true, feature.getAttrsAtZoom(14).get("link"));
  }

  @Test
  void railwaysAreFlaggedAndNeverGetOnewayOrAccess() {
    var feature = TestSupport.processOne(
      new Streets(CONFIG),
      TestSupport.longWay(Map.of("railway", "rail", "oneway", "yes", "foot", "no"))
    );
    var attrs = feature.getAttrsAtZoom(14);
    assertEquals(true, attrs.get("rail"));
    assertFalse(attrs.containsKey("oneway"), "oneway is always false for rail");
    assertFalse(attrs.containsKey("foot"), "access applies to highways only");
  }

  @Test
  void serviceRailwaysAppearTwoZoomsLater() {
    assertEquals(
      8,
      TestSupport.processOne(new Streets(CONFIG), TestSupport.longWay(Map.of("railway", "rail")))
        .getMinZoom()
    );
    assertEquals(
      10,
      TestSupport.processOne(
        new Streets(CONFIG),
        TestSupport.longWay(Map.of("railway", "rail", "service", "siding"))
      ).getMinZoom()
    );
  }

  @Test
  void accessAttributesOnlyAppearFromZoom13() {
    var feature = TestSupport.processOne(
      new Streets(CONFIG),
      TestSupport.longWay(Map.of("highway", "path", "bicycle", "designated"))
    );
    assertFalse(feature.getAttrsAtZoom(12).containsKey("bicycle"));
    assertEquals("yes", feature.getAttrsAtZoom(13).get("bicycle"));
  }

  @Test
  void onewayAttributesOnlyAppearAtZoom14() {
    var feature = TestSupport.processOne(
      new Streets(CONFIG),
      TestSupport.longWay(Map.of("highway", "residential", "oneway", "-1"))
    );
    assertFalse(feature.getAttrsAtZoom(13).containsKey("oneway"));
    assertEquals(true, feature.getAttrsAtZoom(14).get("oneway"));
    assertEquals(true, feature.getAttrsAtZoom(14).get("oneway_reverse"));
  }

  @Test
  void importantRoadsSortBeforeMinorOnes() {
    var motorway = TestSupport.processOne(
      new Streets(CONFIG),
      TestSupport.longWay(Map.of("highway", "motorway"))
    );
    var residential = TestSupport.processOne(
      new Streets(CONFIG),
      TestSupport.longWay(Map.of("highway", "residential"))
    );
    assertTrue(motorway.getSortKey() < residential.getSortKey());
  }

  // --- pois and addresses ---------------------------------------------------

  @Test
  void onlyCuratedValuesBecomePois() {
    assertEquals(
      1,
      TestSupport.process(new Pois(CONFIG), TestSupport.node(Map.of("amenity", "restaurant")))
        .size()
    );
    assertTrue(
      TestSupport.process(new Pois(CONFIG), TestSupport.node(Map.of("amenity", "waste_transfer_station")))
        .isEmpty(),
      "a value outside the curated list is not a POI"
    );
  }

  @Test
  void conditionalAttributesOnlyAppearOnTheirOwnKindOfPoi() {
    var restaurant = TestSupport.processOne(
      new Pois(CONFIG),
      TestSupport.node(Map.of("amenity", "restaurant", "cuisine", "italian"))
    );
    assertEquals("italian", restaurant.getAttrsAtZoom(14).get("cuisine"));

    var bench = TestSupport.processOne(
      new Pois(CONFIG),
      TestSupport.node(Map.of("amenity", "bench", "cuisine", "italian"))
    );
    assertFalse(bench.getAttrsAtZoom(14).containsKey("cuisine"));
  }

  @Test
  void recyclingAndAtmBooleansAreOnlySetWhenTrue() {
    var recycling = TestSupport.processOne(
      new Pois(CONFIG),
      TestSupport.node(Map.of("amenity", "recycling", "recycling:paper", "yes", "recycling:glass_bottles", "no"))
    );
    var attrs = recycling.getAttrsAtZoom(14);
    assertEquals(true, attrs.get("recycling:paper"));
    assertFalse(attrs.containsKey("recycling:glass_bottles"), "false defaults are omitted");
  }

  @Test
  void anAddressNodeBecomesAnAddress() {
    var feature = TestSupport.processOne(
      new Addresses(CONFIG),
      TestSupport.node(Map.of("addr:housenumber", "12"))
    );
    assertEquals(ShortbreadSchema.ADDRESSES, feature.getLayer());
    assertEquals("12", feature.getAttrsAtZoom(14).get("housenumber"));
  }

  @Test
  void anAddressPolygonIsRepresentedByAPoint() {
    var feature = TestSupport.processOne(
      new Addresses(CONFIG),
      TestSupport.area(Map.of("addr:housename", "The Grange"))
    );
    assertEquals("The Grange", feature.getAttrsAtZoom(14).get("housename"));
    assertTrue(feature.getGeometry() instanceof org.locationtech.jts.geom.Puntal);
  }

  @Test
  void housenameAndHousenumberCanBothBePresent() {
    var attrs = TestSupport.processOne(
      new Addresses(CONFIG),
      TestSupport.node(Map.of("addr:housename", "Villa", "addr:housenumber", "3"))
    ).getAttrsAtZoom(14);
    assertEquals("Villa", attrs.get("housename"));
    assertEquals("3", attrs.get("housenumber"));
  }

  @Test
  void anAddressThatIsAlsoAPoiIsNotDuplicated() {
    var tags = Map.<String, Object>of("amenity", "restaurant", "addr:housenumber", "12");
    assertTrue(
      TestSupport.process(new Addresses(CONFIG), TestSupport.node(tags)).isEmpty(),
      "a POI carries its own housenumber, so it must not also be an address"
    );
    var poi = TestSupport.processOne(new Pois(CONFIG), TestSupport.node(tags));
    assertEquals("12", poi.getAttrsAtZoom(14).get("housenumber"));
  }

  // --- boundaries -----------------------------------------------------------

  private static List<OsmReader.RelationMember<com.onthegomap.planetiler.reader.osm.OsmRelationInfo>>
    parents(BoundaryRelations.BoundaryRelation... relations) {
    return java.util.Arrays.stream(relations)
      .map(r ->
        new OsmReader.RelationMember<com.onthegomap.planetiler.reader.osm.OsmRelationInfo>("outer", r)
      )
      .toList();
  }

  @Test
  void aBoundaryTakesTheLowestAdminLevelOfItsParents() {
    var feature = TestSupport.processOne(
      new Boundaries(CONFIG),
      TestSupport.wayInRelations(
        Map.of("boundary", "administrative"),
        parents(
          new BoundaryRelations.BoundaryRelation(1, 4, false),
          new BoundaryRelations.BoundaryRelation(2, 2, false)
        )
      )
    );
    assertEquals(2, feature.getAttrsAtZoom(14).get("admin_level"));
    assertEquals(0, feature.getMinZoom(), "country boundaries start at zoom 0");
  }

  @Test
  void stateBoundariesStartAtZoom7() {
    var feature = TestSupport.processOne(
      new Boundaries(CONFIG),
      TestSupport.wayInRelations(
        Map.of("boundary", "administrative"),
        parents(new BoundaryRelations.BoundaryRelation(1, 4, false))
      )
    );
    assertEquals(7, feature.getMinZoom());
  }

  @Test
  void aCoastlineBoundaryIsMaritime() {
    var feature = TestSupport.processOne(
      new Boundaries(CONFIG),
      TestSupport.wayInRelations(
        Map.of("boundary", "administrative", "natural", "coastline"),
        parents(new BoundaryRelations.BoundaryRelation(1, 2, false))
      )
    );
    assertEquals(true, feature.getAttrsAtZoom(14).get("maritime"));
  }

  @Test
  void disputedComesFromTheWayOrFromAParentRelation() {
    var fromWay = TestSupport.processOne(
      new Boundaries(CONFIG),
      TestSupport.wayInRelations(
        Map.of("boundary", "administrative", "disputed", "yes"),
        parents(new BoundaryRelations.BoundaryRelation(1, 2, false))
      )
    );
    assertEquals(true, fromWay.getAttrsAtZoom(14).get("disputed"));

    var fromRelation = TestSupport.processOne(
      new Boundaries(CONFIG),
      TestSupport.wayInRelations(
        Map.of("boundary", "administrative"),
        parents(
          new BoundaryRelations.BoundaryRelation(1, 2, false),
          // A disputed relation with no admin_level still counts.
          new BoundaryRelations.BoundaryRelation(2, null, true)
        )
      )
    );
    assertEquals(true, fromRelation.getAttrsAtZoom(14).get("disputed"));
  }

  @Test
  void aWayWithNoAdministrativeParentIsNotABoundaryLine() {
    assertTrue(
      TestSupport.process(
        new Boundaries(CONFIG),
        TestSupport.wayInRelations(
          Map.of("boundary", "administrative"),
          parents(new BoundaryRelations.BoundaryRelation(1, null, true))
        )
      ).isEmpty()
    );
  }

  // --- place labels ---------------------------------------------------------

  @Test
  void capitalsAreReclassified() {
    assertEquals(
      "capital",
      TestSupport.processOne(
        new PlaceLabels(CONFIG),
        TestSupport.node(Map.of("place", "city", "name", "X", "capital", "yes"))
      ).getAttrsAtZoom(14).get("kind")
    );
    assertEquals(
      "state_capital",
      TestSupport.processOne(
        new PlaceLabels(CONFIG),
        TestSupport.node(Map.of("place", "city", "name", "X", "capital", "4"))
      ).getAttrsAtZoom(14).get("kind")
    );
  }

  @ParameterizedTest
  @CsvSource({ "city, 100000", "town, 5000", "village, 100", "hamlet, 50", "locality, 0" })
  void placesWithNoPopulationTagFallBackToTheirDefault(String place, int expected) {
    assertEquals(
      expected,
      TestSupport.processOne(
        new PlaceLabels(CONFIG),
        TestSupport.node(Map.of("place", place, "name", "X"))
      ).getAttrsAtZoom(14).get("population")
    );
  }

  @Test
  void aTaggedPopulationWinsOverTheDefault() {
    assertEquals(
      42,
      TestSupport.processOne(
        new PlaceLabels(CONFIG),
        TestSupport.node(Map.of("place", "city", "name", "X", "population", "42"))
      ).getAttrsAtZoom(14).get("population")
    );
  }

  @Test
  void biggerPlacesSortFirst() {
    var big = TestSupport.processOne(
      new PlaceLabels(CONFIG),
      TestSupport.node(Map.of("place", "city", "name", "Big", "population", "1000000"))
    );
    var small = TestSupport.processOne(
      new PlaceLabels(CONFIG),
      TestSupport.node(Map.of("place", "village", "name", "Small", "population", "200"))
    );
    assertTrue(big.getSortKey() < small.getSortKey());
  }

  // --- ferries --------------------------------------------------------------

  @Test
  void motorVehicleFerriesAppearBeforeOtherFerries() {
    assertEquals(
      10,
      TestSupport.processOne(new Ferries(CONFIG), TestSupport.longWay(Map.of("route", "ferry")))
        .getMinZoom()
    );
    assertEquals(
      12,
      TestSupport.processOne(
        new Ferries(CONFIG),
        TestSupport.longWay(Map.of("route", "ferry", "motor_vehicle", "no"))
      ).getMinZoom()
    );
  }
}
