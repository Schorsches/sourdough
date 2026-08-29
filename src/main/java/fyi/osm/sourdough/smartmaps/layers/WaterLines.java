package fyi.osm.sourdough.smartmaps.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.util.Parse;
import com.onthegomap.planetiler.util.SortKey;
import fyi.osm.sourdough.common.Booleans;
import fyi.osm.sourdough.smartmaps.SmartMapsConfiguration;
import fyi.osm.sourdough.smartmaps.SmartMapsLayer;
import fyi.osm.sourdough.smartmaps.SmartMapsNames;
import fyi.osm.sourdough.smartmaps.SmartMapsSchema;
import java.util.List;
import java.util.Map;

/**
 * SmartMaps `water_lines`, and the line half of `water_label`.
 *
 * `intermittent` is carried here and not in Shortbread: the layout has a field for it, and
 * a seasonal watercourse is worth drawing differently.
 */
public class WaterLines extends SmartMapsLayer implements ForwardingProfile.LayerPostProcessor {

  public WaterLines(SmartMapsConfiguration config) {
    super(config);
  }

  @Override
  public String name() {
    return SmartMapsSchema.WATER_LINES;
  }

  private static final Map<String, Integer> KIND_MIN_ZOOM = Map.of(
    "canal", 9,
    "river", 9,
    "stream", 14,
    "drain", 14,
    "ditch", 14
  );

  private static final Map<String, Integer> LABEL_MIN_ZOOM = Map.of(
    "canal", 12,
    "river", 12,
    "stream", 14,
    "drain", 14,
    "ditch", 14
  );

  private static final int MIN_LAYER = -5;
  private static final int MAX_LAYER = 5;

  @Override
  public Expression filter() {
    return Expression.matchAny("waterway", List.copyOf(KIND_MIN_ZOOM.keySet()));
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    if (!sf.canBeLine()) return;
    var kind = sf.getString("waterway");
    var minZoom = KIND_MIN_ZOOM.get(kind);
    if (minZoom == null) return;

    boolean tunnel = Booleans.tunnel(sf);
    boolean bridge = Booleans.bridge(sf);
    boolean intermittent = sf.hasTag("intermittent", "yes");

    var line = fc.line(name());
    line.setMinZoom(minZoom);
    line.setMinPixelSize(0.25);
    line.setBufferPixels(4);
    line.setSortKey(sortKey(sf));
    line.setAttr("kind", kind);
    if (tunnel) line.setAttr("tunnel", true);
    if (bridge) line.setAttr("bridge", true);
    if (intermittent) line.setAttr("intermittent", true);

    if (sf.getString("name") != null) {
      var label = fc.line(SmartMapsSchema.WATER_LABEL);
      label.setMinZoom(LABEL_MIN_ZOOM.get(kind));
      label.setMinPixelSize(0.25);
      label.setBufferPixels(8);
      label.setAttr("kind", kind);
      if (tunnel) label.setAttr("tunnel", true);
      if (bridge) label.setAttr("bridge", true);
      SmartMapsNames.setNames(sf, label, config.languages());
    }
  }

  /** Ascending by the OSM `layer` tag, so crossings stack correctly. */
  private static int sortKey(SourceFeature sf) {
    Integer layer = Parse.parseIntOrNull(sf.getString("layer"));
    int value = layer == null ? 0 : Math.clamp(layer, MIN_LAYER, MAX_LAYER);
    return SortKey.orderByInt(value, MIN_LAYER, MAX_LAYER).get();
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items)
    throws GeometryException {
    return FeatureMerge.mergeLineStrings(items, 0.25, 0.25, 4);
  }
}
