package fyi.osm.sourdough.common;

/**
 * Layout of the `ref` attribute for the street label layers.
 *
 * Shortbread ships route refs pre-laid-out: a semicolon-separated OSM ref becomes one
 * ref per line, and the row and column counts describe the resulting block so that a
 * renderer can size a shield without measuring the text itself.
 */
public final class RouteRef {

  private RouteRef() {}

  /** The schema replaces semicolons in `ref` with a line feed. */
  public static final char SEPARATOR = '\n';

  /** Turns "A1;E15" into "A1\nE15". */
  public static String layout(String ref) {
    return ref.replace(';', SEPARATOR);
  }

  /** The number of lines in a laid-out ref. */
  public static int rows(String ref) {
    int rows = 1;
    for (int i = 0; i < ref.length(); i++) {
      if (ref.charAt(i) == SEPARATOR) rows++;
    }
    return rows;
  }

  /** The longest line, which is what determines how wide a shield must be. */
  public static int columns(String ref) {
    int longest = 0;
    int current = 0;
    for (int i = 0; i < ref.length(); i++) {
      if (ref.charAt(i) == SEPARATOR) {
        longest = Math.max(longest, current);
        current = 0;
      } else {
        current++;
      }
    }
    return Math.max(longest, current);
  }
}
