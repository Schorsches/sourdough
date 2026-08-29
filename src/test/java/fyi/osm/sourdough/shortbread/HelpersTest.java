package fyi.osm.sourdough.shortbread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onthegomap.planetiler.TestUtils;
import com.onthegomap.planetiler.reader.SimpleFeature;
import com.onthegomap.planetiler.reader.WithTags;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Unit tests for the pure helpers shared across Shortbread layers. */
class HelpersTest {

  private static WithTags tags(String... keyValues) {
    var map = new java.util.HashMap<String, Object>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put(keyValues[i], keyValues[i + 1]);
    }
    return SimpleFeature.create(TestUtils.newPoint(0, 0), map);
  }

  // --- Access ---------------------------------------------------------------

  @ParameterizedTest
  @CsvSource({
    "yes, yes",
    "designated, yes",
    "permissive, yes",
    "customers, limited",
    "destination, limited",
    "agricultural, limited",
    "forestry, limited",
    "delivery, limited",
    "discouraged, limited",
    "permit, limited",
    "dismount, no",
    "military, no",
    "private, no",
    "no, no"
  })
  void accessValuesAreNormalized(String osmValue, String expected) {
    assertEquals(expected, Access.evaluate(tags("foot", osmValue), Access.FOOT));
  }

  @Test
  void unrecognizedAccessValuesFallThroughToTheNextKeyInTheChain() {
    // "unknown" is removed from consideration, so `access` decides.
    assertEquals("no", Access.evaluate(tags("foot", "unknown", "access", "private"), Access.FOOT));
  }

  @Test
  void theMostSpecificAccessKeyWins() {
    var sf = tags("motorcar", "no", "motor_vehicle", "yes", "vehicle", "yes", "access", "yes");
    assertEquals("no", Access.evaluate(sf, Access.MOTORCAR));
  }

  @Test
  void bicycleFallsBackThroughVehicleToAccess() {
    assertEquals("yes", Access.evaluate(tags("vehicle", "designated"), Access.BICYCLE));
    assertEquals("limited", Access.evaluate(tags("access", "customers"), Access.BICYCLE));
  }

  @Test
  void noAccessTagsMeansNoValue() {
    assertNull(Access.evaluate(tags("highway", "residential"), Access.FOOT));
  }

  // --- Booleans -------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = { "yes", "building_passage" })
  void tunnelValues(String value) {
    assertTrue(Booleans.tunnel(tags("tunnel", value)));
  }

  @Test
  void coveredCountsAsATunnel() {
    assertTrue(Booleans.tunnel(tags("covered", "yes")));
  }

  @Test
  void otherTunnelValuesAreNotTunnels() {
    assertFalse(Booleans.tunnel(tags("tunnel", "no")));
    assertFalse(Booleans.tunnel(tags("tunnel", "culvert")));
  }

  @ParameterizedTest
  @ValueSource(
    strings = { "yes", "viaduct", "boardwalk", "cantilever", "covered", "low_water_crossing", "movable", "trestle" }
  )
  void bridgeValues(String value) {
    assertTrue(Booleans.bridge(tags("bridge", value)));
  }

  @Test
  void otherBridgeValuesAreNotBridges() {
    assertFalse(Booleans.bridge(tags("bridge", "no")));
    assertFalse(Booleans.bridge(tags("bridge", "aqueduct")));
  }

  @ParameterizedTest
  @ValueSource(strings = { "yes", "1", "true", "-1" })
  void onewayValues(String value) {
    assertTrue(Booleans.oneway(tags("oneway", value)));
  }

  @Test
  void onlyMinusOneIsAReverseOneway() {
    assertTrue(Booleans.onewayReverse(tags("oneway", "-1")));
    assertFalse(Booleans.onewayReverse(tags("oneway", "yes")));
  }

  // --- ZOrder ---------------------------------------------------------------

  @Test
  void moreImportantRoadsSortBeforeLessImportantOnes() {
    int motorway = ZOrder.sortKey(tags("highway", "motorway"), "motorway");
    int residential = ZOrder.sortKey(tags("highway", "residential"), "residential");
    assertTrue(motorway < residential, "motorways should sort before residential roads");
  }

  @Test
  void tunnelsSortBeforeBridges() {
    int tunnel = ZOrder.sortKey(tags("highway", "primary", "tunnel", "yes"), "primary");
    int ground = ZOrder.sortKey(tags("highway", "primary"), "primary");
    int bridge = ZOrder.sortKey(tags("highway", "primary", "bridge", "yes"), "primary");
    assertTrue(tunnel < ground, "tunnels should sort before ground level");
    assertTrue(ground < bridge, "bridges should sort after ground level");
  }

  @Test
  void theLayerTagDominatesTheRoadClass() {
    // A motorway in a tunnel below ground still sorts under a footway at ground level.
    int lowMotorway = ZOrder.sortKey(tags("highway", "motorway", "layer", "-2"), "motorway");
    int groundFootway = ZOrder.sortKey(tags("highway", "footway"), "footway");
    assertTrue(lowMotorway < groundFootway);
  }

  @Test
  void anUnparseableLayerTagIsTreatedAsGroundLevel() {
    assertEquals(
      ZOrder.sortKey(tags("highway", "primary"), "primary"),
      ZOrder.sortKey(tags("highway", "primary", "layer", "abc"), "primary")
    );
  }

  @Test
  void unknownKindsSortLast() {
    assertTrue(ZOrder.classRank("motorway") < ZOrder.classRank("something_else"));
  }

  // --- Street label refs ----------------------------------------------------

  @Test
  void semicolonSeparatedRefsBecomeSeparateRows() {
    var ref = RouteRef.layout("A1;E15");
    assertEquals("A1\nE15", ref);
    assertEquals(2, RouteRef.rows(ref));
    assertEquals(3, RouteRef.columns(ref));
  }

  @Test
  void aSingleRefIsOneRow() {
    var ref = RouteRef.layout("M25");
    assertEquals("M25", ref);
    assertEquals(1, RouteRef.rows(ref));
    assertEquals(3, RouteRef.columns(ref));
  }

  @Test
  void refColumnsMeasureTheLongestRow() {
    var ref = RouteRef.layout("A1;E15;B4404");
    assertEquals(3, RouteRef.rows(ref));
    assertEquals(5, RouteRef.columns(ref));
  }

  // --- Language presets -----------------------------------------------------

  @Test
  void noLanguagesByDefault() {
    assertEquals(java.util.List.of(), LanguagePresets.resolve(java.util.List.of()));
  }

  @Test
  void thePresetExpandsToItsFullList() {
    var resolved = LanguagePresets.resolve(java.util.List.of("smartmaps"));
    assertEquals(LanguagePresets.SMARTMAPS.size(), resolved.size());
    assertTrue(resolved.contains("de"));
    assertTrue(resolved.contains("ko-Latn"));
  }

  @Test
  void presetsAndExplicitCodesCanBeMixedWithoutDuplicates() {
    var resolved = LanguagePresets.resolve(java.util.List.of("de", "smartmaps", "de"));
    assertEquals("de", resolved.get(0));
    assertEquals(LanguagePresets.SMARTMAPS.size(), resolved.size());
  }

  @Test
  void unknownEntriesAreTreatedAsLanguageCodes() {
    assertEquals(java.util.List.of("xx", "yy"), LanguagePresets.resolve(java.util.List.of("xx", " yy ")));
  }

  // --- Way area -------------------------------------------------------------

  @Test
  void hectaresAreSquareMetersOverTenThousand() {
    var sf = SimpleFeature.create(
      com.onthegomap.planetiler.geo.GeoUtils.worldToLatLonCoords(TestUtils.rectangle(0.4, 0.6)),
      Map.of()
    );
    var squareMeters = WayArea.squareMeters(sf);
    var hectares = WayArea.hectares(sf);
    assertEquals(squareMeters / 10_000.0, hectares, 1e-6);
  }

  @Test
  void aQuarterOfTheWorldSquareIsAQuarterOfItsArea() {
    // A rectangle covering half the world in each direction is a quarter of its area.
    var sf = SimpleFeature.create(
      com.onthegomap.planetiler.geo.GeoUtils.worldToLatLonCoords(TestUtils.rectangle(0.25, 0.75)),
      Map.of()
    );
    double world = 40_075_016.6855785 * 40_075_016.6855785;
    assertEquals(world / 4, WayArea.squareMeters(sf), world * 1e-6);
  }
}
