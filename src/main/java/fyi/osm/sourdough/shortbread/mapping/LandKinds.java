package fyi.osm.sourdough.shortbread.mapping;

import com.onthegomap.planetiler.reader.WithTags;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The `land` layer's tag-to-kind table.
 *
 * The schema draws this layer's values from four unrelated OSM keys, so the table is
 * keyed by (key, value) rather than by value alone. Keys are checked in the order below,
 * which matters only for objects carrying more than one of them.
 */
public final class LandKinds {

  private LandKinds() {}

  /** @param kind the Shortbread `kind` value, @param minZoom the lowest zoom it appears at */
  public record Land(String kind, int minZoom) {}

  private static final Map<String, Map<String, Land>> BY_KEY = build();

  private static Map<String, Map<String, Land>> build() {
    var byKey = new LinkedHashMap<String, Map<String, Land>>();

    var landuse = new LinkedHashMap<String, Land>();
    landuse.put("forest", new Land("forest", 7));
    landuse.put("residential", new Land("residential", 10));
    landuse.put("industrial", new Land("industrial", 10));
    landuse.put("commercial", new Land("commercial", 10));
    landuse.put("garages", new Land("garages", 10));
    landuse.put("retail", new Land("retail", 10));
    landuse.put("railway", new Land("railway", 10));
    landuse.put("landfill", new Land("landfill", 10));
    landuse.put("brownfield", new Land("brownfield", 10));
    landuse.put("greenfield", new Land("greenfield", 10));
    landuse.put("farmyard", new Land("farmyard", 10));
    landuse.put("farmland", new Land("farmland", 10));
    landuse.put("grass", new Land("grass", 11));
    landuse.put("meadow", new Land("meadow", 11));
    landuse.put("orchard", new Land("orchard", 11));
    landuse.put("vineyard", new Land("vineyard", 11));
    landuse.put("allotments", new Land("allotments", 11));
    landuse.put("village_green", new Land("village_green", 11));
    landuse.put("recreation_ground", new Land("recreation_ground", 11));
    landuse.put("greenhouse_horticulture", new Land("greenhouse_horticulture", 11));
    landuse.put("plant_nursery", new Land("plant_nursery", 11));
    landuse.put("quarry", new Land("quarry", 11));
    landuse.put("cemetery", new Land("cemetery", 13));
    byKey.put("landuse", Map.copyOf(landuse));

    var natural = new LinkedHashMap<String, Land>();
    natural.put("wood", new Land("forest", 7));
    natural.put("sand", new Land("sand", 10));
    natural.put("beach", new Land("beach", 10));
    natural.put("heath", new Land("heath", 11));
    natural.put("scrub", new Land("scrub", 11));
    natural.put("grassland", new Land("grassland", 11));
    natural.put("bare_rock", new Land("bare_rock", 11));
    natural.put("scree", new Land("scree", 11));
    natural.put("shingle", new Land("shingle", 11));
    byKey.put("natural", Map.copyOf(natural));

    var wetland = new LinkedHashMap<String, Land>();
    wetland.put("swamp", new Land("swamp", 11));
    wetland.put("bog", new Land("bog", 11));
    wetland.put("string_bog", new Land("string_bog", 11));
    wetland.put("wet_meadow", new Land("wet_meadow", 11));
    wetland.put("marsh", new Land("marsh", 11));
    byKey.put("wetland", Map.copyOf(wetland));

    var leisure = new LinkedHashMap<String, Land>();
    leisure.put("golf_course", new Land("golf_course", 11));
    leisure.put("park", new Land("park", 11));
    leisure.put("garden", new Land("garden", 11));
    leisure.put("playground", new Land("playground", 11));
    leisure.put("miniature_golf", new Land("miniature_golf", 11));
    byKey.put("leisure", Map.copyOf(leisure));

    byKey.put("amenity", Map.of("grave_yard", new Land("grave_yard", 13)));

    return Map.copyOf(byKey);
  }

  /** The OSM keys this layer selects on. */
  public static final List<String> KEYS = List.of(
    "landuse",
    "natural",
    "wetland",
    "leisure",
    "amenity"
  );

  /** All values selected for a given key, for building the profile's tag filter. */
  public static List<String> valuesFor(String key) {
    var values = BY_KEY.get(key);
    return values == null ? List.of() : List.copyOf(values.keySet());
  }

  /** The land classification of a feature, or null if it is not land cover. */
  public static Land lookup(WithTags sf) {
    for (var key : KEYS) {
      var value = sf.getString(key);
      if (value == null) continue;
      var land = BY_KEY.get(key).get(value);
      if (land != null) return land;
    }
    return null;
  }
}
