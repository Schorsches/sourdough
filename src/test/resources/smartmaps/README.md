# Vendored SmartMaps TileJSON

`tiles.json` is the TileJSON for the "SmartMaps Planet" tileset, as supplied. It is
vendored so `TileJsonConformanceTest` can check this implementation's layer and field
names against the document itself rather than against a transcription of it.

Two caveats govern how the tests use it.

**It defines shape, not content.** It gives layer names, field names and field types, and
nothing else: no OSM selection rules and no `kind` vocabularies. This implementation is
therefore shape-compatible; the classification behind the fields is our own.

**Its contents are observed, not declared.** Language coverage varies per layer in a way
nobody configures by hand (41 `name:xx` codes on `place_label`, 8 on `building`, 2 on
`landcover`), and the zoom ranges contradict each other: `water_polygons` is z14 only
while `water_label`, which labels those polygons, runs z0-14. Field lists and zoom ranges
are a lower bound, so the tests treat the field list as a set that must be covered, and
ignore the per-layer zoom ranges. See SMARTMAPS_SCHEMA.md.

The `attribution` and `logo` fields are SmartMaps' own and are deliberately not reproduced
in generated tiles; our output carries OpenStreetMap attribution.
