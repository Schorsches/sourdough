package fyi.osm.sourdough.smartmaps.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.WithTags;
import fyi.osm.sourdough.common.buildings3d.BuildingDimensionParser;
import fyi.osm.sourdough.common.buildings3d.BuildingMetrics;
import fyi.osm.sourdough.smartmaps.SmartMapsConfiguration;
import fyi.osm.sourdough.smartmaps.SmartMapsLayer;
import fyi.osm.sourdough.smartmaps.SmartMapsNames;
import fyi.osm.sourdough.smartmaps.SmartMapsSchema;
import java.util.List;

/**
 * SmartMaps `building`: building outlines and Simple 3D Buildings parts in one layer,
 * from zoom 14, ready to extrude.
 *
 * Parts share the layer with their parent buildings and are told apart by the
 * `building:part` flag. That is deliberately the opposite of the shortbread-1.1-3d
 * extension, where parts get a layer of their own so that a stock Shortbread style does
 * not draw complex buildings twice. Here the merged layer is what the target layout
 * specifies, and a style written for it knows to filter on the flag. See BUILDINGS_3D.md.
 *
 * This layout has no factual height field, only `render_height` -- the value is there to
 * be extruded, not to be believed -- so estimated heights are on by default and are not
 * flagged. What the estimate is derived from is recorded in the build log by
 * {@link BuildingMetrics}.
 *
 * Buildings are not merged into multipolygons. Merging by attributes collapsed a whole
 * tile's buildings into a handful of features when it was tried for Shortbread, and it
 * would make per-building extrusion heights meaningless here.
 */
public class Building extends SmartMapsLayer {

  private final BuildingDimensionParser dimensions;
  private final BuildingMetrics metrics;

  public Building(
    SmartMapsConfiguration config,
    BuildingDimensionParser dimensions,
    BuildingMetrics metrics
  ) {
    super(config);
    this.dimensions = dimensions;
    this.metrics = metrics;
  }

  private static final int MIN_ZOOM = 14;

  /**
   * POI-ish tags the layout carries on buildings, so a style can label a building without
   * joining it to the `poi` layer.
   */
  private static final List<String> PASSTHROUGH = List.of(
    "amenity",
    "shop",
    "leisure",
    "tourism",
    "historic",
    "man_made",
    "information",
    "religion",
    "denomination",
    "cuisine",
    "sport"
  );

  @Override
  public String name() {
    return SmartMapsSchema.BUILDING;
  }

  @Override
  public Expression filter() {
    return Expression.or(
      Expression.matchField("building"),
      Expression.matchField("building:part")
    );
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    if (!sf.canBePolygon()) return;

    var building = sf.getString("building");
    var part = sf.getString("building:part");
    boolean isBuilding = building != null && !building.equals("no");
    boolean isPart = part != null && !part.equals("no");
    if (!isBuilding && !isPart) return;

    var polygon = fc.polygon(name());
    polygon.setMinZoom(MIN_ZOOM);
    // Parts are often small slivers of a larger outline, so they survive to a smaller
    // size than a whole building needs to.
    polygon.setMinPixelSize(isPart && !isBuilding ? 0.25 : 0.5);
    polygon.setBufferPixels(8);

    if (isPart) {
      polygon.setAttr("building:part", true);
      metrics.increment(BuildingMetrics.BUILDING_PARTS_EMITTED);
    }

    var parsed = dimensions.parse(sf);
    if (parsed.height() != null) {
      polygon.setAttr("render_height", parsed.height());
    }
    if (parsed.minHeight() != null && parsed.minHeight() > 0) {
      polygon.setAttr("render_min_height", parsed.minHeight());
    }
    if (has3dInformation(sf)) {
      polygon.setAttr("3d", true);
    }
    if (hasRoofInformation(sf)) {
      polygon.setAttr("roof", true);
    }

    SmartMapsNames.setNames(sf, polygon, config.languages());
    setIfPresent(sf, polygon, "addr:housename", "housename");
    setIfPresent(sf, polygon, "addr:housenumber", "housenumber");
    for (var key : PASSTHROUGH) {
      setIfPresent(sf, polygon, key, key);
    }
    if (sf.hasTag("amenity", "bank") && sf.hasTag("atm", "yes")) {
      polygon.setAttr("atm", true);
    }
  }

  /**
   * Inferred: the layout names a boolean `3d` and says nothing more about it, so it is
   * read as "this feature carries Simple 3D Buildings information", which is what makes
   * an extrusion faithful rather than estimated. An estimated height alone does not
   * qualify -- that would set the flag on every building in the world.
   */
  static boolean has3dInformation(WithTags sf) {
    return sf.hasTag("height") ||
      sf.hasTag("building:levels") ||
      sf.hasTag("min_height") ||
      sf.hasTag("building:min_level") ||
      sf.hasTag("building:part") ||
      hasRoofInformation(sf);
  }

  /** Inferred: `roof` means the roof has a shape or a height worth modelling. */
  static boolean hasRoofInformation(WithTags sf) {
    return sf.hasTag("roof:shape") || sf.hasTag("roof:height") || sf.hasTag("roof:levels");
  }

  private static void setIfPresent(
    SourceFeature sf,
    FeatureCollector.Feature feature,
    String osmKey,
    String attribute
  ) {
    var value = sf.getString(osmKey);
    if (value != null) {
      feature.setAttr(attribute, value);
    }
  }
}
