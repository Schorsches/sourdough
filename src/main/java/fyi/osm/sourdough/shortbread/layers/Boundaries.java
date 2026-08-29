package fyi.osm.sourdough.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmRelationInfo;
import fyi.osm.sourdough.common.BoundaryRelations;
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadLayer;
import fyi.osm.sourdough.shortbread.ShortbreadSchema;
import java.util.List;

/**
 * Shortbread `boundaries`: country and state boundary lines.
 *
 * This is the one layer whose attributes genuinely come from parent relations. A way
 * carries the *lowest* admin_level of the administrative relations it belongs to, so a
 * way that is both a national and a state border is tagged as a national one.
 *
 * The disputed rule has three independent sources: the way's own disputed=yes, a parent
 * disputed relation with no admin_level at all, or a parent disputed relation at
 * admin_level 2 or 4.
 */
public class Boundaries
  extends ShortbreadLayer
  implements ForwardingProfile.OsmRelationPreprocessor, ForwardingProfile.LayerPostProcessor {

  public Boundaries(ShortbreadConfiguration config) {
    super(config);
  }

  private static final int COUNTRY_MIN_ZOOM = 0;
  private static final int STATE_MIN_ZOOM = 7;

  @Override
  public String name() {
    return ShortbreadSchema.BOUNDARIES;
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
    int adminLevel = inherited.adminLevel();

    var line = fc.line(name());
    line.setMinZoom(adminLevel == 2 ? COUNTRY_MIN_ZOOM : STATE_MIN_ZOOM);
    line.setMinPixelSize(0);
    line.setBufferPixels(4);
    line.setAttr("admin_level", adminLevel);
    if (inherited.maritime()) {
      line.setAttr("maritime", true);
    }
    if (inherited.disputed()) {
      line.setAttr("disputed", true);
    }
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items)
    throws GeometryException {
    return FeatureMerge.mergeLineStrings(items, 0, 0.25, 4, true);
  }
}
