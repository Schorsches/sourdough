package fyi.osm.sourdough.shortbread.mapping;

import com.onthegomap.planetiler.reader.WithTags;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The `sites` layer's tag-to-kind table. Every site appears from zoom 14. */
public final class SiteKinds {

  private SiteKinds() {}

  public static final int MIN_ZOOM = 14;

  private static final Map<String, Map<String, String>> BY_KEY = build();

  private static Map<String, Map<String, String>> build() {
    var byKey = new LinkedHashMap<String, Map<String, String>>();
    byKey.put("military", Map.of("danger_area", "danger_area"));
    byKey.put("leisure", Map.of("sports_centre", "sports_centre"));
    var amenity = new LinkedHashMap<String, String>();
    amenity.put("university", "university");
    amenity.put("college", "college");
    amenity.put("school", "school");
    amenity.put("hospital", "hospital");
    amenity.put("prison", "prison");
    amenity.put("parking", "parking");
    amenity.put("bicycle_parking", "bicycle_parking");
    byKey.put("amenity", Map.copyOf(amenity));
    byKey.put("landuse", Map.of("construction", "construction"));
    return Map.copyOf(byKey);
  }

  public static final List<String> KEYS = List.of("military", "leisure", "amenity", "landuse");

  public static List<String> valuesFor(String key) {
    var values = BY_KEY.get(key);
    return values == null ? List.of() : List.copyOf(values.keySet());
  }

  /** The site kind of a feature, or null if it is not a site. */
  public static String lookup(WithTags sf) {
    for (var key : KEYS) {
      var value = sf.getString(key);
      if (value == null) continue;
      var kind = BY_KEY.get(key).get(value);
      if (kind != null) return kind;
    }
    return null;
  }
}
