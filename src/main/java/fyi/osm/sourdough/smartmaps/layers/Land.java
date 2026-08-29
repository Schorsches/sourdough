package fyi.osm.sourdough.smartmaps.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import fyi.osm.sourdough.common.Elevation;
import fyi.osm.sourdough.common.WayArea;
import fyi.osm.sourdough.common.mapping.LandKinds;
import fyi.osm.sourdough.common.mapping.SiteKinds;
import fyi.osm.sourdough.smartmaps.SmartMapsConfiguration;
import fyi.osm.sourdough.smartmaps.SmartMapsLayer;
import fyi.osm.sourdough.smartmaps.SmartMapsNames;
import fyi.osm.sourdough.smartmaps.SmartMapsSchema;
import java.util.List;

/**
 * SmartMaps `landcover` and `landuse`.
 *
 * One handler for both, because they are one classification split two ways; see
 * {@link LandKindRouting}. Sites -- schools, hospitals, car parks -- are land use, so they
 * join `landuse` rather than getting a layer of their own as they do in Shortbread.
 *
 * Both layers carry the passthrough tags the layout lists (`amenity`, `leisure`,
 * `tourism`, `sport`, `landuse`), which is what lets a style distinguish features that
 * share a `kind`.
 */
public class Land extends SmartMapsLayer implements ForwardingProfile.LayerPostProcessor {

  public Land(SmartMapsConfiguration config) {
    super(config);
  }

  /** Sites are not in the land tables and are all available from this zoom. */
  private static final int SITE_MIN_ZOOM = 14;

  @Override
  public String name() {
    return SmartMapsSchema.LANDUSE;
  }

  @Override
  public Expression filter() {
    var expressions = new java.util.ArrayList<Expression>();
    for (var key : LandKinds.KEYS) {
      expressions.add(Expression.matchAny(key, LandKinds.valuesFor(key)));
    }
    for (var key : SiteKinds.KEYS) {
      expressions.add(Expression.matchAny(key, SiteKinds.valuesFor(key)));
    }
    return Expression.or(expressions);
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    if (!sf.canBePolygon()) return;

    var land = LandKinds.lookup(sf);
    if (land != null) {
      emit(sf, fc, LandKindRouting.layerFor(land.kind()), land.kind(), land.minZoom());
      return;
    }
    var site = SiteKinds.lookup(sf);
    if (site != null) {
      emit(sf, fc, SmartMapsSchema.LANDUSE, site, SITE_MIN_ZOOM);
    }
  }

  private void emit(SourceFeature sf, FeatureCollector fc, String layer, String kind, int minZoom) {
    var polygon = fc.polygon(layer);
    polygon.setMinZoom(minZoom);
    polygon.setMinPixelSize(2.0);
    polygon.setPixelTolerance(0.25);
    polygon.setBufferPixels(8);
    polygon.setAttr("kind", kind);

    var wayArea = WayArea.squareMeters(sf);
    if (wayArea != null) {
      polygon.setAttr("way_area", wayArea);
    }
    SmartMapsNames.setNames(sf, polygon, config.languages());

    // Passthrough tags, so a style can tell apart features sharing a kind.
    setIfPresent(sf, polygon, "boundary");
    if (sf.hasTag("maritime", "yes")) {
      polygon.setAttr("maritime", true);
    }
    if (layer.equals(SmartMapsSchema.LANDUSE)) {
      for (var key : List.of("amenity", "landuse", "leisure", "tourism", "sport")) {
        setIfPresent(sf, polygon, key);
      }
      setIfPresent(sf, polygon, "addr:housenumber", "housenumber");
      var meters = Elevation.meters(sf);
      if (meters != null) {
        polygon.setAttr("ele", meters);
        polygon.setAttr("ele_ft", Elevation.feet(meters));
      }
    }
  }

  private static void setIfPresent(SourceFeature sf, FeatureCollector.Feature f, String key) {
    setIfPresent(sf, f, key, key);
  }

  private static void setIfPresent(
    SourceFeature sf,
    FeatureCollector.Feature f,
    String osmKey,
    String attribute
  ) {
    var value = sf.getString(osmKey);
    if (value != null) f.setAttr(attribute, value);
  }

  /**
   * The merge both land layers get. `landcover` is not this handler's declared layer, so
   * it is registered separately as a {@link fyi.osm.sourdough.smartmaps.SecondaryLayer};
   * without that it would receive no post-processing at all.
   */
  public static List<VectorTile.Feature> merge(int zoom, List<VectorTile.Feature> items)
    throws GeometryException {
    return FeatureMerge.mergeNearbyPolygons(items, 3.0, 3.0, 0.5, 0.5);
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items)
    throws GeometryException {
    return merge(zoom, items);
  }
}
