package fyi.osm.sourdough.smartmaps.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import fyi.osm.sourdough.common.mapping.PoiKinds;
import fyi.osm.sourdough.smartmaps.SmartMapsConfiguration;
import fyi.osm.sourdough.smartmaps.SmartMapsLayer;
import fyi.osm.sourdough.smartmaps.SmartMapsSchema;

/**
 * SmartMaps `housenumber_label`: addresses, as points.
 *
 * A feature that is also a POI is left out, because the `poi` layer already carries
 * housename and housenumber. The check is a pure function of the feature's own tags, so no
 * cross-layer state is needed.
 */
public class HousenumberLabel extends SmartMapsLayer {

  public HousenumberLabel(SmartMapsConfiguration config) {
    super(config);
  }

  private static final int MIN_ZOOM = 14;

  @Override
  public String name() {
    return SmartMapsSchema.HOUSENUMBER_LABEL;
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
