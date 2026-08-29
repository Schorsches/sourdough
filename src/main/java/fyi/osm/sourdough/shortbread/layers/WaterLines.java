package fyi.osm.sourdough.shortbread.layers;

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
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadLayer;
import fyi.osm.sourdough.shortbread.ShortbreadNames;
import fyi.osm.sourdough.shortbread.ShortbreadSchema;
import java.util.List;
import java.util.Map;

/**
 * Shortbread `water_lines` and `water_lines_labels`: linear waterways.
 *
 * Features are sorted by the OSM `layer` tag ascending, defaulting to 0, so that a
 * renderer drawing them in order gets the crossings right.
 *
 * Deviation from the specification: 1.1 added kind=drain to water_lines but left it out
 * of the water_lines_labels zoom table. Named drains are labelled at zoom 14 here, the
 * same as ditches and streams, on the assumption that the omission is an oversight. See
 * SHORTBREAD_SCHEMA.md.
 */
public class WaterLines extends ShortbreadLayer implements ForwardingProfile.LayerPostProcessor {

  public WaterLines(ShortbreadConfiguration config) {
    super(config);
  }

  @Override
  public String name() {
    return ShortbreadSchema.WATER_LINES;
  }

  /** Waterway values that become water lines, and the zoom each appears at. */
  private static final Map<String, Integer> KIND_MIN_ZOOM = Map.of(
    "canal", 9,
    "river", 9,
    "stream", 14,
    "drain", 14,
    "ditch", 14
  );

  /** Labels for the major waterways start three zooms later than the lines. */
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

    var line = fc.line(name());
    line.setMinZoom(minZoom);
    // Rivers and canals are only worth drawing once they are a visible length.
    line.setMinPixelSize(0.25);
    line.setBufferPixels(4);
    line.setSortKey(sortKey(sf));
    line.setAttr("kind", kind);
    if (tunnel) line.setAttr("tunnel", true);
    if (bridge) line.setAttr("bridge", true);

    var name = sf.getString("name");
    if (name != null) {
      var label = fc.line(ShortbreadSchema.WATER_LINES_LABELS);
      label.setMinZoom(LABEL_MIN_ZOOM.get(kind));
      label.setMinPixelSize(0.25);
      label.setBufferPixels(8);
      label.setAttr("kind", kind);
      if (tunnel) label.setAttr("tunnel", true);
      if (bridge) label.setAttr("bridge", true);
      ShortbreadNames.setNames(sf, label, config.languages());
    }
  }

  /** Ascending by the OSM `layer` tag, which defaults to 0. */
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
