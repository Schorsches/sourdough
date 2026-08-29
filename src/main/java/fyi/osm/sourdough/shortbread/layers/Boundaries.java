package fyi.osm.sourdough.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmRelationInfo;
import com.onthegomap.planetiler.util.Parse;
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadLayer;
import fyi.osm.sourdough.shortbread.ShortbreadSchema;
import java.util.List;
import java.util.Set;

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

  /** Shortbread only carries national and state boundaries. */
  private static final Set<Integer> ADMIN_LEVELS = Set.of(2, 4);

  private static final int COUNTRY_MIN_ZOOM = 0;
  private static final int STATE_MIN_ZOOM = 7;

  @Override
  public String name() {
    return ShortbreadSchema.BOUNDARIES;
  }

  /**
   * A parent boundary relation. `adminLevel` is null for a disputed relation that
   * carries no admin_level, which the schema treats as applying to any member way.
   */
  record BoundaryRelation(long id, Integer adminLevel, boolean disputed) implements OsmRelationInfo {}

  @Override
  public List<OsmRelationInfo> preprocessOsmRelation(OsmElement.Relation relation) {
    if (!relation.hasTag("type", "boundary")) return null;

    Integer adminLevel = Parse.parseIntOrNull(relation.getString("admin_level"));
    boolean administrative = relation.hasTag("boundary", "administrative");
    boolean disputed = relation.hasTag("boundary", "disputed");

    if (administrative && adminLevel != null && ADMIN_LEVELS.contains(adminLevel)) {
      return List.of(new BoundaryRelation(relation.id(), adminLevel, false));
    }
    // A disputed relation counts either with no admin_level, or at level 2 or 4.
    if (disputed && (adminLevel == null || ADMIN_LEVELS.contains(adminLevel))) {
      return List.of(new BoundaryRelation(relation.id(), adminLevel, true));
    }
    return null;
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    if (!sf.canBeLine()) return;

    var parents = sf.relationInfo(BoundaryRelation.class);
    if (parents.isEmpty()) return;

    Integer adminLevel = null;
    boolean disputed = sf.hasTag("disputed", "yes");
    for (var member : parents) {
      var relation = member.relation();
      if (relation.disputed()) {
        disputed = true;
      } else if (relation.adminLevel() != null) {
        // "the lowest numerical admin_level value of the parent relations"
        adminLevel = adminLevel == null
          ? relation.adminLevel()
          : Math.min(adminLevel, relation.adminLevel());
      }
    }

    // A way that is only a member of disputed relations has no administrative level and
    // is therefore not a boundary line in this schema.
    if (adminLevel == null) return;

    var line = fc.line(name());
    line.setMinZoom(adminLevel == 2 ? COUNTRY_MIN_ZOOM : STATE_MIN_ZOOM);
    line.setMinPixelSize(0);
    line.setBufferPixels(4);
    line.setAttr("admin_level", adminLevel);
    if (sf.hasTag("maritime", "yes") || sf.hasTag("natural", "coastline")) {
      line.setAttr("maritime", true);
    }
    if (disputed) {
      line.setAttr("disputed", true);
    }
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items)
    throws GeometryException {
    return FeatureMerge.mergeLineStrings(items, 0, 0.25, 4, true);
  }
}
