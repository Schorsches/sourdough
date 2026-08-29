package fyi.osm.sourdough.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.WithTags;
import fyi.osm.sourdough.common.Booleans;
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadLayer;
import fyi.osm.sourdough.shortbread.ShortbreadNames;
import fyi.osm.sourdough.shortbread.ShortbreadSchema;
import fyi.osm.sourdough.common.ZOrder;
import java.util.Map;

/**
 * Shortbread `street_polygons` and its label layer `streets_polygons_labels`.
 *
 * The label layer's name is plural where the polygon layer is singular. That is a wart
 * in the specification, preserved here because minor versions may not rename layers.
 *
 * The `rail` property is documented as always false, so it is never emitted.
 */
public class StreetPolygons extends ShortbreadLayer {

  public StreetPolygons(ShortbreadConfiguration config) {
    super(config);
  }

  @Override
  public String name() {
    return ShortbreadSchema.STREET_POLYGONS;
  }

  /** Polygon street kinds and the zoom each appears at. */
  private static final Map<String, Integer> HIGHWAY_KINDS = Map.of(
    "pedestrian", 14,
    "service", 14
  );

  private static final Map<String, Integer> AEROWAY_KINDS = Map.of(
    "runway", 11,
    "taxiway", 13
  );

  /** The label points all appear at zoom 14, including runways and taxiways. */
  private static final int LABEL_MIN_ZOOM = 14;

  @Override
  public Expression filter() {
    return Expression.or(
      Expression.matchAny("highway", "pedestrian", "service"),
      Expression.matchAny("area:aeroway", "runway", "taxiway")
    );
  }

  /** The polygon kind, or null when the feature is not a street polygon. */
  static String kind(WithTags sf) {
    var highway = sf.getString("highway");
    if (highway != null && HIGHWAY_KINDS.containsKey(highway)) return highway;
    var aeroway = sf.getString("area:aeroway");
    if (aeroway != null && AEROWAY_KINDS.containsKey(aeroway)) return aeroway;
    return null;
  }

  private static int minZoom(String kind) {
    return HIGHWAY_KINDS.containsKey(kind) ? HIGHWAY_KINDS.get(kind) : AEROWAY_KINDS.get(kind);
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    if (!sf.canBePolygon()) return;
    var kind = kind(sf);
    if (kind == null) return;

    var polygon = fc.polygon(name());
    polygon.setMinZoom(minZoom(kind));
    polygon.setMinPixelSize(0.5);
    polygon.setBufferPixels(8);
    polygon.setSortKey(ZOrder.sortKey(sf, kind));
    polygon.setAttr("kind", kind);

    if (Booleans.tunnel(sf)) polygon.setAttr("tunnel", true);
    if (Booleans.bridge(sf)) polygon.setAttr("bridge", true);
    var service = sf.getString("service");
    if (service != null) polygon.setAttr("service", service);
    var surface = sf.getString("surface");
    if (surface != null) polygon.setAttr("surface", surface);

    if (sf.getString("name") != null) {
      var label = fc.pointOnSurface(ShortbreadSchema.STREETS_POLYGONS_LABELS);
      label.setMinZoom(LABEL_MIN_ZOOM);
      label.setBufferPixels(16);
      label.setAttr("kind", kind);
      ShortbreadNames.setNames(sf, label, config.languages());
    }
  }
}
