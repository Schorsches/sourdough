package fyi.osm.sourdough.smartmaps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fyi.osm.sourdough.common.SchemaDescription.AttrType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Checks {@link SmartMapsSchema} against the vendored TileJSON.
 *
 * The point is the same as SpecConformanceTest's: check against the source document, not
 * against a transcription of it, because a transcription can be wrong in exactly the way
 * the implementation is wrong.
 *
 * What is checked, and what deliberately is not, follows from the document being observed
 * rather than declared:
 *
 * - Layer names must match exactly. Those are not sampling-dependent.
 * - Every non-name field the document lists must be declared, with a compatible type. The
 *   document's field list is a lower bound, so extra declared fields are allowed only
 *   where this implementation has a documented reason.
 * - Per-layer zoom ranges are NOT checked. They are self-contradictory in the source
 *   (water_polygons z14 only, its own labels z0-14), so the Shortbread zoom rules are
 *   used instead; SMARTMAPS_SCHEMA.md records that.
 * - Language fields are not checked here; they are driven by --additional-languages and
 *   covered by SmartMapsNamesTest.
 */
class TileJsonConformanceTest {

  private record TileJsonLayer(String id, Map<String, String> fields) {}

  private static Map<String, TileJsonLayer> tileJson() throws IOException {
    String json;
    try (InputStream in = TileJsonConformanceTest.class.getResourceAsStream("/smartmaps/tiles.json")) {
      assertTrue(in != null, "vendored tiles.json is missing from test resources");
      json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    var layers = new LinkedHashMap<String, TileJsonLayer>();
    // A deliberately small reader: adding a JSON dependency for one test file would be a
    // poor trade, and the document's shape is fixed.
    var layerPattern = Pattern.compile(
      "\\{\"id\":\"([a-z_]+)\",\"fields\":\\{(.*?)\\},\"minzoom\""
    );
    Matcher layerMatcher = layerPattern.matcher(json);
    while (layerMatcher.find()) {
      var fields = new LinkedHashMap<String, String>();
      Matcher fieldMatcher =
        Pattern.compile("\"([^\"]+)\":\"(String|Number|Boolean)\"").matcher(layerMatcher.group(2));
      while (fieldMatcher.find()) {
        fields.put(fieldMatcher.group(1), fieldMatcher.group(2));
      }
      layers.put(layerMatcher.group(1), new TileJsonLayer(layerMatcher.group(1), fields));
    }
    return layers;
  }

  /** Name variants are configuration-driven, so they are excluded from field comparison. */
  private static boolean isNameVariant(String field) {
    return field.startsWith("name:") || field.equals("name_de") || field.equals("name_en");
  }

  private static boolean compatible(String tileJsonType, AttrType declared) {
    return switch (tileJsonType) {
      case "String" -> declared == AttrType.STRING;
      case "Boolean" -> declared == AttrType.BOOLEAN;
      // TileJSON has one Number type; the schema distinguishes counts from measurements.
      case "Number" -> declared == AttrType.INTEGER || declared == AttrType.FLOAT;
      default -> false;
    };
  }

  @Test
  void theVendoredDocumentIsTheOneWeThinkItIs() throws IOException {
    var layers = tileJson();
    assertEquals(12, layers.size(), "vendored tiles.json changed unexpectedly");
    assertEquals(
      360,
      layers.values().stream().mapToInt(l -> l.fields().size()).sum(),
      "vendored tiles.json changed unexpectedly"
    );
  }

  @Test
  void theLayerInventoryMatchesTheDocument() throws IOException {
    assertEquals(
      new TreeSet<>(tileJson().keySet()),
      new TreeSet<>(SmartMapsSchema.layerNames()),
      "layer inventory differs from the TileJSON"
    );
  }

  @Test
  void everyFieldTheDocumentListsIsDeclaredWithACompatibleType() throws IOException {
    var problems = new TreeMap<String, String>();
    for (var layer : tileJson().values()) {
      var spec = SmartMapsSchema.layer(layer.id());
      layer.fields().forEach((field, type) -> {
        if (isNameVariant(field)) return;
        var declared = spec.attributes().get(field);
        if (declared == null) {
          problems.put(layer.id() + "." + field, "not declared");
        } else if (!compatible(type, declared)) {
          problems.put(layer.id() + "." + field, "declared " + declared + ", document says " + type);
        }
      });
    }
    assertEquals(new TreeMap<String, String>(), problems, "fields missing or mistyped");
  }

  @Test
  void everyDeclaredFieldIsEitherInTheDocumentOrDocumentedAsAnAddition() throws IOException {
    // The document's field lists are observed from sampled tiles, so a field it omits is
    // not necessarily absent from the real schema. Additions are allowed, but each one
    // must be listed here so it is a deliberate decision rather than a drift.
    var knownAdditions = Map.of(
      // Ocean has no layer of its own in the document; dropping coastlines would leave a
      // broken map, so it goes to water_polygons as kind=ocean.
      "water_polygons", new TreeSet<>(java.util.List.of()),
      // Protected-area polygons carry these in the document's landcover but not landuse.
      "landuse", new TreeSet<>(java.util.List.of())
    );

    var unexplained = new TreeSet<String>();
    for (var layer : tileJson().values()) {
      var documented = new TreeSet<String>();
      layer.fields().keySet().forEach(f -> {
        if (!isNameVariant(f)) documented.add(f);
      });
      var allowed = knownAdditions.getOrDefault(layer.id(), new TreeSet<>());
      for (var declared : SmartMapsSchema.layer(layer.id()).attributes().keySet()) {
        if (!documented.contains(declared) && !allowed.contains(declared)) {
          unexplained.add(layer.id() + "." + declared);
        }
      }
    }
    assertEquals(new TreeSet<String>(), unexplained, "declared fields the document does not list");
  }
}
