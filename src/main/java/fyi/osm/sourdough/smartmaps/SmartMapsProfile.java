package fyi.osm.sourdough.smartmaps;

import com.onthegomap.planetiler.ForwardingProfile;
import fyi.osm.sourdough.common.buildings3d.BuildingDimensionParser;
import fyi.osm.sourdough.common.buildings3d.BuildingMetrics;
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

    var layers = new ArrayList<SmartMapsLayer>(List.of());

    for (var layer : layers) {
      registerHandler(layer);
    }

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
