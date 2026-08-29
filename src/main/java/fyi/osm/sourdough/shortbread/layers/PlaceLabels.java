package fyi.osm.sourdough.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.WithTags;
import com.onthegomap.planetiler.util.Parse;
import com.onthegomap.planetiler.util.SortKey;
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadLayer;
import fyi.osm.sourdough.shortbread.ShortbreadNames;
import fyi.osm.sourdough.shortbread.ShortbreadSchema;
import java.util.List;
import java.util.Map;

/**
 * Shortbread `place_labels`: labels for populated places, sorted by population
 * descending.
 *
 * Deviation from the specification: the default population for capitals is given as
 * "depends on place *", but the footnote that the asterisk refers to was never written.
 * Capitals here fall back to the default for their underlying place value, which is the
 * reading that keeps the population sort meaningful. See SHORTBREAD_SCHEMA.md.
 */
public class PlaceLabels extends ShortbreadLayer {

  public PlaceLabels(ShortbreadConfiguration config) {
    super(config);
  }

  @Override
  public String name() {
    return ShortbreadSchema.PLACE_LABELS;
  }

  /** Place values that can be capitals, and therefore change `kind`. */
  private static final List<String> CAPITAL_PLACES = List.of("city", "town", "village", "hamlet");

  private static final Map<String, Integer> MIN_ZOOM = Map.ofEntries(
    Map.entry("capital", 4),
    Map.entry("state_capital", 4),
    Map.entry("city", 6),
    Map.entry("town", 7),
    Map.entry("village", 10),
    Map.entry("hamlet", 10),
    Map.entry("suburb", 10),
    Map.entry("quarter", 10),
    Map.entry("neighbourhood", 10),
    Map.entry("isolated_dwelling", 10),
    Map.entry("farm", 10),
    Map.entry("island", 10),
    Map.entry("locality", 10)
  );

  /** Default population by OSM place value, used when the place has no population tag. */
  private static final Map<String, Integer> DEFAULT_POPULATION = Map.ofEntries(
    Map.entry("city", 100_000),
    Map.entry("town", 5_000),
    Map.entry("village", 100),
    Map.entry("hamlet", 50),
    Map.entry("suburb", 1_000),
    Map.entry("quarter", 500),
    Map.entry("neighbourhood", 100),
    Map.entry("isolated_dwelling", 5),
    Map.entry("farm", 5),
    Map.entry("island", 0),
    Map.entry("locality", 0)
  );

  /** Upper endpoint of the population sort scale; larger than any real settlement. */
  private static final double MAX_POPULATION = 50_000_000;

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.matchAny("place", List.copyOf(DEFAULT_POPULATION.keySet())),
      Expression.matchField("name")
    );
  }

  /** The `kind` value: capitals are reclassified, everything else keeps its place value. */
  static String kind(WithTags sf) {
    var place = sf.getString("place");
    if (place == null || !DEFAULT_POPULATION.containsKey(place)) return null;
    if (CAPITAL_PLACES.contains(place)) {
      if (sf.hasTag("capital", "yes")) return "capital";
      if (sf.hasTag("capital", "4")) return "state_capital";
    }
    return place;
  }

  /**
   * The population to emit. An explicit tag wins; otherwise the default for the
   * underlying place value, which for capitals is what the specification's undefined
   * footnote is read as meaning.
   */
  static int population(WithTags sf) {
    Integer tagged = Parse.parseIntOrNull(sf.getString("population"));
    if (tagged != null && tagged >= 0) return tagged;
    var place = sf.getString("place");
    return DEFAULT_POPULATION.getOrDefault(place, 0);
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    var kind = kind(sf);
    if (kind == null) return;
    if (sf.getString("name") == null) return;
    // Places mapped as areas are represented by a point.
    if (!sf.isPoint() && !sf.canBePolygon()) return;

    int population = population(sf);

    var point = sf.isPoint() ? fc.point(name()) : fc.pointOnSurface(name());
    point.setMinZoom(MIN_ZOOM.getOrDefault(kind, 10));
    point.setBufferPixels(64);
    point.setAttr("kind", kind);
    point.setAttr("population", population);
    // Highest population first.
    point.setSortKey(SortKey.orderByLog(population, MAX_POPULATION, 1, 1000).get());
    ShortbreadNames.setNames(sf, point, config.languages());
  }
}
