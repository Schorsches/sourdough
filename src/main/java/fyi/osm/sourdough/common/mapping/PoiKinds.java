package fyi.osm.sourdough.common.mapping;

import com.onthegomap.planetiler.reader.WithTags;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The curated set of OpenStreetMap key-value combinations that Shortbread's `pois` layer
 * carries, transcribed from the pinned specification.
 *
 * The selection is deliberately narrow: a POI is included only when its key carries one
 * of the values listed here, so this is a lookup table rather than a set of rules. Future
 * Shortbread versions add entries to these lists and nothing else needs to change.
 */
public final class PoiKinds {

  private PoiKinds() {}

  /** Every POI is available at zoom 14 and no lower. */
  public static final int MIN_ZOOM = 14;

  private static final Set<String> AMENITY = Set.of(
    "arts_centre", "atm", "bank", "bar", "bench", "bicycle_rental", "biergarten", "cafe",
    "car_rental", "car_sharing", "car_wash", "cinema", "clinic", "college", "community_centre",
    "courthouse", "dentist", "doctors", "drinking_water", "embassy", "fast_food",
    "fire_station", "food_court", "fountain", "fuel", "grave_yard", "hospital", "hunting_stand",
    "library", "marketplace", "nightclub", "nursing_home", "pharmacy", "place_of_worship",
    "police", "post_box", "post_office", "prison", "pub", "public_building", "recycling",
    "restaurant", "school", "shelter", "telephone", "theatre", "toilets", "townhall",
    "university", "vending_machine", "veterinary", "waste_basket"
  );

  private static final Set<String> LEISURE = Set.of(
    "dog_park", "golf_course", "ice_rink", "park", "pitch", "playground", "sports_centre",
    "stadium", "swimming_pool", "water_park"
  );

  private static final Set<String> TOURISM = Set.of(
    "artwork", "alpine_hut", "bed_and_breakfast", "camp_site", "caravan_site", "chalet",
    "guest_house", "hostel", "hotel", "information", "motel", "picnic_site", "theme_park",
    "viewpoint", "zoo"
  );

  private static final Set<String> SHOP = Set.of(
    "alcohol", "bakery", "beauty", "beverages", "bicycle", "books", "butcher", "car", "chemist",
    "clothes", "computer", "convenience", "department_store", "doityourself", "dry_cleaning",
    "florist", "furniture", "garden_centre", "general", "gift", "greengrocer", "hairdresser",
    "hardware", "jewelry", "kiosk", "laundry", "mall", "mobile_phone", "newsagent", "optician",
    "outdoor", "shoes", "sports", "stationery", "supermarket", "toys", "travel_agency", "video"
  );

  private static final Set<String> MAN_MADE = Set.of(
    "lighthouse", "surveillance", "tower", "wastewater_plant", "water_well", "water_works",
    "watermill", "windmill"
  );

  private static final Set<String> HISTORIC = Set.of(
    "archaeological_site", "battlefield", "castle", "fort", "memorial", "monument", "ruins",
    "wayside_cross", "wayside_shrine"
  );

  private static final Set<String> EMERGENCY = Set.of(
    "defibrillator", "fire_hydrant", "phone"
  );

  private static final Set<String> HIGHWAY = Set.of(
    "emergency_access_point"
  );

  private static final Set<String> OFFICE = Set.of(
    "diplomatic"
  );

  /**
   * The key properties, in the order the specification lists them. A POI feature sets
   * each of these whose OSM value is in the corresponding set above.
   */
  private static final Map<String, Set<String>> SELECTED = buildSelected();

  private static Map<String, Set<String>> buildSelected() {
    var map = new LinkedHashMap<String, Set<String>>();
    map.put("amenity", AMENITY);
    map.put("leisure", LEISURE);
    map.put("tourism", TOURISM);
    map.put("shop", SHOP);
    map.put("man_made", MAN_MADE);
    map.put("historic", HISTORIC);
    map.put("emergency", EMERGENCY);
    map.put("highway", HIGHWAY);
    map.put("office", OFFICE);
    return Map.copyOf(map);
  }

  /** The OSM keys the layer selects on, in specification order. */
  public static final List<String> KEYS = List.copyOf(SELECTED.keySet());

  /** The selected values for a key, for building the profile's tag filter. */
  public static List<String> valuesFor(String key) {
    return List.copyOf(SELECTED.getOrDefault(key, Set.of()));
  }

  /** True when this feature carries at least one selected key-value combination. */
  public static boolean isPoi(WithTags sf) {
    for (var entry : SELECTED.entrySet()) {
      var value = sf.getString(entry.getKey());
      if (value != null && entry.getValue().contains(value)) return true;
    }
    return false;
  }

  /** The value to emit for a key property, or null when it is not a selected value. */
  public static String selectedValue(WithTags sf, String key) {
    var values = SELECTED.get(key);
    if (values == null) return null;
    var value = sf.getString(key);
    return value != null && values.contains(value) ? value : null;
  }
}
