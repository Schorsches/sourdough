package fyi.osm.sourdough;

import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.Planetiler;
import com.onthegomap.planetiler.config.Arguments;
import fyi.osm.sourdough.layers.Advertising;
import fyi.osm.sourdough.layers.Aerialways;
import fyi.osm.sourdough.layers.Aeroways;
import fyi.osm.sourdough.layers.Amenities;
import fyi.osm.sourdough.layers.Barriers;
import fyi.osm.sourdough.layers.Boundaries;
import fyi.osm.sourdough.layers.Buildings;
import fyi.osm.sourdough.layers.Clubs;
import fyi.osm.sourdough.layers.Craft;
import fyi.osm.sourdough.layers.Education;
import fyi.osm.sourdough.layers.Emergency;
import fyi.osm.sourdough.layers.Geological;
import fyi.osm.sourdough.layers.Healthcare;
import fyi.osm.sourdough.layers.Highways;
import fyi.osm.sourdough.layers.Historic;
import fyi.osm.sourdough.layers.Landcover;
import fyi.osm.sourdough.layers.Landuse;
import fyi.osm.sourdough.layers.Leisure;
import fyi.osm.sourdough.layers.ManMade;
import fyi.osm.sourdough.layers.Military;
import fyi.osm.sourdough.layers.Natural;
import fyi.osm.sourdough.layers.Offices;
import fyi.osm.sourdough.layers.Pistes;
import fyi.osm.sourdough.layers.Places;
import fyi.osm.sourdough.layers.Power;
import fyi.osm.sourdough.layers.PublicTransport;
import fyi.osm.sourdough.layers.Railways;
import fyi.osm.sourdough.layers.Routes;
import fyi.osm.sourdough.layers.Shops;
import fyi.osm.sourdough.layers.Tourism;
import fyi.osm.sourdough.layers.Water;
import fyi.osm.sourdough.layers.Waterways;
import fyi.osm.sourdough.shortbread.LanguagePresets;
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadProfile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class Builder extends ForwardingProfile {

  private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(Builder.class);

  private final Configuration config;

  public Builder(Configuration config) {
    this.config = config;
    var layers = List.of(
      new Advertising(config),
      new Aerialways(config),
      new Aeroways(config),
      new Amenities(config),
      new Barriers(config),
      new Boundaries(config),
      new Buildings(config),
      new Clubs(config),
      new Craft(config),
      new Education(config),
      new Emergency(config),
      new Geological(config),
      new Healthcare(config),
      new Highways(config),
      new Historic(config),
      new Landcover(config),
      new Landuse(config),
      new Leisure(config),
      new ManMade(config),
      new Military(config),
      new Natural(config),
      new Offices(config),
      new Pistes(config),
      new Places(config),
      new Power(config),
      new PublicTransport(config),
      new Railways(config),
      new Routes(config),
      new Shops(config),
      new Tourism(config),
      new Water(config),
      new Waterways(config)
    );

    for (var layer : layers) {
      registerHandler(layer);

      // Water layer has special requirement for preprocessed ocean data
      if (layer instanceof Water) {
        registerSourceHandler("osm_water", ((Water) layer)::processPreparedOsm);
      }
    }
  }

  @Override
  public String name() {
    return "Sourdough Tiles";
  }

  @Override
  public String description() {
    return "Vector tiles derived from OpenStreetMap data. See https://sourdough.osm.fyi/";
  }

  @Override
  public String version() {
    return "0.4.0";
  }

  @Override
  public boolean isOverlay() {
    return false;
  }

  @Override
  public String attribution() {
    return "Map data from <a href='https://www.openstreetmap.org/copyright' target='_blank'>OpenStreetMap</a>";
  }

  public static void main(String[] args) throws IOException {
    run(Arguments.fromArgsOrConfigFile(args));
  }

  static void run(Arguments args) throws IOException {
    var schema = Schema.fromId(
      args.getString("schema", "tile schema to generate (" + Schema.ids() + ")", Schema.SOURDOUGH.id())
    );

    // Each schema carries its own default maxzoom. An explicit --maxzoom still wins,
    // except that Shortbread fixes the tileset maxzoom at 14 by specification.
    int requestedMaxzoom = args.getInteger("maxzoom", "maximum zoom level", schema.defaultMaxzoom());
    if (schema.isShortbread() && requestedMaxzoom > schema.defaultMaxzoom()) {
      throw new IllegalArgumentException(
        "The " +
        schema.id() +
        " schema has a fixed maximum zoom of " +
        schema.defaultMaxzoom() +
        ", but --maxzoom " +
        requestedMaxzoom +
        " was requested. Shortbread tiles are overzoomed by the client above zoom " +
        schema.defaultMaxzoom() +
        "; building higher zoom levels would produce an archive that is not Shortbread."
      );
    }
    args = args.orElse(Arguments.of("maxzoom", schema.defaultMaxzoom()));

    String area = args.getString("area", "geofabrik area to download", "monaco");
    String language = args.getString(
      "language",
      "language code for name substitution (e.g. 'es' for Spanish)",
      null
    );
    List<String> additionalLanguages = args.getList(
      "additional_languages",
      "list of additional languages to include as separate attributes (e.g. 'fr,de,es'), " +
      "or a preset name (" +
      LanguagePresets.presetNames() +
      ")",
      List.of()
    );

    var planetiler = Planetiler.create(args)
      .addOsmSource("osm", Path.of("data", "sources", area + ".osm.pbf"), "geofabrik:" + area)
      .addShapefileSource(
        "osm_water",
        Path.of("data", "sources", "water-polygons-split-3857.zip"),
        "https://osmdata.openstreetmap.de/download/water-polygons-split-3857.zip"
      );

    if (schema.isShortbread()) {
      if (language != null) {
        LOGGER.warn(
          "--language does not apply to the {} schema: Shortbread's `name` attribute is the " +
          "OSM name tag itself. Use --additional-languages to emit name_xx attributes instead.",
          schema.id()
        );
      }
      var languages = LanguagePresets.resolve(additionalLanguages);
      var config = new ShortbreadConfiguration(
        schema,
        languages,
        ShortbreadConfiguration.DEFAULT_LEVEL_HEIGHT_METERS
      );
      planetiler
        .setProfile(new ShortbreadProfile(config))
        .setOutput("data/" + schema.id() + ".pmtiles")
        .run();
      return;
    }

    var config = new Configuration(language, additionalLanguages);
    planetiler.setProfile(new Builder(config)).setOutput("data/sourdough.pmtiles").run();
  }
}
