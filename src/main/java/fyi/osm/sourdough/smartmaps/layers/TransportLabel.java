package fyi.osm.sourdough.smartmaps.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import fyi.osm.sourdough.common.Booleans;
import fyi.osm.sourdough.common.RouteRef;
import fyi.osm.sourdough.common.mapping.StreetKinds;
import fyi.osm.sourdough.smartmaps.SmartMapsConfiguration;
import fyi.osm.sourdough.smartmaps.SmartMapsLayer;
import fyi.osm.sourdough.smartmaps.SmartMapsNames;
import fyi.osm.sourdough.smartmaps.SmartMapsSchema;
import java.util.List;

/**
 * SmartMaps `transport_label`: street names, route shields and motorway exits.
 *
 * Merges Shortbread's `street_labels` (lines) and `street_labels_points` (motorway
 * junctions), so this layer carries both geometries.
 *
 * As in Shortbread, link roads get their own `kind` here (`motorway_link`) rather than the
 * parent kind plus a flag, and refs are shipped pre-laid-out with the row and column
 * counts a renderer needs to size a shield without measuring text.
 *
 * `ref_prefix` and `ref_org` are <strong>inferred</strong>: the source document gives
 * their names and types and nothing more. `ref_prefix` is read as the leading alphabetic
 * part of the first ref -- the "A" of "A1", which is what selects a shield design -- and
 * `ref_org` as the route's `network`. Both are one method each to change if real
 * SmartMaps tiles turn out to mean something else by them.
 */
public class TransportLabel
  extends SmartMapsLayer
  implements ForwardingProfile.LayerPostProcessor {

  public TransportLabel(SmartMapsConfiguration config) {
    super(config);
  }

  private static final int JUNCTION_MIN_ZOOM = 12;
  private static final String JUNCTION_KIND = "motorway_junction";

  @Override
  public String name() {
    return SmartMapsSchema.TRANSPORT_LABEL;
  }

  @Override
  public Expression filter() {
    return Expression.or(
      Expression.matchAny("highway", JUNCTION_KIND),
      Expression.and(
        Expression.or(
          Expression.matchAny("highway", StreetKinds.highwayValues()),
          Expression.matchAny("aeroway", StreetKinds.aerowayValues()),
          Expression.matchAny("railway", StreetKinds.railwayValues())
        ),
        Expression.or(Expression.matchField("name"), Expression.matchField("ref"))
      )
    );
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    if (sf.isPoint() && sf.hasTag("highway", JUNCTION_KIND)) {
      processJunction(sf, fc);
      return;
    }
    if (sf.canBeLine()) {
      processStreet(sf, fc);
    }
  }

  private void processStreet(SourceFeature sf, FeatureCollector fc) {
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

    setRef(sf, line, ref);
    SmartMapsNames.setNames(sf, line, config.languages());

    line.setAttr("tunnel", Booleans.tunnel(sf));
  }

  private void processJunction(SourceFeature sf, FeatureCollector fc) {
    var point = fc.point(name());
    point.setMinZoom(JUNCTION_MIN_ZOOM);
    point.setBufferPixels(16);
    point.setAttr("kind", JUNCTION_KIND);
    // An exit ref is a single number, not a route list, so it is not laid out -- but
    // ref_prefix and ref_org still apply where they are tagged.
    var ref = sf.getString("ref");
    if (ref != null) {
      point.setAttr("ref", ref);
      setRefPrefix(point, ref);
    }
    setNetwork(sf, point);
    // A motorway junction can sit inside a tunnel, and the layer emits its one boolean on
    // every feature rather than only where true.
    point.setAttr("tunnel", Booleans.tunnel(sf));
    SmartMapsNames.setNames(sf, point, config.languages());
  }

  private void setRef(SourceFeature sf, FeatureCollector.Feature line, String ref) {
    if (ref == null) return;
    var laidOut = RouteRef.layout(ref);
    line.setAttr("ref", laidOut);
    line.setAttr("ref_rows", RouteRef.rows(laidOut));
    line.setAttr("ref_cols", RouteRef.columns(laidOut));
    setRefPrefix(line, ref);
    setNetwork(sf, line);
  }

  private static void setRefPrefix(FeatureCollector.Feature feature, String ref) {
    var prefix = refPrefix(ref);
    if (prefix != null) {
      feature.setAttr("ref_prefix", prefix);
    }
  }

  private static void setNetwork(SourceFeature sf, FeatureCollector.Feature feature) {
    var network = sf.getString("network");
    if (network != null) {
      feature.setAttr("network", network);
      // Inferred: `ref_org` is read as the organisation whose route this is, which in OSM
      // is what `network` names.
      feature.setAttr("ref_org", network);
    }
  }

  /**
   * The leading alphabetic part of the first ref -- "A" from "A1;E15", "B" from "B 27".
   * Null when the ref does not start with letters, as a bare exit number does.
   */
  static String refPrefix(String ref) {
    if (ref == null) return null;
    var first = ref.split(";", 2)[0].trim();
    int end = 0;
    while (end < first.length() && Character.isLetter(first.charAt(end))) {
      end++;
    }
    return end == 0 ? null : first.substring(0, end);
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items)
    throws GeometryException {
    // Joining segments of the same street gives a renderer a longer run to place a label
    // along, which is the whole point of this layer.
    return FeatureMerge.mergeLineStrings(items, 0, 0.25, 4);
  }
}
