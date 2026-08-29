package fyi.osm.sourdough.smartmaps.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.WithTags;
import com.onthegomap.planetiler.util.Parse;
import com.onthegomap.planetiler.util.SortKey;
import fyi.osm.sourdough.smartmaps.SmartMapsConfiguration;
import fyi.osm.sourdough.smartmaps.SmartMapsLayer;
import fyi.osm.sourdough.smartmaps.SmartMapsNames;
import fyi.osm.sourdough.smartmaps.SmartMapsSchema;
import java.util.List;
import java.util.Map;

/** SmartMaps `place_label`: populated places, sorted by population descending. */
public class PlaceLabel extends SmartMapsLayer {

  public PlaceLabel(SmartMapsConfiguration config) {
    super(config);
  }

  @Override
  public String name() {
    return SmartMapsSchema.PLACE_LABEL;
  }

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

  private static final double MAX_POPULATION = 50_000_000;

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.matchAny("place", List.copyOf(DEFAULT_POPULATION.keySet())),
      Expression.matchField("name")
    );
  }

  static String kind(WithTags sf) {
    var place = sf.getString("place");
    if (place == null || !DEFAULT_POPULATION.containsKey(place)) return null;
    if (CAPITAL_PLACES.contains(place)) {
      if (sf.hasTag("capital", "yes")) return "capital";
      if (sf.hasTag("capital", "4")) return "state_capital";
    }
    return place;
  }

  static int population(WithTags sf) {
    Integer tagged = Parse.parseIntOrNull(sf.getString("population"));
    if (tagged != null && tagged >= 0) return tagged;
    return DEFAULT_POPULATION.getOrDefault(sf.getString("place"), 0);
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    var kind = kind(sf);
    if (kind == null || sf.getString("name") == null) return;
    if (!sf.isPoint() && !sf.canBePolygon()) return;

    int population = population(sf);
    var point = sf.isPoint() ? fc.point(name()) : fc.pointOnSurface(name());
    point.setMinZoom(MIN_ZOOM.getOrDefault(kind, 10));
    point.setBufferPixels(64);
    point.setAttr("kind", kind);
    point.setAttr("population", population);
    point.setSortKey(SortKey.orderByLog(population, MAX_POPULATION, 1, 1000).get());
    SmartMapsNames.setNames(sf, point, config.languages());
  }
}
