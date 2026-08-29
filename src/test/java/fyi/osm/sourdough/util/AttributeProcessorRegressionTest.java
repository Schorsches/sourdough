package fyi.osm.sourdough.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import fyi.osm.sourdough.Configuration;
import fyi.osm.sourdough.TestSupport;
import fyi.osm.sourdough.layers.Buildings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Locks in existing Sourdough attribute behavior so that extracting shared parsing or
 * name-resolution logic cannot change the Sourdough tileset by accident.
 *
 * These assertions describe the schema as it shipped in v0.4.0. If one of them fails,
 * the Sourdough output has changed and that change needs to be deliberate.
 */
class AttributeProcessorRegressionTest {

  private static final Configuration NO_LANGUAGES = Configuration.defaults();

  @Test
  void heightIsParsedToMetersAsANumber() {
    var feature = TestSupport.processOne(
      new Buildings(NO_LANGUAGES),
      TestSupport.area(Map.of("building", "yes", "height", "12 m"))
    );
    assertEquals(12.0, (Double) feature.getAttrsAtZoom(14).get("height"), 1e-9);
  }

  @Test
  void buildingLevelsIsParsedToAnInteger() {
    var feature = TestSupport.processOne(
      new Buildings(NO_LANGUAGES),
      TestSupport.area(Map.of("building", "yes", "building:levels", "4"))
    );
    assertEquals(4, feature.getAttrsAtZoom(14).get("building:levels"));
  }

  @Test
  void unparseableNumericTagIsOmittedRatherThanEmittedAsAString() {
    var feature = TestSupport.processOne(
      new Buildings(NO_LANGUAGES),
      TestSupport.area(Map.of("building", "yes", "building:levels", "four"))
    );
    assertNull(feature.getAttrsAtZoom(14).get("building:levels"));
  }

  @Test
  void unknownTagsStayStrings() {
    var feature = TestSupport.processOne(
      new Buildings(NO_LANGUAGES),
      TestSupport.area(Map.of("building", "yes", "roof:shape", "gabled"))
    );
    assertEquals("gabled", feature.getAttrsAtZoom(14).get("roof:shape"));
  }

  @Test
  void languageOptionSubstitutesTheLocalizedNameIntoName() {
    var config = new Configuration("fr", List.of());
    var feature = TestSupport.processOne(
      new Buildings(config),
      TestSupport.area(Map.of("building", "yes", "name", "Town Hall", "name:fr", "Hotel de Ville"))
    );
    assertEquals("Hotel de Ville", feature.getAttrsAtZoom(14).get("name"));
  }

  @Test
  void languageOptionFallsBackToThePlainNameWhenTheTranslationIsMissing() {
    var config = new Configuration("fr", List.of());
    var feature = TestSupport.processOne(
      new Buildings(config),
      TestSupport.area(Map.of("building", "yes", "name", "Town Hall"))
    );
    assertEquals("Town Hall", feature.getAttrsAtZoom(14).get("name"));
  }

  @Test
  void additionalLanguagesAreEmittedWithSourdoughsColonConvention() {
    var config = new Configuration(null, List.of("fr", "de"));
    var feature = TestSupport.processOne(
      new Buildings(config),
      TestSupport.area(
        Map.of("building", "yes", "name", "Town Hall", "name:fr", "Hotel de Ville")
      )
    );
    var attrs = feature.getAttrsAtZoom(14);
    assertEquals("Town Hall", attrs.get("name"));
    assertEquals("Hotel de Ville", attrs.get("name:fr"));
    assertFalse(attrs.containsKey("name:de"), "no attribute for a missing translation");
  }

  @Test
  void buildingsUseTheSourdoughZoomRange() {
    var feature = TestSupport.processOne(
      new Buildings(NO_LANGUAGES),
      TestSupport.area(Map.of("building", "yes")),
      TestSupport.SOURDOUGH_MAXZOOM
    );
    assertEquals("buildings", feature.getLayer());
    assertEquals(11, feature.getMinZoom());
    assertEquals(15, feature.getMaxZoom());
  }
}
