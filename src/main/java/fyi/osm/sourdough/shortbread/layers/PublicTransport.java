package fyi.osm.sourdough.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.WithTags;
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadLayer;
import fyi.osm.sourdough.shortbread.ShortbreadNames;
import fyi.osm.sourdough.shortbread.ShortbreadSchema;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shortbread `public_transport`: stations, stops and airports as points. Areas in OSM are
 * represented by a point.
 */
public class PublicTransport extends ShortbreadLayer {

  public PublicTransport(ShortbreadConfiguration config) {
    super(config);
  }

  /** @param kind the Shortbread kind, @param minZoom the zoom it first appears at */
  private record Stop(String kind, int minZoom) {}

  private static final Map<String, Map<String, Stop>> BY_KEY = build();

  private static Map<String, Map<String, Stop>> build() {
    var byKey = new LinkedHashMap<String, Map<String, Stop>>();
    byKey.put(
      "aeroway",
      Map.of("aerodrome", new Stop("aerodrome", 11), "helipad", new Stop("helipad", 13))
    );
    byKey.put(
      "amenity",
      Map.of(
        "ferry_terminal", new Stop("ferry_terminal", 12),
        "bus_station", new Stop("bus_station", 13)
      )
    );
    byKey.put(
      "railway",
      Map.of(
        "station", new Stop("station", 13),
        "halt", new Stop("halt", 13),
        "tram_stop", new Stop("tram_stop", 14)
      )
    );
    byKey.put("aerialway", Map.of("station", new Stop("aerialway_station", 13)));
    byKey.put("highway", Map.of("bus_stop", new Stop("bus_stop", 14)));
    return Map.copyOf(byKey);
  }

  @Override
  public String name() {
    return ShortbreadSchema.PUBLIC_TRANSPORT;
  }

  @Override
  public Expression filter() {
    return Expression.or(
      BY_KEY.entrySet().stream()
        .map(entry -> Expression.matchAny(entry.getKey(), java.util.List.copyOf(entry.getValue().keySet())))
        .toArray(Expression[]::new)
    );
  }

  private static Stop lookup(WithTags sf) {
    for (var entry : BY_KEY.entrySet()) {
      var value = sf.getString(entry.getKey());
      if (value == null) continue;
      var stop = entry.getValue().get(value);
      if (stop != null) return stop;
    }
    return null;
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    var stop = lookup(sf);
    if (stop == null) return;

    FeatureCollector.Feature point;
    if (sf.isPoint()) {
      point = fc.point(name());
    } else if (sf.canBePolygon()) {
      point = fc.pointOnSurface(name());
    } else {
      return;
    }

    point.setMinZoom(stop.minZoom());
    point.setBufferPixels(16);
    point.setAttr("kind", stop.kind());
    ShortbreadNames.setNames(sf, point, config.languages());
    var iata = sf.getString("iata");
    if (iata != null) {
      point.setAttr("iata", iata);
    }
  }
}
