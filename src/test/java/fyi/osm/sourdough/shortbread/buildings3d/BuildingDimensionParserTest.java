package fyi.osm.sourdough.shortbread.buildings3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onthegomap.planetiler.reader.SimpleFeature;
import com.onthegomap.planetiler.reader.WithTags;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BuildingDimensionParserTest {

  private static final double LEVEL_HEIGHT = 3.0;

  private BuildingMetrics metrics;
  private BuildingDimensionParser parser;

  @BeforeEach
  void setUp() {
    metrics = new BuildingMetrics();
    // Most cases here are about reading tagged dimensions, so the type-based estimate is
    // off by default and switched on explicitly by the tests that cover it.
    parser = new BuildingDimensionParser(LEVEL_HEIGHT, false, metrics);
  }

  private BuildingDimensions parseEstimating(String... keyValues) {
    parser = new BuildingDimensionParser(LEVEL_HEIGHT, true, metrics);
    return parse(keyValues);
  }

  private BuildingDimensions parse(String... keyValues) {
    var tags = new java.util.HashMap<String, Object>();
    for (int i = 0; i < keyValues.length; i += 2) {
      tags.put(keyValues[i], keyValues[i + 1]);
    }
    WithTags feature = SimpleFeature.create(
      com.onthegomap.planetiler.TestUtils.newPoint(0, 0),
      tags
    );
    return parser.parse(feature);
  }

  @Test
  void buildingWithNoDimensionsGetsNoHeightWhenEstimatesAreOff() {
    var dimensions = parse("building", "yes");
    assertNull(dimensions.height());
    assertNull(dimensions.minHeight());
    assertFalse(dimensions.estimated());
    assertEquals(1, metrics.get(BuildingMetrics.HEIGHT_ABSENT));
  }

  // --- estimates from the building type ------------------------------------

  @Test
  void aBuildingWithNoDimensionsIsEstimatedFromItsType() {
    var dimensions = parseEstimating("building", "yes");
    // building=yes says nothing, so the global fallback of two storeys applies.
    assertEquals(6.0, dimensions.height());
    assertTrue(dimensions.estimated());
    assertEquals(1, metrics.get(BuildingMetrics.HEIGHT_FROM_TYPE));
  }

  @ParameterizedTest
  @CsvSource({
    "garage, 3.0",
    "shed, 3.0",
    "house, 6.0",
    "detached, 6.0",
    "retail, 9.0",
    "apartments, 12.0",
    "office, 12.0",
    "cathedral, 18.0"
  })
  void buildingTypesGetTheirOwnEstimate(String type, double expected) {
    assertEquals(expected, parseEstimating("building", type).height());
  }

  @Test
  void anUnknownBuildingTypeFallsBackToTheGlobalDefault() {
    assertEquals(
      BuildingTypeDefaults.DEFAULT_LEVELS * LEVEL_HEIGHT,
      parseEstimating("building", "something_nobody_has_mapped_before").height()
    );
  }

  @Test
  void anEstimateCarriesNoLevelCount() {
    // A consumer tells a level-derived height from a type-derived one by whether
    // building_levels is there, so an estimate must not invent one.
    var dimensions = parseEstimating("building", "house");
    assertTrue(dimensions.estimated());
    assertNull(dimensions.levels());
  }

  @Test
  void anExplicitHeightBeatsTheTypeEstimate() {
    var dimensions = parseEstimating("building", "garage", "height", "30");
    assertEquals(30.0, dimensions.height());
    assertFalse(dimensions.estimated());
    assertEquals(0, metrics.get(BuildingMetrics.HEIGHT_FROM_TYPE));
  }

  @Test
  void aLevelCountBeatsTheTypeEstimate() {
    var dimensions = parseEstimating("building", "garage", "building:levels", "5");
    assertEquals(15.0, dimensions.height());
    assertEquals(5, dimensions.levels());
    assertEquals(1, metrics.get(BuildingMetrics.HEIGHT_FROM_LEVELS));
    assertEquals(0, metrics.get(BuildingMetrics.HEIGHT_FROM_TYPE));
  }

  @Test
  void aMalformedHeightFallsThroughToTheEstimateRatherThanNothing() {
    var dimensions = parseEstimating("building", "house", "height", "twelve");
    assertEquals(6.0, dimensions.height());
    assertTrue(dimensions.estimated());
    assertEquals(1, metrics.get(BuildingMetrics.HEIGHT_INVALID));
  }

  @Test
  void aTaggedRoofHeightIsStillAddedToAnEstimate() {
    var dimensions = parseEstimating("building", "house", "roof:height", "2");
    assertEquals(8.0, dimensions.height());
    assertEquals(2.0, dimensions.roofHeight());
  }

  @Test
  void aBuildingPartIsEstimatedFromItsOwnType() {
    var dimensions = parseEstimating("building:part", "garage");
    assertEquals(3.0, dimensions.height());
  }

  @Test
  void aGenericBuildingPartUsesTheParentBuildingType() {
    // building:part=yes carries no type of its own, so the building tag decides.
    assertEquals(12.0, parseEstimating("building:part", "yes", "building", "apartments").height());
  }

  @Test
  void explicitMetricHeightIsUsedAsIs() {
    var dimensions = parse("building", "yes", "height", "12");
    assertEquals(12.0, dimensions.height());
    assertFalse(dimensions.estimated());
    assertEquals(1, metrics.get(BuildingMetrics.HEIGHT_EXPLICIT));
  }

  @Test
  void heightInFeetIsConvertedToMeters() {
    var dimensions = parse("building", "yes", "height", "40 ft");
    assertEquals(12.2, dimensions.height(), 0.05);
  }

  @Test
  void heightWithAMetreSuffixIsParsed() {
    assertEquals(12.0, parse("building", "yes", "height", "12 m").height());
  }

  @Test
  void decimalCommaIsAccepted() {
    assertEquals(3.5, parse("building", "yes", "height", "3,5").height());
  }

  @Test
  void levelsDeriveAHeightAndAreMarkedEstimated() {
    var dimensions = parse("building", "yes", "building:levels", "4");
    assertEquals(12.0, dimensions.height());
    assertEquals(4, dimensions.levels());
    assertTrue(dimensions.estimated());
    assertEquals(1, metrics.get(BuildingMetrics.HEIGHT_FROM_LEVELS));
  }

  @Test
  void roofHeightIsAddedOnTopOfALevelDerivedFacade() {
    // The level count describes the facade only, so adding the roof is correct here.
    var dimensions = parse("building", "yes", "building:levels", "4", "roof:height", "2");
    assertEquals(14.0, dimensions.height());
    assertEquals(2.0, dimensions.roofHeight());
    assertTrue(dimensions.estimated());
  }

  @Test
  void explicitHeightIsNeverIncreasedByRoofHeight() {
    // The critical rule: height already includes the roof, so this must be 14, not 16.
    var dimensions = parse("building", "yes", "height", "14", "roof:height", "2");
    assertEquals(14.0, dimensions.height());
    assertEquals(2.0, dimensions.roofHeight());
    assertFalse(dimensions.estimated());
  }

  @Test
  void roofLevelsDeriveARoofHeightWhenNoneIsTagged() {
    var dimensions = parse("building", "yes", "building:levels", "4", "roof:levels", "1");
    assertEquals(15.0, dimensions.height());
    assertEquals(3.0, dimensions.roofHeight());
  }

  @Test
  void roofTallerThanTheBuildingIsRejected() {
    var dimensions = parse("building", "yes", "height", "10", "roof:height", "12");
    assertEquals(10.0, dimensions.height());
    assertNull(dimensions.roofHeight());
    assertEquals(1, metrics.get(BuildingMetrics.ROOF_HEIGHT_INVALID));
  }

  @Test
  void elevatedPartKeepsItsExplicitMinHeight() {
    var dimensions = parse("building:part", "yes", "height", "20", "min_height", "8");
    assertEquals(20.0, dimensions.height());
    assertEquals(8.0, dimensions.minHeight());
  }

  @Test
  void minLevelDerivesAMinHeight() {
    var dimensions = parse("building:part", "yes", "building:levels", "6", "building:min_level", "2");
    assertEquals(18.0, dimensions.height());
    assertEquals(6.0, dimensions.minHeight());
  }

  @Test
  void minHeightAtOrAboveTheTopIsRejected() {
    var dimensions = parse("building:part", "yes", "height", "10", "min_height", "10");
    assertEquals(10.0, dimensions.height());
    assertNull(dimensions.minHeight());
    assertEquals(1, metrics.get(BuildingMetrics.MIN_HEIGHT_INVALID));
  }

  @Test
  void minLevelAtOrAboveTheLevelCountIsRejected() {
    var dimensions = parse("building:part", "yes", "building:levels", "2", "building:min_level", "4");
    assertEquals(6.0, dimensions.height());
    assertNull(dimensions.minHeight());
  }

  @Test
  void zeroMinHeightIsOmittedBecauseItIsTheRendererDefault() {
    assertNull(parse("building", "yes", "height", "10", "min_height", "0").minHeight());
  }

  @ParameterizedTest
  @ValueSource(strings = { "abc", "", " ", "-5", "0", "NaN", "Infinity", "1e9", "12;14", "3,5,7" })
  void malformedHeightsProduceNoHeight(String value) {
    var dimensions = parse("building", "yes", "height", value);
    assertNull(dimensions.height(), "expected no height for height=" + value);
  }

  @ParameterizedTest
  @ValueSource(strings = { "abc", "-2", "9999", "NaN", "Infinity" })
  void malformedLevelsProduceNoHeight(String value) {
    var dimensions = parse("building", "yes", "building:levels", value);
    assertNull(dimensions.height(), "expected no height for building:levels=" + value);
  }

  @Test
  void fractionalLevelsAreAccepted() {
    var dimensions = parse("building", "yes", "building:levels", "3.5");
    assertEquals(10.5, dimensions.height());
  }

  @ParameterizedTest
  @CsvSource({
    "building:height, 15, 15.0",
    "height, 15, 15.0"
  })
  void heightAliasesAreAccepted(String key, String value, double expected) {
    assertEquals(expected, parse("building", "yes", key, value).height());
  }

  @Test
  void explicitHeightWinsOverLevels() {
    var dimensions = parse("building", "yes", "height", "9", "building:levels", "20");
    assertEquals(9.0, dimensions.height());
    assertFalse(dimensions.estimated());
  }

  @Test
  void heightsAreRoundedToOneDecimalPlace() {
    var dimensions = parse("building", "yes", "height", "12.3456");
    assertEquals(12.3, dimensions.height());
  }
}
