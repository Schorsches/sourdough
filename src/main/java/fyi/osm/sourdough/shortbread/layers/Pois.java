package fyi.osm.sourdough.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadLayer;
import fyi.osm.sourdough.shortbread.ShortbreadNames;
import fyi.osm.sourdough.shortbread.ShortbreadSchema;
import fyi.osm.sourdough.common.mapping.PoiKinds;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shortbread `pois`: a deliberately curated set of points of interest, at zoom 14 only.
 *
 * Areas are represented by a point. Attributes that the schema documents with a default
 * of null or false are omitted when they hold that default: a missing attribute and an
 * attribute set to its default mean the same thing to a consumer, and omitting them is
 * what keeps this layer's share of a dense zoom-14 tile reasonable.
 */
public class Pois extends ShortbreadLayer {

  public Pois(ShortbreadConfiguration config) {
    super(config);
  }

  @Override
  public String name() {
    return ShortbreadSchema.POIS;
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

    for (var key : PoiKinds.KEYS) {
      var value = PoiKinds.selectedValue(sf, key);
      if (value != null) {
        point.setAttr(key, value);
      }
    }

    ShortbreadNames.setNames(sf, point, config.languages());
    setIfPresent(sf, point, "addr:housename", "housename");
    setIfPresent(sf, point, "addr:housenumber", "housenumber");

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
