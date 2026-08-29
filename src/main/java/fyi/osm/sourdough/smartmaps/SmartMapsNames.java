package fyi.osm.sourdough.smartmaps;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.reader.SourceFeature;
import java.util.List;

/**
 * Name attributes for the SmartMaps layout.
 *
 * `name` is the OpenStreetMap name tag verbatim. Language variants are emitted under
 * <em>both</em> spellings the reference tileset uses -- `name:de` and `name_de` -- because
 * a style written against it may read either. Both come from the same `name:xx` tag, so
 * carrying both costs one extra attribute per language on named features and removes a
 * whole class of "why is this label empty" question for consumers.
 *
 * Nothing is emitted for a language the feature does not have, and no languages at all are
 * emitted unless asked for with --additional-languages.
 */
public final class SmartMapsNames {

  private SmartMapsNames() {}

  /** The colon spelling, as OpenStreetMap writes it. */
  public static String colonAttribute(String languageCode) {
    return "name:" + languageCode;
  }

  /** The underscore spelling, which several styles expect instead. */
  public static String underscoreAttribute(String languageCode) {
    return "name_" + languageCode;
  }

  /** Sets `name` and both spellings of every configured language present on the feature. */
  public static void setNames(
    SourceFeature sf,
    FeatureCollector.Feature feature,
    List<String> languages
  ) {
    var name = sf.getString("name");
    if (name != null) {
      feature.setAttr("name", name);
    }
    for (var language : languages) {
      var value = sf.getString("name:" + language);
      if (value != null) {
        feature.setAttr(colonAttribute(language), value);
        feature.setAttr(underscoreAttribute(language), value);
      }
    }
  }
}
