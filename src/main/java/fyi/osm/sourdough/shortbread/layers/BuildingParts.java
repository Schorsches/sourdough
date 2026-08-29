package fyi.osm.sourdough.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadLayer;
import fyi.osm.sourdough.shortbread.ShortbreadSchema;
import fyi.osm.sourdough.common.buildings3d.BuildingDimensionParser;
import fyi.osm.sourdough.common.buildings3d.BuildingMetrics;
import fyi.osm.sourdough.shortbread.buildings3d.Buildings3d;

/**
 * `building_parts`: Simple 3D Buildings parts, in the shortbread-1.1-3d schema only.
 *
 * Parts live in their own layer rather than in `buildings` on purpose. A part overlaps
 * its parent building exactly, so a style that draws the `buildings` layer without
 * knowing about parts would draw every complex building twice. Keeping them separate
 * means an existing Shortbread style renders this schema exactly as it renders the base
 * one, and a 3D renderer can suppress a parent outline in favour of its parts.
 */
public class BuildingParts extends ShortbreadLayer {

  private final BuildingDimensionParser dimensions;
  private final BuildingMetrics metrics;

  public BuildingParts(
    ShortbreadConfiguration config,
    BuildingDimensionParser dimensions,
    BuildingMetrics metrics
  ) {
    super(config);
    this.dimensions = dimensions;
    this.metrics = metrics;
  }

  private static final int MIN_ZOOM = 14;

  @Override
  public String name() {
    return ShortbreadSchema.BUILDING_PARTS;
  }

  @Override
  public Expression filter() {
    return Expression.matchField("building:part");
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    if (!config.hasBuildings3d()) return;
    if (!sf.canBePolygon()) return;
    var part = sf.getString("building:part");
    if (part == null || part.equals("no")) return;

    var polygon = fc.polygon(name());
    polygon.setMinZoom(MIN_ZOOM);
    polygon.setMinPixelSize(0.25);
    polygon.setBufferPixels(8);
    polygon.setAttr("building_part", part);

    Buildings3d.setAttributes(sf, polygon, dimensions.parse(sf));
    metrics.increment(BuildingMetrics.BUILDING_PARTS_EMITTED);
  }
}
