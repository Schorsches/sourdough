package fyi.osm.sourdough.shortbread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fyi.osm.sourdough.shortbread.mapping.LandKinds;
import fyi.osm.sourdough.shortbread.mapping.PoiKinds;
import fyi.osm.sourdough.shortbread.mapping.SiteKinds;
import fyi.osm.sourdough.shortbread.mapping.StreetKinds;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Differential check of this implementation's tag selection against the Shortbread
 * project's own machine-readable tag list.
 *
 * This is the one external artifact that states, in a form a test can read, exactly which
 * OSM key-value combinations the schema consumes. Comparing the mapping tables against it
 * catches a typo or an omission in the 137-entry POI table or the 43-entry land table in a
 * way that reading the prose specification a second time would not.
 *
 * The vendored file is stale: it describes Shortbread 1.0. That is useful rather than a
 * problem, because the difference between it and this implementation should be *exactly*
 * the documented 1.0 to 1.1 delta and nothing else. Anything extra is a bug on one side or
 * the other.
 *
 * Tagged `differential` and excluded from `mvn test` by default. Run with:
 *
 *   mvn test -DexcludedTestGroups= -Dtest=TaginfoDifferentialTest
 */
@Tag("differential")
class TaginfoDifferentialTest {

  /** A key-value pair from taginfo, or a bare key when no value is given. */
  private record TagEntry(String key, String value) {
    @Override
    public String toString() {
      return value == null ? key : key + "=" + value;
    }
  }

  private static List<TagEntry> taginfo() throws IOException {
    // A deliberately small hand-rolled reader: adding a JSON dependency to the build for
    // one test file would be a poor trade.
    String json;
    try (InputStream in =
      TaginfoDifferentialTest.class.getResourceAsStream("/shortbread/taginfo.json")) {
      assertTrue(in != null, "vendored taginfo.json is missing from test resources");
      json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }
    var entries = new ArrayList<TagEntry>();
    Matcher objects = Pattern.compile("\\{[^{}]*\"key\"[^{}]*\\}").matcher(json);
    while (objects.find()) {
      var object = objects.group();
      entries.add(new TagEntry(field(object, "key"), field(object, "value")));
    }
    return List.copyOf(entries);
  }

  private static String field(String object, String name) {
    Matcher matcher =
      Pattern.compile("\"" + name + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(object);
    return matcher.find() ? matcher.group(1) : null;
  }

  /**
   * The seven changes between Shortbread 1.0 and 1.1 that affect tag selection. The
   * vendored taginfo predates all of them.
   */
  private static final Set<String> ADDED_IN_1_1 =
    Set.of("waterway=drain", "amenity=fuel", "leisure=dog_park");

  private static final Set<String> REMOVED_IN_1_1 =
    Set.of("amenity=playground", "amenity=dog_park");

  /**
   * Defects in the vendored taginfo file itself, confirmed against the specification
   * prose at the pinned revision. This implementation follows the specification.
   *
   * - The specification lists `tourism=artwork` in the pois layer (1.1.md line 851);
   *   taginfo records it under the `historic` key instead.
   * - The specification lists `landuse=plant_nursery`; taginfo's value carries a stray
   *   trailing backtick.
   */
  private static final Set<String> TAGINFO_DEFECTS =
    Set.of("historic=artwork", "landuse=plant_nursery`");

  /** Everything this implementation selects, as key=value strings. */
  private static Set<String> selectedByThisImplementation() {
    var selected = new LinkedHashSet<String>();
    for (var key : PoiKinds.KEYS) {
      for (var value : PoiKinds.valuesFor(key)) {
        selected.add(key + "=" + value);
      }
    }
    for (var key : LandKinds.KEYS) {
      for (var value : LandKinds.valuesFor(key)) {
        selected.add(key + "=" + value);
      }
    }
    for (var key : SiteKinds.KEYS) {
      for (var value : SiteKinds.valuesFor(key)) {
        selected.add(key + "=" + value);
      }
    }
    for (var value : StreetKinds.highwayValues()) {
      selected.add("highway=" + value);
    }
    for (var value : StreetKinds.aerowayValues()) {
      selected.add("aeroway=" + value);
    }
    for (var value : StreetKinds.railwayValues()) {
      selected.add("railway=" + value);
    }
    return selected;
  }

  @Test
  void theVendoredTaginfoIsTheOneWeThinkItIs() throws IOException {
    var entries = taginfo();
    assertEquals(267, entries.size(), "vendored taginfo.json changed unexpectedly");
  }

  @Test
  void everyTaginfoValueThisSchemaClassifiesIsSelected() throws IOException {
    var selected = selectedByThisImplementation();
    // Keys whose values this implementation classifies into a `kind` or a POI property.
    var classifiedKeys = Set.of(
      "amenity", "leisure", "tourism", "shop", "man_made", "historic", "emergency",
      "office", "landuse", "natural", "wetland", "aerialway", "place", "waterway",
      "aeroway"
    );

    var missing = new TreeSet<String>();
    for (var entry : taginfo()) {
      if (entry.value() == null) continue;
      if (!classifiedKeys.contains(entry.key())) continue;
      var pair = entry.toString();
      if (REMOVED_IN_1_1.contains(pair)) continue;
      if (TAGINFO_DEFECTS.contains(pair)) continue;
      if (!selected.contains(pair) && !handledElsewhere(entry)) {
        missing.add(pair);
      }
    }
    assertEquals(
      new TreeSet<String>(),
      missing,
      "taginfo lists these tags but this implementation does not select them"
    );
  }

  /**
   * Values classified by a layer that does not expose a lookup table, because the layer
   * has a single hard-coded kind or a small inline map.
   */
  private static boolean handledElsewhere(TagEntry entry) {
    return switch (entry.key()) {
      // ocean, water_polygons, dams and piers.
      case "waterway" -> Set.of("riverbank", "dock", "canal", "dam", "river", "stream", "ditch")
        .contains(entry.value());
      case "natural" -> Set.of("water", "glacier", "coastline").contains(entry.value());
      case "man_made" -> Set.of("pier", "breakwater", "groyne", "bridge").contains(entry.value());
      // place_labels, aerialways and public_transport use inline tables.
      case "place", "aerialway" -> true;
      case "aeroway" -> Set.of("aerodrome", "helipad").contains(entry.value());
      case "amenity" -> Set.of("ferry_terminal", "bus_station").contains(entry.value());
      case "landuse" -> Set.of("reservoir", "basin").contains(entry.value());
      default -> false;
    };
  }

  @Test
  void theOnlyExtrasAreTheDocumentedOnePointOneAdditions() throws IOException {
    var taginfoPairs = new TreeSet<String>();
    for (var entry : taginfo()) {
      if (entry.value() != null) taginfoPairs.add(entry.toString());
    }

    // Restricted to the POI layer, whose selection taginfo describes exhaustively.
    var extras = new TreeSet<String>();
    for (var key : PoiKinds.KEYS) {
      for (var value : PoiKinds.valuesFor(key)) {
        var pair = key + "=" + value;
        if (!taginfoPairs.contains(pair)) extras.add(pair);
      }
    }
    // `tourism=artwork` shows up here because taginfo files it under `historic` instead,
    // which is a defect in taginfo rather than in this implementation.
    var expected = new TreeSet<>(ADDED_IN_1_1);
    expected.remove("waterway=drain"); // water_lines, not a POI
    expected.add("tourism=artwork");
    assertEquals(
      expected,
      extras,
      "this implementation selects POI tags that neither taginfo nor the 1.1 delta explains"
    );
  }

  @Test
  void theKnownTaginfoDefectsAreStillPresentUpstream() throws IOException {
    // If upstream fixes these, this test should fail so that the allowance is removed
    // rather than quietly masking a future real difference.
    var pairs = new TreeSet<String>();
    for (var entry : taginfo()) {
      if (entry.value() != null) pairs.add(entry.toString());
    }
    for (var defect : TAGINFO_DEFECTS) {
      assertTrue(pairs.contains(defect), defect + " appears to have been fixed upstream");
    }
  }

  @Test
  void thePoiTagsRemovedInOnePointOneAreNoLongerSelected() {
    var selected = selectedByThisImplementation();
    for (var removed : REMOVED_IN_1_1) {
      assertTrue(
        !selected.contains(removed),
        removed + " was removed from the pois layer in Shortbread 1.1"
      );
    }
  }
}
