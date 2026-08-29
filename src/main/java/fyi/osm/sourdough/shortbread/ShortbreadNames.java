package fyi.osm.sourdough.shortbread;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.reader.SourceFeature;
import java.util.List;

/**
 * Shortbread's name attributes.
 *
 * `name` is the OSM `name` tag verbatim: the specification names that key as its source,
 * so Sourdough's --language substitution deliberately does not apply here. Language
 * variants are emitted as `name_xx`, keeping any IETF subtags intact (`name_ko-Latn`).
 */
public final class ShortbreadNames {

  private ShortbreadNames() {}

  /** The Shortbread attribute name for an OSM `name:xx` tag. */
  public static String attributeFor(String languageCode) {
    return "name_" + languageCode;
  }

  /** Sets `name` and any configured `name_xx` attributes present on the feature. */
  public static void setNames(
    SourceFeature sf,
    FeatureCollector.Feature feature,
    List<String> languages
  ) {
    var name = sf.getString("name");
    if (name != null) {
      feature.setAttr("name", name);
    }
    setLanguageNames(sf, feature, languages);
  }

  /** Sets only the `name_xx` attributes, for layers that source `name` differently. */
  public static void setLanguageNames(
    SourceFeature sf,
    FeatureCollector.Feature feature,
    List<String> languages
  ) {
    for (var language : languages) {
      var value = sf.getString("name:" + language);
      if (value != null) {
        feature.setAttr(attributeFor(language), value);
      }
    }
  }
}
