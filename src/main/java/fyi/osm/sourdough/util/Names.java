package fyi.osm.sourdough.util;

import com.onthegomap.planetiler.reader.SourceFeature;

/**
 * Shared resolution of OpenStreetMap language-suffixed name tags.
 *
 * This is deliberately only about *finding* values in the source data. Each schema
 * decides how to spell the resulting attribute: Sourdough emits `name:fr`, while
 * Shortbread emits `name_fr`. Keeping the lookup here means both schemas agree on
 * what a language variant is, without agreeing on how to name it.
 */
public final class Names {

  private Names() {}

  /** Returns the value of `key:language`, or null if absent (or no language given). */
  public static String localized(SourceFeature sf, String key, String language) {
    if (language == null) return null;
    return sf.getString(key + ":" + language);
  }

  /**
   * Returns the value of `key:language` if present, otherwise the value of `key`.
   * This is the substitution behavior of Sourdough's --language option.
   */
  public static String preferLanguage(SourceFeature sf, String key, String language) {
    var localized = localized(sf, key, language);
    return localized != null ? localized : sf.getString(key);
  }
}
