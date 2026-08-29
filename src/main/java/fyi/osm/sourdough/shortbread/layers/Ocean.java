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
import java.util.List;

/**
 * Shortbread `ocean`: the sea, from preprocessed OSM coastline polygons.
 *
 * The layer has no attributes at all, so nothing but geometry is emitted. The source is
 * the same water-polygons shapefile Sourdough already downloads, registered once per
 * run, so switching schemas never fetches or processes it twice.
 */
public class Ocean extends ShortbreadLayer implements ForwardingProfile.LayerPostProcessor {

  public Ocean(ShortbreadConfiguration config) {
    super(config);
  }

  @Override
  public String name() {
    return ShortbreadSchema.OCEAN;
  }

  @Override
  public Expression filter() {
    // Ocean comes only from the shapefile source, handled by processPreparedOcean.
    return Expression.FALSE;
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    // Nothing from the OSM source belongs in this layer.
  }

  /** Registered against the water-polygons shapefile source. */
  public void processPreparedOcean(SourceFeature sf, FeatureCollector fc) {
    fc.polygon(name()).setZoomRange(0, ShortbreadSchema.MAXZOOM).setBufferPixels(8);
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items)
    throws GeometryException {
    return FeatureMerge.mergeOverlappingPolygons(items, 1);
  }
}
