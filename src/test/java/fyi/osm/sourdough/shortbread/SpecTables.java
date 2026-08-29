package fyi.osm.sourdough.shortbread;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the feature tables out of the vendored Shortbread specification.
 *
 * Parsing the specification directly means the tests check the implementation against the
 * document rather than against a second transcription of it, which would be just as likely
 * to be wrong. It also makes a version upgrade start with replacing one file.
 *
 * The parser is deliberately narrow: it understands the specific table shapes this
 * document uses and nothing more.
 */
public final class SpecTables {

  private SpecTables() {}

  private static final String RESOURCE = "/shortbread/shortbread-1.1.md";

  /**
   * One row of a layer's Features table.
   *
   * @param layer the layer the table belongs to
   * @param kind the `kind` value, or null for tables keyed on something else
   * @param alternatives the OSM tag combinations that select this feature. The outer list
   *     is alternatives ("A or B"), the inner list a conjunction ("A + B"), each entry a
   *     "key=value" string
   * @param geometry the geometry column, when the table has one
   * @param minZoom the first number in the Zoom column
   */
  public record FeatureRow(
    String layer,
    String kind,
    List<List<String>> alternatives,
    String geometry,
    int minZoom
  ) {
    /** True when the row names at least one OSM tag combination. */
    public boolean hasTags() {
      return !alternatives.isEmpty();
    }
  }

  private static String source() {
    try (InputStream in = SpecTables.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("vendored specification missing: " + RESOURCE);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("could not read " + RESOURCE, e);
    }
  }

  private static final Pattern LAYER_HEADING = Pattern.compile("^### Layer \"([a-z_]+)\"$");
  private static final Pattern TAG_SHORTCODE =
    Pattern.compile("\\{\\{<\\s*tag\\s+([^>]*?)\\s*>\\}\\}");
  private static final Pattern KIND = Pattern.compile("`([^`]+)`");
  // Zoom cells come in several shapes: "14+", "8/10+" for railways with and without a
  // service tag, and prose like "available if their line is longer than 0.25 pixel but
  // not below 9". The earliest zoom the feature can appear at is what matters here.
  private static final Pattern SPLIT_ZOOM = Pattern.compile("(\\d+)/(\\d+)\\+");
  private static final Pattern PLUS_ZOOM = Pattern.compile("(\\d+)\\+");
  private static final Pattern NOT_BELOW_ZOOM = Pattern.compile("not below (\\d+)");
  private static final Pattern ANY_NUMBER = Pattern.compile("(\\d+)");

  /** Every layer's Features table, keyed by layer name, in document order. */
  public static Map<String, List<FeatureRow>> featureTables() {
    var byLayer = new LinkedHashMap<String, List<FeatureRow>>();
    String layer = null;
    boolean inFeatures = false;
    List<String> header = null;

    for (var line : source().split("\n")) {
      var heading = LAYER_HEADING.matcher(line.trim());
      if (heading.matches()) {
        layer = heading.group(1);
        inFeatures = false;
        header = null;
        byLayer.putIfAbsent(layer, new ArrayList<>());
        continue;
      }
      if (layer == null) continue;

      if (line.startsWith("#### ")) {
        inFeatures = line.trim().equals("#### Features");
        header = null;
        continue;
      }
      if (!inFeatures || !line.trim().startsWith("|")) {
        if (!line.trim().startsWith("|")) header = null;
        continue;
      }

      var cells = cells(line);
      if (header == null) {
        header = cells;
        continue;
      }
      // The dashed separator row under the header.
      if (cells.stream().allMatch(c -> c.replace(":", "").replace("-", "").isBlank())) {
        continue;
      }

      var row = parseRow(layer, header, cells);
      if (row != null) byLayer.get(layer).add(row);
    }
    return byLayer;
  }

  private static List<String> cells(String line) {
    var trimmed = line.trim();
    // Strip the leading and trailing pipes before splitting, so empty edge cells do not
    // appear as columns.
    if (trimmed.startsWith("|")) trimmed = trimmed.substring(1);
    if (trimmed.endsWith("|")) trimmed = trimmed.substring(0, trimmed.length() - 1);
    return java.util.Arrays.stream(trimmed.split("\\|", -1)).map(String::trim).toList();
  }

  private static FeatureRow parseRow(String layer, List<String> header, List<String> cells) {
    int kindColumn = columnOf(header, "value of `kind`");
    int tagColumn = columnOf(header, "osm tag");
    int geometryColumn = columnOf(header, "geometry");
    int zoomColumn = columnOf(header, "zoom");
    if (zoomColumn < 0 || zoomColumn >= cells.size()) return null;

    Integer parsedZoom = minZoomOf(cells.get(zoomColumn));
    if (parsedZoom == null) return null;
    int minZoom = parsedZoom;

    String kind = null;
    if (kindColumn >= 0 && kindColumn < cells.size()) {
      var kindMatcher = KIND.matcher(cells.get(kindColumn));
      if (kindMatcher.find()) kind = kindMatcher.group(1);
    }

    var alternatives = tagColumn >= 0 && tagColumn < cells.size()
      ? parseTagExpression(cells.get(tagColumn))
      : List.<List<String>>of();

    String geometry = null;
    if (geometryColumn >= 0 && geometryColumn < cells.size()) {
      geometry = cells.get(geometryColumn);
    }
    return new FeatureRow(layer, kind, alternatives, geometry, minZoom);
  }

  /**
   * Parses an OSM tag cell into alternatives of conjunctions.
   *
   * The cells use three shapes: "A" on its own, "A or B" and "A, B" for alternatives, and
   * "A + B" for a conjunction, which combine as in "{{< tag waterway riverbank >}},
   * {{< tag natural water >}} + {{< tag water river >}}".
   */
  static List<List<String>> parseTagExpression(String cell) {
    var alternatives = new ArrayList<List<String>>();
    for (var alternative : cell.split(",| or ")) {
      var conjuncts = new ArrayList<String>();
      var matcher = TAG_SHORTCODE.matcher(alternative);
      while (matcher.find()) {
        // A shortcode is "key value [value...]"; quotes wrap keys containing a colon.
        var parts = matcher.group(1).replace("\"", "").trim().split("\\s+");
        if (parts.length >= 2) {
          // Several values on one shortcode mean "any of these", so the first is enough
          // to exercise the row.
          conjuncts.add(parts[0] + "=" + parts[1]);
        }
      }
      if (!conjuncts.isEmpty()) alternatives.add(List.copyOf(conjuncts));
    }
    return List.copyOf(alternatives);
  }

  /** The earliest zoom a feature can appear at, from the Zoom column's text. */
  static Integer minZoomOf(String zoomCell) {
    var split = SPLIT_ZOOM.matcher(zoomCell);
    if (split.find()) return Integer.parseInt(split.group(1));
    var plus = PLUS_ZOOM.matcher(zoomCell);
    if (plus.find()) return Integer.parseInt(plus.group(1));
    var notBelow = NOT_BELOW_ZOOM.matcher(zoomCell);
    if (notBelow.find()) return Integer.parseInt(notBelow.group(1));
    var any = ANY_NUMBER.matcher(zoomCell);
    return any.find() ? Integer.parseInt(any.group(1)) : null;
  }

  private static int columnOf(List<String> header, String name) {
    for (int i = 0; i < header.size(); i++) {
      if (header.get(i).toLowerCase(java.util.Locale.ROOT).contains(name)) return i;
    }
    return -1;
  }

  /** The key-value combinations listed as bullet points in the pois layer. */
  public static List<String> poiTags() {
    var tags = new ArrayList<String>();
    boolean inPois = false;
    for (var line : source().split("\n")) {
      if (line.trim().startsWith("### Layer ")) {
        inPois = line.contains("\"pois\"");
        continue;
      }
      if (!inPois || !line.trim().startsWith("- ")) continue;
      var matcher = TAG_SHORTCODE.matcher(line);
      while (matcher.find()) {
        var parts = matcher.group(1).replace("\"", "").trim().split("\\s+");
        if (parts.length >= 2) tags.add(parts[0] + "=" + parts[1]);
      }
    }
    return List.copyOf(tags);
  }
}
