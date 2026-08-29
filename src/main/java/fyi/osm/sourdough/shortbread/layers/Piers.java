package fyi.osm.sourdough.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadLayer;
import fyi.osm.sourdough.shortbread.ShortbreadSchema;
import java.util.Set;

/** Shortbread `pier_lines` and `pier_polygons`: piers, breakwaters and groynes. */
public class Piers extends ShortbreadLayer {

  public Piers(ShortbreadConfiguration config) {
    super(config);
  }

  private static final int MIN_ZOOM = 12;
  private static final Set<String> KINDS = Set.of("pier", "breakwater", "groyne");

  @Override
  public String name() {
    return ShortbreadSchema.PIER_LINES;
  }

  @Override
  public Expression filter() {
    return Expression.matchAny("man_made", "pier", "breakwater", "groyne");
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    var kind = sf.getString("man_made");
    if (kind == null || !KINDS.contains(kind)) return;

    if (sf.canBePolygon()) {
      fc.polygon(ShortbreadSchema.PIER_POLYGONS)
        .setMinZoom(MIN_ZOOM)
        .setMinPixelSize(0.5)
        .setAttr("kind", kind);
    } else if (sf.canBeLine()) {
      fc.line(ShortbreadSchema.PIER_LINES)
        .setMinZoom(MIN_ZOOM)
        .setMinPixelSize(0)
        .setAttr("kind", kind);
    }
  }
}
