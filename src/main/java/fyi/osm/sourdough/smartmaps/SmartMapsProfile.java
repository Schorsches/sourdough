package fyi.osm.sourdough.smartmaps;

import com.onthegomap.planetiler.ForwardingProfile;
import fyi.osm.sourdough.common.buildings3d.BuildingDimensionParser;
import fyi.osm.sourdough.common.buildings3d.BuildingMetrics;
import fyi.osm.sourdough.smartmaps.layers.Boundary;
import fyi.osm.sourdough.smartmaps.layers.Building;
import fyi.osm.sourdough.smartmaps.layers.HousenumberLabel;
import fyi.osm.sourdough.smartmaps.layers.Land;
import fyi.osm.sourdough.smartmaps.layers.PlaceLabel;
import fyi.osm.sourdough.smartmaps.layers.Poi;
import fyi.osm.sourdough.smartmaps.layers.Transport;
import fyi.osm.sourdough.smartmaps.layers.TransportLabel;
import fyi.osm.sourdough.smartmaps.layers.WaterLines;
import fyi.osm.sourdough.smartmaps.layers.WaterPolygons;
import java.util.ArrayList;
import java.util.List;

/**
 * Planetiler profile producing a tileset with the SmartMaps layer layout.
 *
 * This is <strong>shape-compatible, not the SmartMaps tileset</strong>. It emits the layer
 * and field names a SmartMaps style expects, with content classified by this project's own
 * rules; individual `kind` values may differ from the published tileset. Nothing here is
 * affiliated with or endorsed by SmartMaps, and the output carries OpenStreetMap
 * attribution rather than theirs. See SMARTMAPS_SCHEMA.md.
 */
public class SmartMapsProfile extends ForwardingProfile {

  private static final org.slf4j.Logger LOGGER =
    org.slf4j.LoggerFactory.getLogger(SmartMapsProfile.class);

  private final SmartMapsConfiguration config;
  private final BuildingMetrics buildingMetrics = new BuildingMetrics();

  public SmartMapsProfile(SmartMapsConfiguration config) {
    this.config = config;

    var dimensions = new BuildingDimensionParser(
      config.levelHeight(),
      config.estimateMissingHeights(),
      buildingMetrics
    );

    var layers = new ArrayList<SmartMapsLayer>(
      List.of(
        new WaterPolygons(config),
        new WaterLines(config),
        new Boundary(config),
        new PlaceLabel(config),
        new Land(config),
        new Building(config, dimensions, buildingMetrics),
        new HousenumberLabel(config),
        new Transport(config),
        new TransportLabel(config),
        new Poi(config)
      )
    );

    for (var layer : layers) {
      registerHandler(layer);

      // The ocean comes from the preprocessed coastline shapefile, not from OSM. This
      // layout has no ocean layer, so it lands in water_polygons as kind=ocean.
      if (layer instanceof WaterPolygons water) {
        registerSourceHandler("osm_water", water::processPreparedOcean);
      }
    }

    // landcover is written by the Land handler but is not the layer it declares, so
    // Planetiler would never post-process it. Polygons there want the same merge as
    // landuse.
    registerHandler(new SecondaryLayer(SmartMapsSchema.LANDCOVER, Land::merge));

    registerHandler(
      (ForwardingProfile.FinishHandler) (sourceName, featureCollectors, next) -> {
        if ("osm".equals(sourceName)) {
          LOGGER.info(buildingMetrics.summary());
        }
      }
    );
  }

  public SmartMapsConfiguration configuration() {
    return config;
  }

  public BuildingMetrics buildingMetrics() {
    return buildingMetrics;
  }

  @Override
  public String name() {
    return "SmartMaps-compatible";
  }

  @Override
  public String description() {
    return "Vector tiles using the SmartMaps layer layout, generated from OpenStreetMap " +
      "data. Not affiliated with SmartMaps.";
  }

  @Override
  public String version() {
    return SmartMapsSchema.SCHEMA_VERSION;
  }

  @Override
  public boolean isOverlay() {
    return false;
  }

  @Override
  public String attribution() {
    return "Map data from <a href='https://www.openstreetmap.org/copyright' target='_blank'>OpenStreetMap</a>";
  }
}
