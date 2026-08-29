package fyi.osm.sourdough.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadLayer;
import fyi.osm.sourdough.shortbread.ShortbreadSchema;

/**
 * Shortbread `bridges`: bridge areas as polygons.
 *
 * The schema makes no distinction between what crosses the bridge, so there is nothing
 * to carry beyond the kind.
 */
public class Bridges extends ShortbreadLayer {

  public Bridges(ShortbreadConfiguration config) {
    super(config);
  }

  private static final int MIN_ZOOM = 12;

  @Override
  public String name() {
    return ShortbreadSchema.BRIDGES;
  }

  @Override
  public Expression filter() {
    return Expression.matchAny("man_made", "bridge");
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    if (!sf.canBePolygon()) return;
    if (!sf.hasTag("man_made", "bridge")) return;

    fc.polygon(name())
      .setMinZoom(MIN_ZOOM)
      .setMinPixelSize(0.5)
      .setBufferPixels(8)
      .setAttr("kind", "bridge");
  }
}
