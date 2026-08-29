package fyi.osm.sourdough;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onthegomap.planetiler.config.Arguments;
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadNames;
import fyi.osm.sourdough.shortbread.ShortbreadProfile;
import fyi.osm.sourdough.shortbread.ShortbreadSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** How a schema is chosen, and what that choice implies. */
class SchemaSelectionTest {

  @Test
  void sourdoughIsTheDefaultAndKeepsItsOwnMaxzoom() {
    assertEquals(Schema.SOURDOUGH, Schema.fromId("sourdough"));
    assertEquals(15, Schema.SOURDOUGH.defaultMaxzoom());
    assertFalse(Schema.SOURDOUGH.isShortbread());
    assertFalse(Schema.SOURDOUGH.hasBuildings3d());
  }

  @Test
  void shortbreadSchemasAreFixedAtZoom14() {
    assertEquals(14, Schema.SHORTBREAD.defaultMaxzoom());
    assertEquals(14, Schema.SHORTBREAD_3D.defaultMaxzoom());
    assertEquals(ShortbreadSchema.MAXZOOM, Schema.SHORTBREAD.defaultMaxzoom());
  }

  @Test
  void onlyTheThreeDSchemaEnablesTheExtension() {
    assertFalse(Schema.SHORTBREAD.hasBuildings3d());
    assertTrue(Schema.SHORTBREAD_3D.hasBuildings3d());
  }

  @Test
  void anUnknownSchemaNameIsRejectedWithTheValidOnes() {
    var error = assertThrows(IllegalArgumentException.class, () -> Schema.fromId("shortbread"));
    assertTrue(error.getMessage().contains("shortbread-1.1"), error.getMessage());
  }

  @Test
  void aMaxzoomAboveTheShortbreadLimitIsRejected() {
    var args = Arguments.of("schema", "shortbread-1.1", "maxzoom", "15", "area", "monaco");
    var error = assertThrows(IllegalArgumentException.class, () -> Builder.run(args));
    assertTrue(error.getMessage().contains("fixed maximum zoom"), error.getMessage());
    assertTrue(error.getMessage().contains("overzoomed"), error.getMessage());
  }

  @Test
  void aLowerMaxzoomIsStillAllowedForShortbread() {
    // Only exceeding the limit is an error; building fewer zooms is the user's choice.
    var args = Arguments.of("schema", "shortbread-1.1", "maxzoom", "10", "area", "monaco");
    // It fails later, on missing input data, not on the zoom check.
    var error = assertThrows(Exception.class, () -> Builder.run(args));
    assertFalse(String.valueOf(error.getMessage()).contains("fixed maximum zoom"));
  }

  @Test
  void sourdoughAcceptsItsOwnHigherMaxzoom() {
    var args = Arguments.of("schema", "sourdough", "maxzoom", "15", "area", "monaco");
    var error = assertThrows(Exception.class, () -> Builder.run(args));
    assertFalse(String.valueOf(error.getMessage()).contains("fixed maximum zoom"));
  }

  @Test
  void theProfileDescribesItselfDifferentlyForEachSchema() {
    var base = new ShortbreadProfile(ShortbreadConfiguration.defaults(Schema.SHORTBREAD));
    var threeD = new ShortbreadProfile(ShortbreadConfiguration.defaults(Schema.SHORTBREAD_3D));
    assertEquals("Shortbread 1.1", base.name());
    assertTrue(threeD.name().contains("3D"));
    assertTrue(base.attribution().contains("openstreetmap.org/copyright"));
  }

  // --- names ----------------------------------------------------------------

  @Test
  void shortbreadUsesUnderscoreLanguageAttributes() {
    assertEquals("name_de", ShortbreadNames.attributeFor("de"));
    // IETF subtags are kept intact.
    assertEquals("name_ko-Latn", ShortbreadNames.attributeFor("ko-Latn"));
  }

  @Test
  void nameIsTheOsmNameTagAndVariantsAreAdded() {
    var config = new ShortbreadConfiguration(Schema.SHORTBREAD, List.of("de", "ko-Latn", "fr"), 3.0);
    var feature = TestSupport.processOne(
      new fyi.osm.sourdough.shortbread.layers.PlaceLabels(config),
      TestSupport.node(
        Map.of(
          "place", "city",
          "name", "Köln",
          "name:de", "Köln",
          "name:en", "Cologne",
          "name:ko-Latn", "Kellen"
        )
      )
    );
    var attrs = feature.getAttrsAtZoom(14);
    assertEquals("Köln", attrs.get("name"), "name is the OSM name tag, untranslated");
    assertEquals("Köln", attrs.get("name_de"));
    assertEquals("Kellen", attrs.get("name_ko-Latn"));
    assertFalse(attrs.containsKey("name_fr"), "a missing translation emits nothing");
    assertFalse(attrs.containsKey("name_en"), "only configured languages are emitted");
    assertFalse(attrs.containsKey("name:de"), "Sourdough's colon convention is not used here");
  }

  @Test
  void noLanguagesAreEmittedByDefault() {
    var feature = TestSupport.processOne(
      new fyi.osm.sourdough.shortbread.layers.PlaceLabels(ShortbreadConfiguration.defaults()),
      TestSupport.node(Map.of("place", "city", "name", "Köln", "name:de", "Köln"))
    );
    var attrs = feature.getAttrsAtZoom(14);
    assertEquals("Köln", attrs.get("name"));
    assertEquals(1, attrs.keySet().stream().filter(k -> k.startsWith("name")).count());
  }
}
