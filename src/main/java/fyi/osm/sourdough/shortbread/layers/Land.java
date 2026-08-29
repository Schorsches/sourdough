package fyi.osm.sourdough.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadLayer;
import fyi.osm.sourdough.shortbread.ShortbreadSchema;
import fyi.osm.sourdough.common.mapping.LandKinds;
import java.util.List;

/** Shortbread `land`: basic land cover, usually drawn first. */
public class Land extends ShortbreadLayer implements ForwardingProfile.LayerPostProcessor {

  public Land(ShortbreadConfiguration config) {
    super(config);
  }

  @Override
  public String name() {
    return ShortbreadSchema.LAND;
  }

  @Override
  public Expression filter() {
    return Expression.or(
      LandKinds.KEYS.stream()
        .map(key -> Expression.matchAny(key, LandKinds.valuesFor(key)))
        .toArray(Expression[]::new)
    );
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    if (!sf.canBePolygon()) return;
    var land = LandKinds.lookup(sf);
    if (land == null) return;

    fc.polygon(name())
      .setMinZoom(land.minZoom())
      .setMinPixelSize(2.0)
      .setPixelTolerance(0.25)
      .setBufferPixels(8)
      .setAttr("kind", land.kind());
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items)
    throws GeometryException {
    return FeatureMerge.mergeNearbyPolygons(items, 3.0, 3.0, 0.5, 0.5);
  }
}
