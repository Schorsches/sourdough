package fyi.osm.sourdough.smartmaps;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.onthegomap.planetiler.TestUtils;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.GeometryException;
import fyi.osm.sourdough.smartmaps.layers.Transport;
import fyi.osm.sourdough.smartmaps.layers.TransportLabel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;

/**
 * The merged layers post-process a list holding more than one geometry type.
 *
 * Shortbread's line layers can call {@code FeatureMerge.mergeLineStrings} knowing every
 * feature in the list is a line. SmartMaps merges layers Shortbread keeps apart, so
 * `transport` post-processes points and polygons alongside its lines. A merge that
 * quietly dropped them would delete every station and every pedestrian square from the
 * tileset, and nothing else in the suite would notice -- the layer would still be present
 * and still be well-formed.
 *
 * Whether each layer HAS post-processing is a separate question, checked across both
 * schemas by {@code LayerPostProcessingTest}.
 */
class MergedLayerPostProcessTest {

  private static final SmartMapsConfiguration CONFIG = SmartMapsConfiguration.defaults();

  private static VectorTile.Feature feature(String layer, Geometry geometry, long id) {
    return new VectorTile.Feature(
      layer,
      id,
      VectorTile.encodeGeometry(geometry),
      Map.of("kind", "test")
    );
  }

  private static List<VectorTile.Feature> mixed(String layer) {
    return List.of(
      feature(layer, TestUtils.newLineString(0, 0, 10, 10), 1),
      feature(layer, TestUtils.newPoint(20, 20), 2),
      feature(layer, TestUtils.rectangle(30, 30, 40, 40), 3)
    );
  }

  @Test
  void transportKeepsItsPointsAndPolygonsThroughTheLineMerge() throws GeometryException {
    var merged = new Transport(CONFIG).postProcess(14, mixed(SmartMapsSchema.TRANSPORT));
    assertEquals(
      3,
      merged.size(),
      "the line merge must pass stops and street areas through, not drop them: " + merged
    );
    assertEquals(
      List.of(1L, 2L, 3L),
      merged.stream().map(VectorTile.Feature::id).sorted().toList()
    );
  }

  @Test
  void transportLabelKeepsItsMotorwayExitsThroughTheLineMerge() throws GeometryException {
    var merged = new TransportLabel(CONFIG)
      .postProcess(14, mixed(SmartMapsSchema.TRANSPORT_LABEL));
    assertEquals(3, merged.size(), "motorway exits are points in a line layer: " + merged);
  }
}
