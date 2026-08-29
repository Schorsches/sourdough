package fyi.osm.sourdough.shortbread;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Named language lists for Shortbread's `name_xx` attributes.
 *
 * Shortbread 1.1 leaves the set of languages up to the implementation, and every extra
 * language is another string attribute on every named label feature, so the default is
 * empty. A preset exists so that operators who do want a broad multilingual tileset do
 * not have to type out several dozen codes.
 */
public final class LanguagePresets {

  private LanguagePresets() {}

  /**
   * The language set used by SmartMaps Planet: the 24 official EU languages plus
   * Luxembourgish, eight widely used non-Latin-script languages, and Latin
   * transliterations of the non-Latin ones.
   */
  public static final List<String> SMARTMAPS = List.of(
    // 24 official EU languages
    "bg", "cs", "da", "de", "el", "en", "es", "et", "fi", "fr", "ga", "hr",
    "hu", "it", "lt", "lv", "mt", "nl", "pl", "pt", "ro", "sk", "sl", "sv",
    // additional European language
    "lb",
    // widely used non-Latin-script languages
    "ar", "hi", "ja", "ko", "ru", "tr", "uk", "zh",
    // Latin transliterations
    "ar-Latn", "bg-Latn", "el-Latn", "hi-Latn", "ja-Latn", "ko-Latn",
    "ru-Latn", "uk-Latn", "zh-Latn"
  );

  private static final Map<String, List<String>> PRESETS = Map.of("smartmaps", SMARTMAPS);

  /**
   * Expands preset names in a --additional-languages value. Entries that are not preset
   * names are treated as literal IETF language codes, so presets and explicit codes can
   * be mixed. Order is preserved and duplicates are dropped.
   */
  public static List<String> resolve(List<String> requested) {
    if (requested == null || requested.isEmpty()) return List.of();
    var resolved = new LinkedHashSet<String>();
    for (var entry : requested) {
      var trimmed = entry.trim();
      if (trimmed.isEmpty()) continue;
      var preset = PRESETS.get(trimmed.toLowerCase(java.util.Locale.ROOT));
      if (preset != null) {
        resolved.addAll(preset);
      } else {
        resolved.add(trimmed);
      }
    }
    return List.copyOf(new ArrayList<>(resolved));
  }

  public static String presetNames() {
    return String.join(", ", PRESETS.keySet());
  }
}
