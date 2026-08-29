package fyi.osm.sourdough.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.util.Parse;
import com.onthegomap.planetiler.util.SortKey;
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadLayer;
import fyi.osm.sourdough.shortbread.ShortbreadNames;
import fyi.osm.sourdough.shortbread.ShortbreadSchema;
import fyi.osm.sourdough.shortbread.WayArea;

/**
 * Shortbread `boundary_labels`: label points for country and state areas.
 *
 * Note that `way_area` here is in hectares, while the water layers specify square
 * meters. The units genuinely differ between layers and must not be unified.
 *
 * Large countries appear several zooms before small ones, following the area thresholds
 * in the specification.
 */
public class BoundaryLabels extends ShortbreadLayer {

  public BoundaryLabels(ShortbreadConfiguration config) {
    super(config);
  }

  @Override
  public String name() {
    return ShortbreadSchema.BOUNDARY_LABELS;
  }

  private static final double SQUARE_KM = 1e6;

  @Override
  public Expression filter() {
    return Expression.and(
      Expression.matchAny("boundary", "administrative"),
      Expression.matchField("name")
    );
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    if (!sf.canBePolygon()) return;
    if (!sf.hasTag("boundary", "administrative")) return;
    if (sf.getString("name") == null) return;

    Integer adminLevel = Parse.parseIntOrNull(sf.getString("admin_level"));
    if (adminLevel == null || (adminLevel != 2 && adminLevel != 4)) return;

    var squareMeters = WayArea.squareMeters(sf);
    if (squareMeters == null) return;

    var label = fc.pointOnSurface(name());
    label.setMinZoom(minZoom(adminLevel, squareMeters));
    label.setBufferPixels(64);
    label.setAttr("admin_level", adminLevel);
    label.setAttr("way_area", squareMeters / 10_000.0);
    // Largest first.
    label.setSortKey(
      SortKey.orderByLog(squareMeters, WayArea.MAX_PLAUSIBLE_AREA, 1, 1000).get()
    );
    ShortbreadNames.setNames(sf, label, config.languages());
  }

  /** The area thresholds from the specification's Features table. */
  static int minZoom(int adminLevel, double squareMeters) {
    if (adminLevel == 2) {
      if (squareMeters >= 2e6 * SQUARE_KM) return 2;
      if (squareMeters >= 7e5 * SQUARE_KM) return 3;
      if (squareMeters >= 1e5 * SQUARE_KM) return 4;
      return 5;
    }
    if (squareMeters >= 7e5 * SQUARE_KM) return 3;
    if (squareMeters >= 1e5 * SQUARE_KM) return 4;
    return 5;
  }
}
