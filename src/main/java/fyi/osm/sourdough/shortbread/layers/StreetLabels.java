package fyi.osm.sourdough.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import fyi.osm.sourdough.common.Booleans;
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadLayer;
import fyi.osm.sourdough.shortbread.ShortbreadNames;
import fyi.osm.sourdough.common.RouteRef;
import fyi.osm.sourdough.shortbread.ShortbreadSchema;
import fyi.osm.sourdough.shortbread.mapping.StreetKinds;
import java.util.List;

/**
 * Shortbread `street_labels`: line geometries carrying street names and refs.
 *
 * Unlike `streets`, this layer spells link roads out as their own kinds
 * (`motorway_link` rather than `motorway` plus a flag).
 *
 * Route refs are shipped pre-laid-out: semicolons become newlines, and `ref_rows` and
 * `ref_cols` describe the resulting block so that a renderer can size a shield without
 * measuring the text itself.
 */
public class StreetLabels extends ShortbreadLayer implements ForwardingProfile.LayerPostProcessor {

  public StreetLabels(ShortbreadConfiguration config) {
    super(config);
  }

  @Override
  public String name() {
    return ShortbreadSchema.STREET_LABELS;
  }

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.or(
        Expression.matchAny("highway", StreetKinds.highwayValues()),
        Expression.matchAny("aeroway", StreetKinds.aerowayValues()),
        Expression.matchAny("railway", StreetKinds.railwayValues())
      ),
      Expression.or(Expression.matchField("name"), Expression.matchField("ref"))
    );
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    if (!sf.canBeLine()) return;
    var street = StreetKinds.lookup(sf);
    if (street == null) return;

    var name = sf.getString("name");
    var ref = sf.getString("ref");
    if (name == null && ref == null) return;

    var line = fc.line(name());
    line.setMinZoom(street.labelMinZoom());
    line.setMinPixelSize(0);
    line.setBufferPixels(8);
    line.setAttr("kind", street.labelKind());

    if (ref != null) {
      var laidOut = RouteRef.layout(ref);
      line.setAttr("ref", laidOut);
      line.setAttr("ref_rows", RouteRef.rows(laidOut));
      line.setAttr("ref_cols", RouteRef.columns(laidOut));
    }
    ShortbreadNames.setNames(sf, line, config.languages());

    if (Booleans.tunnel(sf)) {
      line.setAttr("tunnel", true);
    }
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items)
    throws GeometryException {
    // Joining segments of the same street gives a renderer a longer run to place a label
    // along, which is the whole point of this layer.
    return FeatureMerge.mergeLineStrings(items, 0, 0.25, 4);
  }
}
