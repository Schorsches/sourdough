package fyi.osm.sourdough.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadLayer;
import fyi.osm.sourdough.shortbread.ShortbreadSchema;

/**
 * Shortbread `dam_lines` and `dam_polygons`.
 *
 * Dams only; dykes are deliberately not included by the schema.
 */
public class Dams extends ShortbreadLayer {

  public Dams(ShortbreadConfiguration config) {
    super(config);
  }

  private static final int MIN_ZOOM = 12;
  private static final String KIND = "dam";

  @Override
  public String name() {
    return ShortbreadSchema.DAM_LINES;
  }

  @Override
  public Expression filter() {
    return Expression.matchAny("waterway", "dam");
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    if (!sf.hasTag("waterway", "dam")) return;

    if (sf.canBePolygon()) {
      fc.polygon(ShortbreadSchema.DAM_POLYGONS)
        .setMinZoom(MIN_ZOOM)
        .setMinPixelSize(0.5)
        .setAttr("kind", KIND);
    } else if (sf.canBeLine()) {
      fc.line(ShortbreadSchema.DAM_LINES)
        .setMinZoom(MIN_ZOOM)
        .setMinPixelSize(0)
        .setAttr("kind", KIND);
    }
  }
}
