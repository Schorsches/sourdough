package fyi.osm.sourdough.shortbread;

import com.onthegomap.planetiler.ForwardingProfile;
import fyi.osm.sourdough.common.buildings3d.BuildingDimensionParser;
import fyi.osm.sourdough.common.buildings3d.BuildingMetrics;
import fyi.osm.sourdough.shortbread.layers.Addresses;
import fyi.osm.sourdough.shortbread.layers.Aerialways;
import fyi.osm.sourdough.shortbread.layers.Boundaries;
import fyi.osm.sourdough.shortbread.layers.BoundaryLabels;
import fyi.osm.sourdough.shortbread.layers.Bridges;
import fyi.osm.sourdough.shortbread.layers.BuildingParts;
import fyi.osm.sourdough.shortbread.layers.Buildings;
import fyi.osm.sourdough.shortbread.layers.Dams;
import fyi.osm.sourdough.shortbread.layers.Ferries;
import fyi.osm.sourdough.shortbread.layers.Land;
import fyi.osm.sourdough.shortbread.layers.Ocean;
import fyi.osm.sourdough.shortbread.layers.Piers;
import fyi.osm.sourdough.shortbread.layers.PlaceLabels;
import fyi.osm.sourdough.shortbread.layers.Pois;
import fyi.osm.sourdough.shortbread.layers.PublicTransport;
import fyi.osm.sourdough.shortbread.layers.Sites;
import fyi.osm.sourdough.shortbread.layers.StreetLabels;
import fyi.osm.sourdough.shortbread.layers.StreetLabelsPoints;
import fyi.osm.sourdough.shortbread.layers.StreetPolygons;
import fyi.osm.sourdough.shortbread.layers.Streets;
import fyi.osm.sourdough.shortbread.layers.WaterLines;
import fyi.osm.sourdough.shortbread.layers.WaterPolygons;
import java.util.ArrayList;
import java.util.List;

/**
 * Planetiler profile producing the Shortbread 1.1 schema, optionally with the 3D
 * buildings extension.
 *
 * The Sourdough profile in {@link fyi.osm.sourdough.Builder} is untouched by this class;
 * the two share input sources and a handful of parsing utilities, and nothing else.
 */
public class ShortbreadProfile extends ForwardingProfile {

  private static final org.slf4j.Logger LOGGER =
    org.slf4j.LoggerFactory.getLogger(ShortbreadProfile.class);

  private final ShortbreadConfiguration config;
  private final BuildingMetrics buildingMetrics = new BuildingMetrics();

  public ShortbreadProfile(ShortbreadConfiguration config) {
    this.config = config;

    var dimensions = new BuildingDimensionParser(
      config.levelHeight(),
      config.estimateMissingHeights(),
      buildingMetrics
    );

    var layers = new ArrayList<ShortbreadLayer>(
      List.of(
        new Ocean(config),
        new WaterPolygons(config),
        new WaterLines(config),
        new Dams(config),
        new Piers(config),
        new Boundaries(config),
        new BoundaryLabels(config),
        new PlaceLabels(config),
        new Land(config),
        new Sites(config),
        new Buildings(config, dimensions),
        new Addresses(config),
        new Streets(config),
        new StreetPolygons(config),
        new StreetLabels(config),
        new StreetLabelsPoints(config),
        new Bridges(config),
        new Aerialways(config),
        new Ferries(config),
        new PublicTransport(config),
        new Pois(config)
      )
    );

    // The building_parts layer exists only in the 3D schema.
    if (config.hasBuildings3d()) {
      layers.add(new BuildingParts(config, dimensions, buildingMetrics));
    }

    for (var layer : layers) {
      registerHandler(layer);

      // The ocean comes from the preprocessed coastline shapefile, not from OSM.
      if (layer instanceof Ocean ocean) {
        registerSourceHandler("osm_water", ocean::processPreparedOcean);
      }
    }

    if (config.hasBuildings3d()) {
      // Report the data-quality counters once, after the OSM pass is done.
      registerHandler(
        (ForwardingProfile.FinishHandler) (sourceName, featureCollectors, next) -> {
          if ("osm".equals(sourceName)) {
            LOGGER.info(buildingMetrics.summary());
          }
        }
      );
    }
  }

  /** Exposed for tests and for the end-of-run summary. */
  public BuildingMetrics buildingMetrics() {
    return buildingMetrics;
  }

  public ShortbreadConfiguration configuration() {
    return config;
  }

  @Override
  public String name() {
    return config.hasBuildings3d() ? "Shortbread 1.1 + 3D Buildings" : "Shortbread 1.1";
  }

  @Override
  public String description() {
    return config.hasBuildings3d()
      ? "Shortbread " +
      ShortbreadSchema.SPEC_VERSION +
      " vector tiles from OpenStreetMap data, with a documented 3D buildings extension. " +
      "See " +
      ShortbreadSchema.SPEC_URL
      : "Shortbread " +
      ShortbreadSchema.SPEC_VERSION +
      " vector tiles derived from OpenStreetMap data. See " +
      ShortbreadSchema.SPEC_URL;
  }

  @Override
  public String version() {
    return ShortbreadSchema.SPEC_VERSION;
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
