/**
 * How this project classifies OpenStreetMap features into `kind` values.
 *
 * These vocabularies originate with the Shortbread specification, which is where they were
 * first needed. They live here rather than in that schema's package because the SmartMaps
 * layout reuses them by design: its TileJSON names fields and types but defines no
 * vocabularies of its own, so the classification behind them is this project's, shared
 * between both schemas. See SMARTMAPS_SCHEMA.md.
 *
 * Layer names are not part of this: which layer a classified feature ends up in is a
 * schema's decision, and the two schemas route them differently.
 */
package fyi.osm.sourdough.common.mapping;
