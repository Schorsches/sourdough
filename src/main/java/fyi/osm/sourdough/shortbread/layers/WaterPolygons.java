package fyi.osm.sourdough.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.WithTags;
import com.onthegomap.planetiler.util.SortKey;
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadLayer;
import fyi.osm.sourdough.shortbread.ShortbreadNames;
import fyi.osm.sourdough.shortbread.ShortbreadSchema;
import fyi.osm.sourdough.common.WayArea;

/**
 * Shortbread `water_polygons` and `water_polygons_labels`: inland water and glaciers.
 *
 * The two layers are produced together because the label layer is defined as "all named
 * water polygons", so it shares this layer's classification and area exactly. Label
 * points are sorted by area, largest first, as the schema requires.
 */
public class WaterPolygons extends ShortbreadLayer {

  public WaterPolygons(ShortbreadConfiguration config) {
    super(config);
  }

  @Override
  public String name() {
    return ShortbreadSchema.WATER_POLYGONS;
  }

  /** Docks and canals appear later than the other water bodies. */
  private static final int DEFAULT_MIN_ZOOM = 4;
  private static final int WATERWAY_AREA_MIN_ZOOM = 10;
  private static final int LABEL_MIN_ZOOM = 4;

  @Override
  public Expression filter() {
    return Expression.or(
      Expression.matchAny("natural", "glacier", "water"),
      Expression.matchAny("waterway", "riverbank", "dock", "canal"),
      Expression.matchAny("landuse", "reservoir", "basin")
    );
  }

  /** The `kind` value for a feature, or null if it is not a water polygon. */
  static String kind(WithTags sf) {
    if (sf.hasTag("natural", "glacier")) return "glacier";
    if (sf.hasTag("waterway", "riverbank")) return "river";
    if (sf.hasTag("natural", "water")) {
      return sf.hasTag("water", "river") ? "river" : "water";
    }
    if (sf.hasTag("landuse", "reservoir")) return "reservoir";
    if (sf.hasTag("landuse", "basin")) return "basin";
    if (sf.hasTag("waterway", "dock")) return "dock";
    if (sf.hasTag("waterway", "canal")) return "canal";
    return null;
  }

  private static int minZoom(String kind) {
    return switch (kind) {
      case "dock", "canal" -> WATERWAY_AREA_MIN_ZOOM;
      default -> DEFAULT_MIN_ZOOM;
    };
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    if (!sf.canBePolygon()) return;
    var kind = kind(sf);
    if (kind == null) return;

    var wayArea = WayArea.squareMeters(sf);

    var polygon = fc.polygon(name());
    polygon.setMinZoom(minZoom(kind));
    polygon.setMinPixelSize(1.0);
    polygon.setBufferPixels(8);
    polygon.setAttr("kind", kind);
    if (wayArea != null) {
      polygon.setAttr("way_area", wayArea);
    }

    var name = sf.getString("name");
    if (name != null) {
      var label = fc.pointOnSurface(ShortbreadSchema.WATER_POLYGONS_LABELS);
      label.setMinZoom(Math.max(LABEL_MIN_ZOOM, minZoom(kind)));
      label.setBufferPixels(32);
      label.setAttr("kind", kind);
      if (wayArea != null) {
        label.setAttr("way_area", wayArea);
        // Largest first.
        label.setSortKey(SortKey.orderByLog(wayArea, WayArea.MAX_PLAUSIBLE_AREA, 1, 1000).get());
      }
      ShortbreadNames.setNames(sf, label, config.languages());
    }
  }
}
