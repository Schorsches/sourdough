package fyi.osm.sourdough.shortbread;

import com.onthegomap.planetiler.ForwardingProfile;
import fyi.osm.sourdough.shortbread.layers.Dams;
import fyi.osm.sourdough.shortbread.layers.Ocean;
import fyi.osm.sourdough.shortbread.layers.Piers;
import fyi.osm.sourdough.shortbread.layers.WaterLines;
import fyi.osm.sourdough.shortbread.layers.WaterPolygons;
import java.util.List;

/**
 * Planetiler profile producing the Shortbread 1.1 schema, optionally with the 3D
 * buildings extension.
 *
 * The Sourdough profile in {@link fyi.osm.sourdough.Builder} is untouched by this class;
 * the two share input sources and a handful of parsing utilities, and nothing else.
 */
public class ShortbreadProfile extends ForwardingProfile {

  private final ShortbreadConfiguration config;

  public ShortbreadProfile(ShortbreadConfiguration config) {
    this.config = config;

    var layers = List.<ShortbreadLayer>of(
      new Ocean(config),
      new WaterPolygons(config),
      new WaterLines(config),
      new Dams(config),
      new Piers(config)
    );

    for (var layer : layers) {
      registerHandler(layer);

      // The ocean comes from the preprocessed coastline shapefile, not from OSM.
      if (layer instanceof Ocean ocean) {
        registerSourceHandler("osm_water", ocean::processPreparedOcean);
      }
    }
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
