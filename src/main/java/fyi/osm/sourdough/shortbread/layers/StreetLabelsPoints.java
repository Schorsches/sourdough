package fyi.osm.sourdough.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadLayer;
import fyi.osm.sourdough.shortbread.ShortbreadNames;
import fyi.osm.sourdough.shortbread.ShortbreadSchema;

/** Shortbread `street_labels_points`: motorway exits, and nothing else. */
public class StreetLabelsPoints extends ShortbreadLayer {

  public StreetLabelsPoints(ShortbreadConfiguration config) {
    super(config);
  }

  private static final int MIN_ZOOM = 12;
  private static final String KIND = "motorway_junction";

  @Override
  public String name() {
    return ShortbreadSchema.STREET_LABELS_POINTS;
  }

  @Override
  public Expression filter() {
    return Expression.matchAny("highway", KIND);
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    if (!sf.isPoint()) return;
    if (!sf.hasTag("highway", KIND)) return;

    var point = fc.point(name());
    point.setMinZoom(MIN_ZOOM);
    point.setBufferPixels(16);
    point.setAttr("kind", KIND);
    var ref = sf.getString("ref");
    if (ref != null) {
      point.setAttr("ref", ref);
    }
    ShortbreadNames.setNames(sf, point, config.languages());
  }
}
