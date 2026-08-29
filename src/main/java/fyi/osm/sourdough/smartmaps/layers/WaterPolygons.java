package fyi.osm.sourdough.smartmaps.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.WithTags;
import com.onthegomap.planetiler.util.SortKey;
import fyi.osm.sourdough.common.WayArea;
import fyi.osm.sourdough.smartmaps.SmartMapsConfiguration;
import fyi.osm.sourdough.smartmaps.SmartMapsLayer;
import fyi.osm.sourdough.smartmaps.SmartMapsNames;
import fyi.osm.sourdough.smartmaps.SmartMapsSchema;
import java.util.List;

/**
 * SmartMaps `water_polygons`, and the point half of `water_label`.
 *
 * The layout has no ocean layer. Taken literally that would drop coastlines entirely and
 * leave the sea unpainted, so ocean polygons land here as `kind=ocean` -- the one place a
 * consumer would look for them. Recorded in SMARTMAPS_SCHEMA.md.
 */
public class WaterPolygons
  extends SmartMapsLayer
  implements ForwardingProfile.LayerPostProcessor {

  public WaterPolygons(SmartMapsConfiguration config) {
    super(config);
  }

  private static final int DEFAULT_MIN_ZOOM = 4;
  private static final int WATERWAY_AREA_MIN_ZOOM = 10;
  private static final int LABEL_MIN_ZOOM = 4;

  /** The kind given to polygons from the preprocessed coastline shapefile. */
  public static final String OCEAN_KIND = "ocean";

  @Override
  public String name() {
    return SmartMapsSchema.WATER_POLYGONS;
  }

  @Override
  public Expression filter() {
    return Expression.or(
      Expression.matchAny("natural", "glacier", "water"),
      Expression.matchAny("waterway", "riverbank", "dock", "canal"),
      Expression.matchAny("landuse", "reservoir", "basin")
    );
  }

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

  /** Registered against the preprocessed coastline shapefile. */
  public void processPreparedOcean(SourceFeature sf, FeatureCollector fc) {
    fc.polygon(name())
      .setZoomRange(0, SmartMapsSchema.MAXZOOM)
      .setBufferPixels(8)
      .setAttr("kind", OCEAN_KIND);
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    if (!sf.canBePolygon()) return;
    var kind = kind(sf);
    if (kind == null) return;

    var wayArea = WayArea.squareMeters(sf);
    boolean intermittent = sf.hasTag("intermittent", "yes");

    var polygon = fc.polygon(name());
    polygon.setMinZoom(minZoom(kind));
    polygon.setMinPixelSize(1.0);
    polygon.setBufferPixels(8);
    polygon.setAttr("kind", kind);
    if (intermittent) polygon.setAttr("intermittent", true);
    if (wayArea != null) polygon.setAttr("way_area", wayArea);
    SmartMapsNames.setNames(sf, polygon, config.languages());

    if (sf.getString("name") != null) {
      var label = fc.pointOnSurface(SmartMapsSchema.WATER_LABEL);
      label.setMinZoom(Math.max(LABEL_MIN_ZOOM, minZoom(kind)));
      label.setBufferPixels(32);
      label.setAttr("kind", kind);
      if (wayArea != null) {
        label.setAttr("way_area", wayArea);
        // Largest first.
        label.setSortKey(SortKey.orderByLog(wayArea, WayArea.MAX_PLAUSIBLE_AREA, 1, 1000).get());
      }
      SmartMapsNames.setNames(sf, label, config.languages());
    }
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items)
    throws GeometryException {
    return FeatureMerge.mergeOverlappingPolygons(items, 1);
  }
}
