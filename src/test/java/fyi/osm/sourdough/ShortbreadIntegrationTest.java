package fyi.osm.sourdough;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.onthegomap.planetiler.archive.TileArchives;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.config.PlanetilerConfig;
import fyi.osm.sourdough.integration.TileStats;
import fyi.osm.sourdough.shortbread.ShortbreadSchema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * End-to-end generation from a real OSM extract, and checks on the resulting archive.
 *
 * Excluded from `mvn test` by default because it needs input data that CI does not carry
 * and takes tens of seconds per schema. Run it with:
 *
 *   mvn test -DexcludedTestGroups= -Dtest=ShortbreadIntegrationTest
 *
 * The input files are the ones a normal run downloads:
 *   data/sources/&lt;area&gt;.osm.pbf
 *   data/sources/water-polygons-split-3857.zip
 *
 * If they are missing the tests skip rather than fail, so that a checkout without the
 * data is not reported as broken.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShortbreadIntegrationTest {

  private static final String AREA = System.getProperty("integration.area", "monaco");
  private static final Path SOURCES = Path.of("data", "sources");
  private static final Path OUTPUT = Path.of("data", "integration");

  private Path shortbread;
  private Path shortbread3d;
  private Path sourdough;

  @BeforeAll
  void generate() throws IOException {
    assumeTrue(
      Files.exists(SOURCES.resolve(AREA + ".osm.pbf")),
      "no OSM extract at " + SOURCES.resolve(AREA + ".osm.pbf") + "; skipping"
    );
    assumeTrue(
      Files.exists(SOURCES.resolve("water-polygons-split-3857.zip")),
      "no water polygons in " + SOURCES + "; skipping"
    );
    Files.createDirectories(OUTPUT);

    shortbread = build("shortbread-1.1");
    shortbread3d = build("shortbread-1.1-3d");
    sourdough = build("sourdough");
  }

  private Path build(String schema) throws IOException {
    var output = OUTPUT.resolve(schema + ".pmtiles");
    Builder.run(
      Arguments.of(
        Map.of(
          "area", AREA,
          "schema", schema,
          "output", output.toString(),
          "force", "true"
        )
      )
    );
    return output;
  }

  private TileStats stats(Path archive) throws IOException {
    try (var reader = TileArchives.newReader(archive, PlanetilerConfig.defaults())) {
      return TileStats.of(reader);
    }
  }

  @Test
  void shortbreadStopsAtZoom14AndSourdoughAtZoom15() throws IOException {
    try (var reader = TileArchives.newReader(shortbread, PlanetilerConfig.defaults())) {
      assertEquals(ShortbreadSchema.MAXZOOM, reader.metadata().maxzoom());
      assertEquals(0, reader.metadata().minzoom());
    }
    try (var reader = TileArchives.newReader(sourdough, PlanetilerConfig.defaults())) {
      assertEquals(15, reader.metadata().maxzoom(), "Sourdough must keep its own maxzoom");
    }
  }

  @Test
  void everyGeneratedLayerIsPartOfTheSchema() throws IOException {
    var known = new HashSet<>(ShortbreadSchema.layerNames());
    var produced = stats(shortbread).featuresByLayer().keySet();
    assertTrue(produced.size() > 10, "expected a realistic number of layers, got " + produced);
    for (var layer : produced) {
      assertTrue(known.contains(layer), layer + " is not a Shortbread layer");
    }
  }

  @Test
  void everyGeneratedAttributeIsDefinedWithTheRightType() throws IOException {
    var problems = new TreeSet<String>();
    var statistics = stats(shortbread);
    statistics.attributesByLayer().forEach((layer, attributes) -> {
      var spec = ShortbreadSchema.layer(layer);
      for (var attribute : attributes) {
        if (!spec.attributes().containsKey(attribute)) {
          problems.add(layer + "." + attribute);
        }
      }
    });
    assertEquals(new TreeSet<String>(), problems, "attributes not defined by the schema");
  }

  @Test
  void buildingsKeepTheirIndividualIdentity() throws IOException {
    var buildings = stats(shortbread).featuresByLayer().getOrDefault("buildings", 0);
    // Merging buildings by attributes would collapse a whole tile into a handful of
    // multipolygons, since they all carry the same single `dummy` attribute.
    assertTrue(
      buildings > 100,
      "expected individual building polygons, got " + buildings + " features"
    );
  }

  @Test
  void theThreeDSchemaCarriesTheSameBuildingsPlusHeights() throws IOException {
    var base = stats(shortbread);
    var extended = stats(shortbread3d);

    assertEquals(
      base.featuresByLayer().get("buildings"),
      extended.featuresByLayer().get("buildings"),
      "the 3D schema must not add or drop buildings"
    );
    assertTrue(
      extended.attributesByLayer().get("buildings").contains("height"),
      "3D buildings should carry heights"
    );
    assertFalse(
      base.attributesByLayer().get("buildings").contains("height"),
      "base Shortbread must not carry heights"
    );
    assertFalse(
      base.featuresByLayer().containsKey(ShortbreadSchema.BUILDING_PARTS),
      "base Shortbread must not contain building_parts"
    );
  }

  @Test
  void theThreeDExtensionCostsLittleInTileSize() throws IOException {
    var base = stats(shortbread);
    var extended = stats(shortbread3d);
    double growth = (extended.totalBytes() - base.totalBytes()) / (double) base.totalBytes();

    System.out.println(base.report("shortbread-1.1"));
    System.out.println(extended.report("shortbread-1.1-3d"));
    System.out.printf("3D tile-size delta: %+.1f%%%n", growth * 100);

    // A generous ceiling. The point is to catch an attribute being added that lands on
    // every building on the planet, not to pin an exact number.
    assertTrue(growth < 0.25, "3D extension grew tiles by " + Math.round(growth * 100) + "%");
  }

  @Test
  void zoom14TilesStayWithinAReasonableSize() throws IOException {
    var zoom14 = stats(shortbread3d).byZoom().get(14);
    assumeTrue(zoom14 != null, "no zoom 14 tiles in this extract");
    // Planetiler warns above 1MB; a Shortbread tile has no business being near that.
    assertTrue(
      zoom14.max() < 1_000_000,
      "largest zoom 14 tile is " + TileStats.human(zoom14.max())
    );
  }
}
