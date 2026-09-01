package fyi.osm.sourdough.smartmaps.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmRelationInfo;
import fyi.osm.sourdough.common.BoundaryRelations;
import fyi.osm.sourdough.smartmaps.SmartMapsConfiguration;
import fyi.osm.sourdough.smartmaps.SmartMapsLayer;
import fyi.osm.sourdough.smartmaps.SmartMapsSchema;
import java.util.List;

/**
 * SmartMaps `boundary`: country and state boundary lines.
 *
 * The three attributes match Shortbread's exactly, so the shared relation rules apply
 * unchanged: lowest admin_level across parents, and disputed from either the way or a
 * qualifying parent relation.
 */
public class Boundary
  extends SmartMapsLayer
  implements ForwardingProfile.OsmRelationPreprocessor, ForwardingProfile.LayerPostProcessor {

  public Boundary(SmartMapsConfiguration config) {
    super(config);
  }

  private static final int COUNTRY_MIN_ZOOM = 0;
  private static final int STATE_MIN_ZOOM = 7;

  @Override
  public String name() {
    return SmartMapsSchema.BOUNDARY;
  }

  @Override
  public List<OsmRelationInfo> preprocessOsmRelation(OsmElement.Relation relation) {
    return BoundaryRelations.preprocess(relation);
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    if (!sf.canBeLine()) return;
    var inherited = BoundaryRelations.resolve(sf);
    if (inherited == null) return;

    var line = fc.line(name());
    line.setMinZoom(
      inherited.adminLevel() == BoundaryRelations.COUNTRY_ADMIN_LEVEL
        ? COUNTRY_MIN_ZOOM
        : STATE_MIN_ZOOM
    );
    line.setMinPixelSize(0);
    line.setBufferPixels(4);
    line.setAttr("admin_level", inherited.adminLevel());
    line.setAttr("maritime", inherited.maritime());
    line.setAttr("disputed", inherited.disputed());
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items)
    throws GeometryException {
    return FeatureMerge.mergeLineStrings(items, 0, 0.25, 4, true);
  }
}
