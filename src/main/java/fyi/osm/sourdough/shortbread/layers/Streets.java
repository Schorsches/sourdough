package fyi.osm.sourdough.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import fyi.osm.sourdough.common.Access;
import fyi.osm.sourdough.common.Booleans;
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadLayer;
import fyi.osm.sourdough.shortbread.ShortbreadSchema;
import fyi.osm.sourdough.common.ZOrder;
import fyi.osm.sourdough.shortbread.mapping.StreetKinds;
import java.util.List;

/**
 * Shortbread `streets`: the whole road, rail and aeroway network as lines.
 *
 * Attributes appear at different zooms, so that low-zoom tiles carry only what a
 * small-scale map needs. Features are ordered by z-order (see {@link ZOrder}).
 *
 * Access attributes are evaluated for highways only; railways and aeroways never carry
 * them, which the specification states explicitly.
 */
public class Streets extends ShortbreadLayer implements ForwardingProfile.LayerPostProcessor {

  public Streets(ShortbreadConfiguration config) {
    super(config);
  }

  // Zooms at which each group of attributes starts being carried.
  private static final int DETAIL_MIN_ZOOM = 11;
  private static final int ACCESS_MIN_ZOOM = 13;
  private static final int ONEWAY_MIN_ZOOM = 14;

  @Override
  public String name() {
    return ShortbreadSchema.STREETS;
  }

  @Override
  public Expression filter() {
    return Expression.or(
      Expression.matchAny("highway", StreetKinds.highwayValues()),
      Expression.matchAny("aeroway", StreetKinds.aerowayValues()),
      Expression.matchAny("railway", StreetKinds.railwayValues())
    );
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    if (!sf.canBeLine()) return;
    var street = StreetKinds.lookup(sf);
    if (street == null) return;

    var line = fc.line(name());
    line.setMinZoom(StreetKinds.minZoom(sf, street));
    line.setMinPixelSize(0);
    line.setBufferPixels(4);
    line.setSortKey(ZOrder.sortKey(sf, street.kind()));

    line.setAttr("kind", street.kind());
    if (street.rail()) {
      line.setAttr("rail", true);
    }
    if (street.link()) {
      line.setAttrWithMinzoom("link", true, DETAIL_MIN_ZOOM);
    }
    if (Booleans.tunnel(sf)) {
      line.setAttrWithMinzoom("tunnel", true, DETAIL_MIN_ZOOM);
    }
    if (Booleans.bridge(sf)) {
      line.setAttrWithMinzoom("bridge", true, DETAIL_MIN_ZOOM);
    }

    setStringIfPresent(sf, line, "tracktype", DETAIL_MIN_ZOOM);
    setStringIfPresent(sf, line, "surface", DETAIL_MIN_ZOOM);
    setStringIfPresent(sf, line, "service", DETAIL_MIN_ZOOM);

    // Oneway is meaningless on rail in this schema and is always false there.
    if (!street.rail()) {
      if (Booleans.oneway(sf)) {
        line.setAttrWithMinzoom("oneway", true, ONEWAY_MIN_ZOOM);
      }
      if (Booleans.onewayReverse(sf)) {
        line.setAttrWithMinzoom("oneway_reverse", true, ONEWAY_MIN_ZOOM);
      }
    }

    if (sf.hasTag("highway")) {
      setAccess(sf, line, "motorcar", Access.MOTORCAR);
      setAccess(sf, line, "bicycle", Access.BICYCLE);
      setAccess(sf, line, "foot", Access.FOOT);
      setAccess(sf, line, "horse", Access.HORSE);
    }
  }

  private static void setAccess(
    SourceFeature sf,
    FeatureCollector.Feature line,
    String attribute,
    List<String> chain
  ) {
    var value = Access.evaluate(sf, chain);
    if (value != null) {
      line.setAttrWithMinzoom(attribute, value, ACCESS_MIN_ZOOM);
    }
  }

  private static void setStringIfPresent(
    SourceFeature sf,
    FeatureCollector.Feature line,
    String key,
    int minZoom
  ) {
    var value = sf.getString(key);
    if (value != null) {
      line.setAttrWithMinzoom(key, value, minZoom);
    }
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items)
    throws GeometryException {
    // Never drop short segments: physical detail tags break roads into pieces, and
    // dropping any of them leaves visible gaps in the network.
    return FeatureMerge.mergeLineStrings(items, 0, 0.25, 4);
  }
}
