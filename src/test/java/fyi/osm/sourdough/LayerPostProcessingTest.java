package fyi.osm.sourdough;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.TestUtils;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.GeometryException;
import fyi.osm.sourdough.common.SchemaDescription.Geometry;
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadProfile;
import fyi.osm.sourdough.shortbread.ShortbreadSchema;
import fyi.osm.sourdough.smartmaps.SmartMapsConfiguration;
import fyi.osm.sourdough.smartmaps.SmartMapsProfile;
import fyi.osm.sourdough.smartmaps.SmartMapsSchema;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Every layer with mergeable geometry has a post-processing decision on record.
 *
 * A handler declares one layer name, and Planetiler keys post-processors by it:
 *
 * <pre>
 *   layerPostProcessors.computeIfAbsent(handler.name(), ...).add(handler);   // register
 *   var processors = layerPostProcessors.get(layerName);                     // look up
 *   if (processors != null) { ... }                                          // else pass through
 * </pre>
 *
 * A handler may nonetheless emit into any number of layers, because emitting only names a
 * layer at the call site. So a handler that writes into a second layer gets no
 * post-processing there, and the lookup finding nothing is a normal, silent outcome: no
 * exception, no warning, no missing layer. Just unmerged geometry, more features and a
 * fatter tile. That is what happened to `landcover`, which the `landuse` handler writes
 * into: 257 features at zoom 14 on a Monaco extract where 113 was right.
 *
 * The check is deliberately not "every layer merges" -- plenty should not. It is "every
 * layer that COULD merge has been thought about", and {@link #EXEMPT} is where that
 * thinking is written down. A new handler that writes somewhere new fails here until
 * someone either registers a merge or adds a reason below.
 *
 * Point-only layers are exempt by construction: there is nothing to merge.
 *
 * This probes the real behaviour through the public postProcessLayerFeatures rather than
 * reading Planetiler's private registry, so it stays true if the internals change. A
 * post-processor that does something other than merge would read here as "no
 * post-processing"; the fix is a line in EXEMPT saying so, which is the point.
 */
class LayerPostProcessingTest {

  /**
   * Layers with mergeable geometry that deliberately have no merge, and why.
   *
   * Anything not listed must actually merge. Adding a line here is a decision, so it
   * needs a reason someone can disagree with later.
   */
  private static final Map<String, String> EXEMPT = exemptions();

  /**
   * Shortbread post-processes exactly seven layers: boundaries, ferries, land, ocean,
   * street_labels, streets and water_lines. The rest were never merged, and this
   * repository's schema work did not change that. Merging one now would alter the output
   * of a schema verified against a published specification, so each stays as it is until
   * someone changes it deliberately rather than in passing.
   */
  private static final String SHORTBREAD_AS_IS =
    "unmerged in Shortbread and left that way: changing it alters the output of a schema " +
    "verified against a published specification, which is not something to do in passing";

  /** Merging outlines by attributes destroys the thing the layer is for. */
  private static final String BUILDINGS_STAY_INDIVIDUAL =
    "merging by attributes collapses a tile's buildings into a handful of multipolygons -- " +
    "a Monaco extract went from ~5,200 to 7 when this was tried -- and it would make " +
    "per-building extrusion heights meaningless";

  /** Joining separately named features costs the renderer the individual names. */
  private static final String LABEL_LAYER =
    "a label layer: merging would join unrelated named features into one and cost the " +
    "renderer the individual names";

  /** Layer names repeat across schemas -- both have a water_polygons -- so keys carry the schema. */
  static String key(String schema, String layer) {
    return schema + ":" + layer;
  }

  private static final String SMARTMAPS = "smartmaps";
  private static final String SHORTBREAD = "shortbread";

  private static Map<String, String> exemptions() {
    var map = new java.util.LinkedHashMap<String, String>();

    // --- SmartMaps ---
    map.put(key(SMARTMAPS, SmartMapsSchema.BUILDING), BUILDINGS_STAY_INDIVIDUAL);
    map.put(key(SMARTMAPS, SmartMapsSchema.WATER_LABEL), LABEL_LAYER);

    // --- Shortbread ---
    map.put(key(SHORTBREAD, ShortbreadSchema.BUILDINGS), BUILDINGS_STAY_INDIVIDUAL);
    map.put(key(SHORTBREAD, ShortbreadSchema.BUILDING_PARTS), BUILDINGS_STAY_INDIVIDUAL);
    map.put(
      key(SHORTBREAD, ShortbreadSchema.WATER_LINES_LABELS),
      LABEL_LAYER + ". Worth revisiting: street_labels does merge, precisely so a renderer " +
      "has a longer run to place a name along, and river labels want the same. " +
      SHORTBREAD_AS_IS
    );
    map.put(
      key(SHORTBREAD, ShortbreadSchema.WATER_POLYGONS),
      "the one non-label Shortbread polygon layer with no merge, so abutting lakes stay " +
      "separate features. SmartMaps' water_polygons does merge. " + SHORTBREAD_AS_IS
    );
    for (var layer : List.of(
      ShortbreadSchema.STREETS_POLYGONS_LABELS,
      ShortbreadSchema.WATER_POLYGONS_LABELS
    )) {
      map.put(key(SHORTBREAD, layer), LABEL_LAYER);
    }
    for (var layer : List.of(
      ShortbreadSchema.SITES,
      ShortbreadSchema.STREET_POLYGONS,
      ShortbreadSchema.BRIDGES,
      ShortbreadSchema.AERIALWAYS,
      ShortbreadSchema.PIER_LINES,
      ShortbreadSchema.PIER_POLYGONS,
      ShortbreadSchema.DAM_LINES,
      ShortbreadSchema.DAM_POLYGONS
    )) {
      map.put(key(SHORTBREAD, layer), SHORTBREAD_AS_IS);
    }
    return Map.copyOf(map);
  }

  @Test
  void everySmartMapsLayerHasAPostProcessingDecision() throws GeometryException {
    var profile = new SmartMapsProfile(SmartMapsConfiguration.defaults());
    var problems = new TreeSet<String>();
    for (var spec : SmartMapsSchema.LAYERS) {
      check(profile, SMARTMAPS, spec.name(), spec.geometries(), problems);
    }
    assertNoProblems(problems);
  }

  @Test
  void everyShortbreadLayerHasAPostProcessingDecision() throws GeometryException {
    var profile = new ShortbreadProfile(ShortbreadConfiguration.defaults(Schema.SHORTBREAD_3D));
    var problems = new TreeSet<String>();
    for (var spec : ShortbreadSchema.LAYERS) {
      check(profile, SHORTBREAD, spec.name(), Set.of(spec.geometry()), problems);
    }
    assertNoProblems(problems);
  }

  /** An exemption for a layer no schema has is a stale entry someone should delete. */
  @Test
  void everyExemptionNamesALayerThatExists() {
    var known = new TreeSet<String>();
    SmartMapsSchema.layerNames().forEach(l -> known.add(key(SMARTMAPS, l)));
    ShortbreadSchema.layerNames().forEach(l -> known.add(key(SHORTBREAD, l)));
    known.add(key(SHORTBREAD, ShortbreadSchema.BUILDING_PARTS));
    var stale = new TreeSet<>(EXEMPT.keySet());
    stale.removeAll(known);
    assertEquals(Set.of(), stale, "exemptions for layers that no longer exist");
  }

  /**
   * Feeds the layer two features that any of this project's merges would combine, and
   * reports whether the count came down.
   */
  private static void check(
    ForwardingProfile profile,
    String schema,
    String layer,
    Set<Geometry> geometries,
    TreeSet<String> problems
  ) throws GeometryException {
    // Every geometry the layer allows gets its own probe. A merged layer may post-process
    // only one of them -- `transport` merges its lines and passes its polygons and points
    // through -- and that still counts as a decision having been made.
    boolean probed = false;
    boolean merged = false;
    for (var geometry : geometries) {
      var probe = probeFor(layer, geometry);
      if (probe == null) continue; // point: nothing a merge could do
      probed = true;
      merged |= profile.postProcessLayerFeatures(layer, 14, probe).size() < probe.size();
    }
    if (!probed) {
      return;
    }
    boolean exempt = EXEMPT.containsKey(key(schema, layer));

    if (!merged && !exempt) {
      problems.add(
        key(schema, layer) + " has mergeable geometry but no post-processing. Either register it -- " +
        "if a handler writes into this layer but does not declare it as its name(), that " +
        "needs a SecondaryLayer -- or add it to EXEMPT with a reason."
      );
    }
    if (merged && exempt) {
      problems.add(
        key(schema, layer) + " is listed in EXEMPT but does merge; remove the stale entry."
      );
    }
  }

  /** Two adjacent features of the given geometry, sharing all attributes. */
  private static List<VectorTile.Feature> probeFor(String layer, Geometry geometry) {
    return switch (geometry) {
      case POLYGON -> List.of(
        feature(layer, TestUtils.rectangle(0, 0, 10, 10), 1),
        feature(layer, TestUtils.rectangle(10, 0, 20, 10), 2)
      );
      case LINE -> List.of(
        feature(layer, TestUtils.newLineString(0, 0, 10, 0), 1),
        feature(layer, TestUtils.newLineString(10, 0, 20, 0), 2)
      );
      case POINT -> null;
    };
  }

  private static VectorTile.Feature feature(
    String layer,
    org.locationtech.jts.geom.Geometry geometry,
    long id
  ) {
    return new VectorTile.Feature(
      layer,
      id,
      VectorTile.encodeGeometry(geometry),
      Map.of("kind", "probe")
    );
  }

  private static void assertNoProblems(TreeSet<String> problems) {
    assertTrue(problems.isEmpty(), String.join("\n\n", problems));
  }
}
