package fyi.osm.sourdough.smartmaps.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import fyi.osm.sourdough.common.Elevation;
import fyi.osm.sourdough.common.mapping.PoiKinds;
import fyi.osm.sourdough.smartmaps.SmartMapsConfiguration;
import fyi.osm.sourdough.smartmaps.SmartMapsLayer;
import fyi.osm.sourdough.smartmaps.SmartMapsNames;
import fyi.osm.sourdough.smartmaps.SmartMapsSchema;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SmartMaps `poi`: a curated set of points of interest, at zoom 14 only. Areas are
 * represented by a point.
 *
 * The same selection as Shortbread's `pois`, plus the three fields this layout adds:
 * `kind`, `ele` and `ele_ft`. `kind` is <strong>inferred</strong> -- the source document
 * gives the name and the type but not the vocabulary -- and is read here as the value of
 * whichever tag selected the feature, so `amenity=cafe` yields `kind=cafe`. The selecting
 * tag itself is still emitted, so nothing is lost if a style wants the pair.
 *
 * Attributes documented with a default of null or false are omitted when they hold it: an
 * absent attribute and one set to its default mean the same thing to a consumer, and
 * omitting them is what keeps this layer's share of a dense zoom-14 tile reasonable.
 */
public class Poi extends SmartMapsLayer {

  public Poi(SmartMapsConfiguration config) {
    super(config);
  }

  /** Attributes carried only by particular kinds of POI. */
  private static final Map<String, Set<String>> CONDITIONAL_STRINGS = Map.of(
    "cuisine", Set.of("restaurant", "fast_food", "pub", "bar", "cafe"),
    "vending", Set.of("vending_machine"),
    "religion", Set.of("place_of_worship"),
    "denomination", Set.of("place_of_worship")
  );

  private static final List<String> RECYCLING_ATTRIBUTES = List.of(
    "recycling:glass_bottles",
    "recycling:paper",
    "recycling:clothes",
    "recycling:scrap_metal"
  );

  @Override
  public String name() {
    return SmartMapsSchema.POI;
  }

  @Override
  public Expression filter() {
    return Expression.or(
      PoiKinds.KEYS.stream()
        .map(key -> Expression.matchAny(key, PoiKinds.valuesFor(key)))
        .toArray(Expression[]::new)
    );
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    if (!PoiKinds.isPoi(sf)) return;

    FeatureCollector.Feature point;
    if (sf.isPoint()) {
      point = fc.point(name());
    } else if (sf.canBePolygon()) {
      point = fc.pointOnSurface(name());
    } else {
      return;
    }

    point.setMinZoom(PoiKinds.MIN_ZOOM);
    point.setBufferPixels(16);

    String kind = null;
    for (var key : PoiKinds.KEYS) {
      var value = PoiKinds.selectedValue(sf, key);
      if (value != null) {
        point.setAttr(key, value);
        // KEYS is ordered by precedence, so the first match is the primary one.
        if (kind == null) kind = value;
      }
    }
    if (kind != null) {
      point.setAttr("kind", kind);
    }

    SmartMapsNames.setNames(sf, point, config.languages());
    setIfPresent(sf, point, "addr:housename", "housename");
    setIfPresent(sf, point, "addr:housenumber", "housenumber");

    var meters = Elevation.meters(sf);
    if (meters != null) {
      point.setAttr("ele", meters);
      point.setAttr("ele_ft", Elevation.feet(meters));
    }

    setConditionalAttributes(sf, point);
  }

  private void setConditionalAttributes(SourceFeature sf, FeatureCollector.Feature point) {
    var amenity = sf.getString("amenity");
    var leisure = sf.getString("leisure");

    CONDITIONAL_STRINGS.forEach((attribute, amenities) -> {
      if (amenity != null && amenities.contains(amenity)) {
        setIfPresent(sf, point, attribute, attribute);
      }
    });

    if (leisure != null && (leisure.equals("pitch") || leisure.equals("sports_centre"))) {
      setIfPresent(sf, point, "sport", "sport");
    }
    if (sf.hasTag("tourism", "information")) {
      setIfPresent(sf, point, "information", "information");
    }
    if (sf.hasTag("man_made", "tower")) {
      setIfPresent(sf, point, "tower:type", "tower:type");
    }
    if ("recycling".equals(amenity)) {
      for (var attribute : RECYCLING_ATTRIBUTES) {
        if (sf.hasTag(attribute, "yes")) {
          point.setAttr(attribute, true);
        }
      }
    }
    if ("bank".equals(amenity) && sf.hasTag("atm", "yes")) {
      point.setAttr("atm", true);
    }
  }

  private static void setIfPresent(
    SourceFeature sf,
    FeatureCollector.Feature feature,
    String osmKey,
    String attribute
  ) {
    var value = sf.getString(osmKey);
    if (value != null) {
      feature.setAttr(attribute, value);
    }
  }
}
