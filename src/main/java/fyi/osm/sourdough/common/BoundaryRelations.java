package fyi.osm.sourdough.common;

import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmRelationInfo;
import com.onthegomap.planetiler.util.Parse;
import java.util.List;
import java.util.Set;

/**
 * Reading administrative boundaries out of OpenStreetMap relations.
 *
 * A boundary way carries no admin_level of its own; it inherits one from the relations it
 * belongs to. A way that is both a national and a state border takes the lower value, so
 * it reads as national. "Disputed" has three independent sources: the way's own
 * disputed=yes, a parent disputed relation with no admin_level, or one at level 2 or 4.
 *
 * Shared because both schemas carry the same three boundary attributes and so need the
 * same answer. Duplicating the rules would mean fixing them twice.
 */
public final class BoundaryRelations {

  private BoundaryRelations() {}

  /** Only national and state boundaries are carried. */
  private static final Set<Integer> ADMIN_LEVELS = Set.of(2, 4);

  public static final int COUNTRY_ADMIN_LEVEL = 2;

  /**
   * A parent boundary relation. {@code adminLevel} is null for a disputed relation that
   * carries none, which applies to any member way.
   */
  public record BoundaryRelation(long id, Integer adminLevel, boolean disputed)
    implements OsmRelationInfo {}

  /** The relation preprocessor body, called from each schema's boundary layer. */
  public static List<OsmRelationInfo> preprocess(OsmElement.Relation relation) {
    if (!relation.hasTag("type", "boundary")) return null;

    Integer adminLevel = Parse.parseIntOrNull(relation.getString("admin_level"));
    boolean administrative = relation.hasTag("boundary", "administrative");
    boolean disputed = relation.hasTag("boundary", "disputed");

    if (administrative && adminLevel != null && ADMIN_LEVELS.contains(adminLevel)) {
      return List.of(new BoundaryRelation(relation.id(), adminLevel, false));
    }
    if (disputed && (adminLevel == null || ADMIN_LEVELS.contains(adminLevel))) {
      return List.of(new BoundaryRelation(relation.id(), adminLevel, true));
    }
    return null;
  }

  /** What a member way inherits from its parents, or null if it is not a boundary line. */
  public record Inherited(int adminLevel, boolean disputed, boolean maritime) {}

  /**
   * Resolves a way against its parent relations. Returns null when the way belongs only to
   * disputed relations and so has no administrative level of its own.
   */
  public static Inherited resolve(SourceFeature sf) {
    var parents = sf.relationInfo(BoundaryRelation.class);
    if (parents.isEmpty()) return null;

    Integer adminLevel = null;
    boolean disputed = sf.hasTag("disputed", "yes");
    for (var member : parents) {
      var relation = member.relation();
      if (relation.disputed()) {
        disputed = true;
      } else if (relation.adminLevel() != null) {
        adminLevel = adminLevel == null
          ? relation.adminLevel()
          : Math.min(adminLevel, relation.adminLevel());
      }
    }
    if (adminLevel == null) return null;

    boolean maritime = sf.hasTag("maritime", "yes") || sf.hasTag("natural", "coastline");
    return new Inherited(adminLevel, disputed, maritime);
  }
}
