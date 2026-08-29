package fyi.osm.sourdough.common;

/**
 * The vocabulary a schema uses to describe itself.
 *
 * Each schema keeps its own layer table, because what a layer may contain is part of that
 * schema's definition -- Shortbread allows exactly one geometry type per layer, SmartMaps
 * merges layers and so does not. What they share is the alphabet those tables are written
 * in, which is here so it is defined once.
 */
public final class SchemaDescription {

  private SchemaDescription() {}

  /** The geometry a tile feature carries. */
  public enum Geometry {
    POINT,
    LINE,
    POLYGON
  }

  /** The MVT value types these schemas use. */
  public enum AttrType {
    STRING,
    INTEGER,
    FLOAT,
    BOOLEAN
  }
}
