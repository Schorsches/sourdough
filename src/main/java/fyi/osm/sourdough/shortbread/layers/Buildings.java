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
import fyi.osm.sourdough.shortbread.buildings3d.Buildings3d;
import fyi.osm.sourdough.shortbread.buildings3d.BuildingDimensionParser;
import java.util.List;

/**
 * Shortbread `buildings`: every polygon with a building tag other than building=no,
 * from zoom 14.
 *
 * Base Shortbread gives this layer exactly one property, `dummy`, which is always 1.
 * That is odd but it is what the specification says, and existing styles may rely on it,
 * so it is emitted as-is.
 *
 * Under the shortbread-1.1-3d schema the layer additionally carries the normalized 3D
 * attributes. Those are additive: a style written for base Shortbread renders identical
 * output either way.
 */
public class Buildings extends ShortbreadLayer implements ForwardingProfile.LayerPostProcessor {

  private final BuildingDimensionParser dimensions;

  public Buildings(ShortbreadConfiguration config, BuildingDimensionParser dimensions) {
    super(config);
    this.dimensions = dimensions;
  }

  private static final int MIN_ZOOM = 14;

  @Override
  public String name() {
    return ShortbreadSchema.BUILDINGS;
  }

  @Override
  public Expression filter() {
    return Expression.matchField("building");
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    if (!sf.canBePolygon()) return;
    var building = sf.getString("building");
    if (building == null || building.equals("no")) return;

    var polygon = fc.polygon(name());
    polygon.setMinZoom(MIN_ZOOM);
    polygon.setMinPixelSize(0.5);
    polygon.setBufferPixels(8);
    polygon.setAttr("dummy", 1);

    if (config.hasBuildings3d()) {
      Buildings3d.setAttributes(sf, polygon, dimensions.parse(sf));
    }
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items)
    throws GeometryException {
    return FeatureMerge.mergeMultiPolygon(items);
  }
}
