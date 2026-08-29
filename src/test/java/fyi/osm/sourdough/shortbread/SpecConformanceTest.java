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
import fyi.osm.sourdough.common.mapping.PoiKinds;
import fyi.osm.sourdough.common.mapping.StreetKinds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Drives the profile from the specification's own feature tables.
 *
 * For every row of every layer's Features table, this builds a feature carrying the tags
 * that row names, runs the whole profile over it, and checks that the layer emits the
 * `kind` the row specifies at the zoom the row specifies.
 *
 * The point is that the specification document is the thing being checked against, rather
 * than a transcription of it into test code, which could be wrong in exactly the same way
 * the implementation is. Upgrading to a later Shortbread version starts by replacing the
 * vendored file: this test then names every kind and zoom that moved.
 */
class SpecConformanceTest {

  private static final PlanetilerConfig CONFIG = PlanetilerConfig.from(
    Arguments.of("maxzoom", Integer.toString(ShortbreadSchema.MAXZOOM))
  );

  /**
   * Layers whose feature tables describe something other than a kind-per-tag mapping, and
   * which have dedicated tests instead: the two boundary layers key on `admin_level`
   * rather than `kind`, and the POI selection is a bullet list, checked separately below.
   */
  private static final Set<String> NOT_KIND_TABLES =
    Set.of("boundaries", "boundary_labels", "pois");

  /**
   * Layers whose Features table has no OSM tag column. Their tags are implied by the kind,
   * so they are checked against the mapping table rather than by driving the profile.
   */
  private static final Set<String> NO_TAG_COLUMN = Set.of("streets", "street_labels");

  /**
   * Label layers are produced from an area even though they emit a point, so the input
   * fixture has to be a polygon rather than the row's output geometry.
   */
  private static final Set<String> POLYGON_INPUT =
    Set.of("streets_polygons_labels", "water_polygons_labels");

  private static List<FeatureCollector.Feature> emit(SourceFeature sf) {
    var profile = new ShortbreadProfile(ShortbreadConfiguration.defaults(Schema.SHORTBREAD));
    var collector = new FeatureCollector.Factory(CONFIG, Stats.inMemory()).get(sf);
    profile.processFeature(sf, collector);
    var features = new ArrayList<FeatureCollector.Feature>();
    collector.forEach(features::add);
    return features;
  }

  private static SourceFeature fixture(SpecTables.FeatureRow row, List<String> tags) {
    var map = new HashMap<String, Object>();
    for (var tag : tags) {
      int split = tag.indexOf('=');
      map.put(tag.substring(0, split), tag.substring(split + 1));
    }
    // Label layers only emit for named features, and a name never changes classification.
    map.putIfAbsent("name", "Test");

    var geometry = POLYGON_INPUT.contains(row.layer())
      ? "polygon"
      : row.geometry() != null
        ? row.geometry()
        : switch (ShortbreadSchema.layer(row.layer()).geometry()) {
          case POINT -> "point";
          case LINE -> "line";
          case POLYGON -> "polygon";
        };

    return switch (geometry) {
      case "point" -> TestSupport.node(map);
      case "line" -> TestSupport.longWay(map);
      default -> TestSupport.area(map, 0.02);
    };
  }

  /** Every zoom the specification allows for a given layer and kind. */
  private static Map<String, Set<Integer>> allowedZooms() {
    var allowed = new LinkedHashMap<String, Set<Integer>>();
    SpecTables.featureTables().forEach((layer, rows) -> {
      for (var row : rows) {
        if (row.kind() == null) continue;
        allowed.computeIfAbsent(layer + "/" + row.kind(), k -> new TreeSet<>()).add(row.minZoom());
      }
    });
    return allowed;
  }

  @Test
  void everySpecifiedFeatureIsEmittedWithItsKindAndZoom() {
    var allowed = allowedZooms();
    var problems = new TreeSet<String>();
    int checked = 0;

    for (var entry : SpecTables.featureTables().entrySet()) {
      var layer = entry.getKey();
      if (NOT_KIND_TABLES.contains(layer) || NO_TAG_COLUMN.contains(layer)) continue;

      for (var row : entry.getValue()) {
        if (row.kind() == null || !row.hasTags()) continue;

        for (var alternative : row.alternatives()) {
          checked++;
          var emitted = emit(fixture(row, alternative));
          var match = emitted.stream()
            .filter(f -> f.getLayer().equals(layer))
            .filter(f -> row.kind().equals(f.getAttrsAtZoom(ShortbreadSchema.MAXZOOM).get("kind")))
            .findFirst();

          if (match.isEmpty()) {
            var got = emitted.stream()
              .map(f -> f.getLayer() + "/" + f.getAttrsAtZoom(ShortbreadSchema.MAXZOOM).get("kind"))
              .toList();
            problems.add(
              layer + "/" + row.kind() + " for " + alternative + ": nothing matched, got " + got
            );
            continue;
          }

          int minZoom = match.get().getMinZoom();
          var zooms = allowed.get(layer + "/" + row.kind());
          if (!zooms.contains(minZoom)) {
            problems.add(
              layer + "/" + row.kind() + " for " + alternative +
              ": emitted at zoom " + minZoom + ", specification says " + zooms
            );
          }
        }
      }
    }

    assertTrue(checked > 80, "expected the specification to yield many rows, got " + checked);
    if (!problems.isEmpty()) {
      fail(problems.size() + " mismatches against the specification:\n" + String.join("\n", problems));
    }
  }

  @Test
  void theStreetsTableMatchesTheSpecifiedKindsAndZooms() {
    var rows = SpecTables.featureTables().get("streets");
    var problems = new TreeSet<String>();

    for (var row : rows) {
      var street = lookupStreetKind(row.kind());
      if (street == null) {
        problems.add("streets/" + row.kind() + " is not classified at all");
        continue;
      }
      if (street.minZoom() != row.minZoom()) {
        problems.add(
          "streets/" + row.kind() + " starts at zoom " + street.minZoom() +
          ", specification says " + row.minZoom()
        );
      }
    }
    assertEquals(new TreeSet<String>(), problems);
    assertEquals(26, rows.size(), "the streets table should have 26 rows");
  }

  @Test
  void theStreetLabelsTableMatchesTheSpecifiedKindsAndZooms() {
    var problems = new TreeSet<String>();
    for (var row : SpecTables.featureTables().get("street_labels")) {
      // street_labels spells link roads out, so the kind is the lookup key directly.
      var street = lookupByLabelKind(row.kind());
      if (street == null) {
        problems.add("street_labels/" + row.kind() + " is not classified at all");
        continue;
      }
      if (street.labelMinZoom() != row.minZoom()) {
        problems.add(
          "street_labels/" + row.kind() + " starts at zoom " + street.labelMinZoom() +
          ", specification says " + row.minZoom()
        );
      }
    }
    assertEquals(new TreeSet<String>(), problems);
  }

  private static StreetKinds.Street lookupStreetKind(String kind) {
    for (var value : StreetKinds.highwayValues()) {
      var street = StreetKinds.lookup(tags("highway", value));
      if (street != null && street.kind().equals(kind) && !street.link()) return street;
    }
    for (var value : StreetKinds.aerowayValues()) {
      var street = StreetKinds.lookup(tags("aeroway", value));
      if (street != null && street.kind().equals(kind)) return street;
    }
    for (var value : StreetKinds.railwayValues()) {
      var street = StreetKinds.lookup(tags("railway", value));
      if (street != null && street.kind().equals(kind)) return street;
    }
    return null;
  }

  private static StreetKinds.Street lookupByLabelKind(String kind) {
    for (var key : List.of("highway", "aeroway", "railway")) {
      var values = switch (key) {
        case "highway" -> StreetKinds.highwayValues();
        case "aeroway" -> StreetKinds.aerowayValues();
        default -> StreetKinds.railwayValues();
      };
      for (var value : values) {
        var street = StreetKinds.lookup(tags(key, value));
        if (street != null && street.labelKind().equals(kind)) return street;
      }
    }
    return null;
  }

  private static com.onthegomap.planetiler.reader.WithTags tags(String key, String value) {
    return com.onthegomap.planetiler.reader.SimpleFeature.create(
      com.onthegomap.planetiler.TestUtils.newPoint(0, 0),
      Map.of(key, value)
    );
  }

  @Test
  void thePoiSelectionMatchesTheSpecificationExactly() {
    var specified = new TreeSet<>(SpecTables.poiTags());
    var selected = new TreeSet<String>();
    for (var key : PoiKinds.KEYS) {
      for (var value : PoiKinds.valuesFor(key)) {
        selected.add(key + "=" + value);
      }
    }
    assertEquals(137, specified.size(), "the specification lists 137 POI combinations");
    assertEquals(specified, selected, "the pois layer selection has drifted from the specification");
  }

  @Test
  void theLayerInventoryMatchesTheSpecification() {
    var specified = new TreeSet<>(SpecTables.featureTables().keySet());
    var implemented = new TreeSet<>(ShortbreadSchema.layerNames());
    assertEquals(specified, implemented, "layer inventory differs from the specification");
    assertEquals(26, specified.size());
  }
}
