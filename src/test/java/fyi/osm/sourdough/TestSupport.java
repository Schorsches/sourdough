package fyi.osm.sourdough;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.TestUtils;
import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.reader.SimpleFeature;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.osm.OsmReader;
import com.onthegomap.planetiler.reader.osm.OsmRelationInfo;
import com.onthegomap.planetiler.stats.Stats;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.locationtech.jts.geom.Geometry;

/**
 * Small harness for driving a layer's processFeature() directly, without a PBF.
 *
 * Geometries are given in world coordinates (0..1 across the Mercator square) so that
 * a test can control a feature's apparent size, which several layers use to pick zooms.
 */
public final class TestSupport {

  private TestSupport() {}

  private static final Map<Integer, FeatureCollector.Factory> FACTORIES = new java.util.HashMap<>();

  /** Planetiler clamps zoom ranges to the run's maxzoom, so tests must pick one. */
  private static synchronized FeatureCollector.Factory factory(int maxzoom) {
    return FACTORIES.computeIfAbsent(maxzoom, mz -> {
      var config = PlanetilerConfig.from(
        com.onthegomap.planetiler.config.Arguments.of("maxzoom", Integer.toString(mz))
      );
      return new FeatureCollector.Factory(config, Stats.inMemory());
    });
  }

  /** Sourdough builds to zoom 15; Shortbread builds to zoom 14. */
  public static final int SOURDOUGH_MAXZOOM = 15;
  public static final int SHORTBREAD_MAXZOOM = 14;

  /** An OSM node with the given tags. */
  public static SourceFeature node(Map<String, Object> tags) {
    return osm(TestUtils.newPoint(0.5, 0.5), tags, List.of());
  }

  /** An OSM way that can be interpreted as a line. */
  public static SourceFeature way(Map<String, Object> tags) {
    return osm(TestUtils.newLineString(0.5, 0.5, 0.5001, 0.5001), tags, List.of());
  }

  /** A longer OSM way, for layers with a minimum length. */
  public static SourceFeature longWay(Map<String, Object> tags) {
    return osm(TestUtils.newLineString(0.4, 0.4, 0.6, 0.6), tags, List.of());
  }

  /** An OSM closed way / multipolygon, sized as a square with the given side length. */
  public static SourceFeature area(Map<String, Object> tags, double side) {
    double lo = 0.5 - side / 2;
    double hi = 0.5 + side / 2;
    return osm(TestUtils.rectangle(lo, lo, hi, hi), tags, List.of());
  }

  /** A small area, big enough to survive minimum-size filters at high zoom. */
  public static SourceFeature area(Map<String, Object> tags) {
    return area(tags, 0.001);
  }

  /** An OSM way that belongs to the given preprocessed relations. */
  public static SourceFeature wayInRelations(
    Map<String, Object> tags,
    List<OsmReader.RelationMember<OsmRelationInfo>> relations
  ) {
    return osm(TestUtils.newLineString(0.4, 0.4, 0.6, 0.6), tags, relations);
  }

  private static SourceFeature osm(
    Geometry worldGeometry,
    Map<String, Object> tags,
    List<OsmReader.RelationMember<OsmRelationInfo>> relations
  ) {
    return SimpleFeature.createFakeOsmFeature(
      com.onthegomap.planetiler.geo.GeoUtils.worldToLatLonCoords(worldGeometry),
      tags,
      "osm",
      null,
      1,
      relations
    );
  }

  /** Runs the layer over the feature and returns everything it emitted. */
  public static List<FeatureCollector.Feature> process(
    ForwardingProfile.FeatureProcessor layer,
    SourceFeature sf
  ) {
    return process(layer, sf, SHORTBREAD_MAXZOOM);
  }

  /** Runs the layer with an explicit tileset maxzoom. */
  public static List<FeatureCollector.Feature> process(
    ForwardingProfile.FeatureProcessor layer,
    SourceFeature sf,
    int maxzoom
  ) {
    var collector = factory(maxzoom).get(sf);
    layer.processFeature(sf, collector);
    var result = new ArrayList<FeatureCollector.Feature>();
    collector.forEach(result::add);
    return result;
  }

  /** Convenience for the common case of expecting exactly one emitted feature. */
  public static FeatureCollector.Feature processOne(
    ForwardingProfile.FeatureProcessor layer,
    SourceFeature sf
  ) {
    return processOne(layer, sf, SHORTBREAD_MAXZOOM);
  }

  /** Convenience for one emitted feature at an explicit tileset maxzoom. */
  public static FeatureCollector.Feature processOne(
    ForwardingProfile.FeatureProcessor layer,
    SourceFeature sf,
    int maxzoom
  ) {
    var features = process(layer, sf, maxzoom);
    if (features.size() != 1) {
      throw new AssertionError("expected exactly 1 feature, got " + features.size() + ": " + features);
    }
    return features.get(0);
  }
}
