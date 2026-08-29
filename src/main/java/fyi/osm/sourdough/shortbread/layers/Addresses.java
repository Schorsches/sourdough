package fyi.osm.sourdough.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadLayer;
import fyi.osm.sourdough.shortbread.ShortbreadSchema;
import fyi.osm.sourdough.common.mapping.PoiKinds;

/**
 * Shortbread `addresses`: anything carrying an address, at zoom 14. Areas are
 * represented by a point.
 *
 * Features that also appear in the `pois` layer are deliberately left out, because that
 * layer already carries `housename` and `housenumber`. The check is a pure function of
 * the feature's own tags, so no cross-layer state is needed to deduplicate.
 */
public class Addresses extends ShortbreadLayer {

  public Addresses(ShortbreadConfiguration config) {
    super(config);
  }

  private static final int MIN_ZOOM = 14;

  @Override
  public String name() {
    return ShortbreadSchema.ADDRESSES;
  }

  @Override
  public Expression filter() {
    return Expression.or(
      Expression.matchField("addr:housenumber"),
      Expression.matchField("addr:housename")
    );
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    var housename = sf.getString("addr:housename");
    var housenumber = sf.getString("addr:housenumber");
    if (housename == null && housenumber == null) return;

    // Already represented, with the same two attributes, in the pois layer.
    if (PoiKinds.isPoi(sf)) return;

    FeatureCollector.Feature point;
    if (sf.isPoint()) {
      point = fc.point(name());
    } else if (sf.canBePolygon()) {
      point = fc.pointOnSurface(name());
    } else {
      return;
    }

    point.setMinZoom(MIN_ZOOM);
    point.setBufferPixels(8);
    if (housename != null) point.setAttr("housename", housename);
    if (housenumber != null) point.setAttr("housenumber", housenumber);
  }
}
