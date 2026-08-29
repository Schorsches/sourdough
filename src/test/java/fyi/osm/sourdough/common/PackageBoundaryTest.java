package fyi.osm.sourdough.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fyi.osm.sourdough.shortbread.ShortbreadSchema;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Enforces the rule stated in {@code package-info.java}: the shared package may not depend
 * on any schema.
 *
 * Without this, the boundary erodes one convenient import at a time, and the next schema
 * inherits a "shared" package that is quietly Shortbread-shaped. Checking the sources
 * directly avoids adding an architecture-testing dependency for one rule.
 */
class PackageBoundaryTest {

  private static final Path COMMON = Path.of("src/main/java/fyi/osm/sourdough/common");

  private static final Pattern SCHEMA_IMPORT =
    Pattern.compile("^\\s*import\\s+fyi\\.osm\\.sourdough\\.(shortbread|smartmaps)\\b.*$",
      Pattern.MULTILINE);

  private static List<Path> sources() throws IOException {
    assertTrue(Files.isDirectory(COMMON), "expected the shared package at " + COMMON);
    try (Stream<Path> files = Files.walk(COMMON)) {
      return files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
    }
  }

  private static String read(Path path) throws IOException {
    return Files.readString(path, StandardCharsets.UTF_8);
  }

  @Test
  void theSharedPackageDoesNotImportAnySchema() throws IOException {
    var offenders = new TreeSet<String>();
    for (var source : sources()) {
      var matcher = SCHEMA_IMPORT.matcher(read(source));
      while (matcher.find()) {
        offenders.add(COMMON.relativize(source) + ": " + matcher.group().trim());
      }
    }
    assertEquals(
      new TreeSet<String>(),
      offenders,
      "the shared package must not depend on a schema; move the schema-specific part out"
    );
  }

  @Test
  void theSharedPackageDoesNotNameTileLayers() throws IOException {
    var offenders = new TreeSet<String>();
    for (var source : sources()) {
      var body = read(source);
      for (var layer : ShortbreadSchema.layerNames()) {
        // A layer name only matters as a string literal; it is how a helper would start
        // deciding what to emit rather than what to compute.
        if (body.contains('"' + layer + '"')) {
          offenders.add(COMMON.relativize(source) + ": \"" + layer + "\"");
        }
      }
    }
    assertEquals(
      new TreeSet<String>(),
      offenders,
      "the shared package must not name tile layers; that decision belongs to a schema"
    );
  }

  @Test
  void theSharedPackageIsNotEmpty() {
    // Guards against the checks above passing because the walk found nothing.
    assertTrue(
      sourcesQuietly() >= 8,
      "expected the shared helpers to be present, found " + sourcesQuietly() + " sources"
    );
  }

  private static int sourcesQuietly() {
    try {
      return sources().size();
    } catch (IOException e) {
      return 0;
    }
  }
}
