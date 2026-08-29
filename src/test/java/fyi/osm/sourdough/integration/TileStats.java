package fyi.osm.sourdough.integration;

import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.archive.ReadableTileArchive;
import com.onthegomap.planetiler.util.Gzip;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Tile-size and feature statistics for a generated archive.
 *
 * The interesting number for this schema is the zoom-14 distribution: that is where
 * buildings, addresses and POIs all appear at once, and where per-building 3D attributes
 * would show up as tile growth if they were going to.
 */
public record TileStats(
  int tileCount,
  long totalBytes,
  Map<Integer, SizeDistribution> byZoom,
  Map<String, Integer> featuresByLayer,
  Map<String, java.util.Set<String>> attributesByLayer
) {

  /** @param max the largest single tile, which is what a client actually waits for */
  public record SizeDistribution(int count, long p50, long p95, long p99, long max, long total) {
    @Override
    public String toString() {
      return String.format(
        "n=%-6d p50=%-7s p95=%-7s p99=%-7s max=%-8s total=%s",
        count, human(p50), human(p95), human(p99), human(max), human(total)
      );
    }
  }

  public static String human(long bytes) {
    if (bytes < 1024) return bytes + "B";
    if (bytes < 1024 * 1024) return String.format("%.1fkB", bytes / 1024.0);
    return String.format("%.1fMB", bytes / (1024.0 * 1024));
  }

  public static TileStats of(ReadableTileArchive archive) throws IOException {
    var sizesByZoom = new TreeMap<Integer, List<Long>>();
    var features = new TreeMap<String, Integer>();
    var attributes = new TreeMap<String, java.util.Set<String>>();
    int tileCount = 0;
    long totalBytes = 0;

    try (var tiles = archive.getAllTiles()) {
      while (tiles.hasNext()) {
        var tile = tiles.next();
        int zoom = tile.coord().z();
        long size = tile.bytes().length;
        sizesByZoom.computeIfAbsent(zoom, z -> new ArrayList<>()).add(size);
        tileCount++;
        totalBytes += size;

        // Feature counts are only collected at the maximum zoom, where every layer is
        // present; collecting them at every zoom would count the same feature repeatedly.
        if (zoom == 14) {
          for (var feature : VectorTile.decode(Gzip.gunzip(tile.bytes()))) {
            features.merge(feature.layer(), 1, Integer::sum);
            attributes
              .computeIfAbsent(feature.layer(), l -> new java.util.TreeSet<>())
              .addAll(feature.tags().keySet());
          }
        }
      }
    }

    var byZoom = new LinkedHashMap<Integer, SizeDistribution>();
    sizesByZoom.forEach((zoom, sizes) -> byZoom.put(zoom, distribution(sizes)));
    return new TileStats(tileCount, totalBytes, byZoom, features, attributes);
  }

  private static SizeDistribution distribution(List<Long> sizes) {
    var sorted = sizes.stream().sorted().toList();
    long total = sorted.stream().mapToLong(Long::longValue).sum();
    return new SizeDistribution(
      sorted.size(),
      percentile(sorted, 0.50),
      percentile(sorted, 0.95),
      percentile(sorted, 0.99),
      sorted.get(sorted.size() - 1),
      total
    );
  }

  private static long percentile(List<Long> sorted, double fraction) {
    int index = (int) Math.ceil(fraction * sorted.size()) - 1;
    return sorted.get(Math.clamp(index, 0, sorted.size() - 1));
  }

  /** A human-readable report, printed by the integration tests. */
  public String report(String label) {
    var out = new StringBuilder();
    out.append(label).append('\n');
    out.append("  tiles=").append(tileCount).append("  archive features=")
      .append(human(totalBytes)).append('\n');
    byZoom.forEach((zoom, distribution) ->
      out.append(String.format("  z%-2d %s%n", zoom, distribution))
    );
    out.append("  z14 features by layer:\n");
    featuresByLayer.forEach((layer, count) ->
      out.append(String.format("    %-26s %6d%n", layer, count))
    );
    return out.toString();
  }
}
