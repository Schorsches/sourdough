package fyi.osm.sourdough.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import fyi.osm.sourdough.shortbread.Booleans;
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadLayer;
import fyi.osm.sourdough.shortbread.ShortbreadNames;
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

  /** The schema replaces semicolons in `ref` with this character. */
  static final char REF_SEPARATOR = '\n';

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
      var laidOut = layoutRef(ref);
      line.setAttr("ref", laidOut);
      line.setAttr("ref_rows", rows(laidOut));
      line.setAttr("ref_cols", columns(laidOut));
    }
    ShortbreadNames.setNames(sf, line, config.languages());

    if (Booleans.tunnel(sf)) {
      line.setAttr("tunnel", true);
    }
  }

  /** Semicolon-separated refs become one ref per line. */
  static String layoutRef(String ref) {
    return ref.replace(';', REF_SEPARATOR);
  }

  static int rows(String ref) {
    int rows = 1;
    for (int i = 0; i < ref.length(); i++) {
      if (ref.charAt(i) == REF_SEPARATOR) rows++;
    }
    return rows;
  }

  /** The longest line, which is what determines how wide a shield must be. */
  static int columns(String ref) {
    int longest = 0;
    int current = 0;
    for (int i = 0; i < ref.length(); i++) {
      if (ref.charAt(i) == REF_SEPARATOR) {
        longest = Math.max(longest, current);
        current = 0;
      } else {
        current++;
      }
    }
    return Math.max(longest, current);
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items)
    throws GeometryException {
    // Joining segments of the same street gives a renderer a longer run to place a label
    // along, which is the whole point of this layer.
    return FeatureMerge.mergeLineStrings(items, 0, 0.25, 4);
  }
}
