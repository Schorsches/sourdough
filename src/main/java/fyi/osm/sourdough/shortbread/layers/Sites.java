package fyi.osm.sourdough.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadLayer;
import fyi.osm.sourdough.shortbread.ShortbreadSchema;
import fyi.osm.sourdough.shortbread.mapping.SiteKinds;

/** Shortbread `sites`: land use drawn above `land` but below `buildings`. */
public class Sites extends ShortbreadLayer {

  public Sites(ShortbreadConfiguration config) {
    super(config);
  }

  @Override
  public String name() {
    return ShortbreadSchema.SITES;
  }

  @Override
  public Expression filter() {
    return Expression.or(
      SiteKinds.KEYS.stream()
        .map(key -> Expression.matchAny(key, SiteKinds.valuesFor(key)))
        .toArray(Expression[]::new)
    );
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    if (!sf.canBePolygon()) return;
    var kind = SiteKinds.lookup(sf);
    if (kind == null) return;

    fc.polygon(name())
      .setMinZoom(SiteKinds.MIN_ZOOM)
      .setMinPixelSize(1.0)
      .setBufferPixels(8)
      .setAttr("kind", kind);
  }
}
