# SmartMaps layout support

This repository can generate a fourth tileset whose layers and fields follow the
**SmartMaps Planet** layout, alongside its own Sourdough schema and the two Shortbread
schemas.

```bash
mvn package
java -jar target/sourdough-builder-HEAD-with-deps.jar \
  --download --area luxembourg --schema smartmaps --output smartmaps.pmtiles
```

## Read this first: shape-compatible, not the SmartMaps tileset

**This is not SmartMaps data, and this project is not affiliated with SmartMaps.** What it
produces is a tileset with the *shape* of the SmartMaps layout: a style written against
that layout finds every layer it expects, every field it expects, and each field with the
type it expects. The classification *behind* those fields is this project's own, derived
from the Shortbread rules already implemented here.

Concretely: `poi` will exist, will be a point layer, and will carry `kind`, `name`,
`amenity`, `ele`, `ele_ft` and the rest. Whether a particular café comes out as
`kind=cafe` here and as something else in the real tileset is not guaranteed, because the
source document does not say. Every `kind` this implementation emits is listed below, so
reconciling a vocabulary against real SmartMaps tiles is a data change rather than an
investigation.

Output carries OpenStreetMap attribution. No SmartMaps branding, attribution or naming is
emitted.

## What the source document does and does not define

The layout is transcribed from a TileJSON for the SmartMaps Planet tileset, vendored at
`src/test/resources/smartmaps/tiles.json` with provenance notes in
`src/test/resources/smartmaps/README.md`.

It gives **layer names, field names, field types and zoom ranges**. It gives no OSM
selection rules and no `kind` vocabularies.

It is also plainly **observed from sampled tiles rather than declared**, which is the
single most important thing about it:

- Language coverage varies per layer in a way nobody configures by hand: `place_label`
  carries 41 `name:xx` codes, `building` 8, `landcover` 2. Those are the languages that
  happened to occur in whatever tiles were sampled.
- Its zoom ranges contradict each other. `water_polygons` is z14-only while `water_label`
   — the labels *for those polygons* — runs z0–14. `transport` is z14-only while
  `transport_label` runs z8–14. Labels floating above no geometry is not a design; it is
  a sampling gap.

So the field lists are treated as a **lower bound** on the real layout, and the zoom
ranges as informational. Zooms here follow the Shortbread rules instead, which are already
verified against a published specification and are internally coherent.

## Layers

Twelve layers, declared once in
`src/main/java/fyi/osm/sourdough/smartmaps/SmartMapsSchema.java` and checked from two
directions:

- `TileJsonConformanceTest` checks the table against the vendored TileJSON — that the
  layout as transcribed is the layout as published.
- `SmartMapsConformanceTest` checks the profile against the table — that what the code
  emits is the layout as transcribed.

Only both together say the output is shape-compatible.

| Layer | Geometry | From | Notes |
|---|---|---|---|
| `place_label` | point | Shortbread `place_labels` | identical fields |
| `boundary` | line | Shortbread `boundaries` | identical fields; shares the relation rules |
| `housenumber_label` | point | Shortbread `addresses` | rename only |
| `water_lines` | line | Shortbread `water_lines` | adds `intermittent` |
| `water_polygons` | polygon | Shortbread `water_polygons` **and `ocean`** | adds `intermittent`, `name`, `way_area` |
| `water_label` | point + line | Shortbread's two water label layers | merged into one |
| `landcover` | polygon | natural-side `land` kinds | adds `boundary`, `maritime`, `name`, `way_area` |
| `landuse` | polygon | landuse-side `land` kinds + `sites` | adds passthrough tags, `ele`, `ele_ft`, `housenumber` |
| `transport` | line + polygon + point | Shortbread `streets`, `street_polygons`, `public_transport` | merged; see below |
| `transport_label` | line + point | Shortbread `street_labels`, `street_labels_points` | merged |
| `poi` | point | Shortbread `pois` | adds `kind`, `ele`, `ele_ft` |
| `building` | polygon | Shortbread `buildings` **and `building_parts`** | merged behind a flag; carries 3D |

## Deviations, and why

Every one of these is a case where following the document literally would produce a worse
tileset, or where the document is silent.

### The sea is in `water_polygons` as `kind=ocean`

The layout has no ocean layer. Taken literally that drops coastline polygons entirely and
leaves the sea unpainted at every zoom. Since the sampled `water_polygons` is z14-only, it
is likely the sample simply never included an ocean tile.

Ocean polygons from the preprocessed coastline shapefile are therefore emitted into
`water_polygons` with `kind=ocean`, which is where a consumer would look for them.

### Zoom ranges come from Shortbread, not from the document

For the reasons in the section above. A style relying on the document's ranges will find
*more* data at low zoom than it expected, never less.

### `transport` carries three geometry types

Shortbread splits the movement network across `streets` (lines), `street_polygons`
(areas) and `public_transport` (stops as points). This layout has one `transport` layer,
and the `station`, `iata` and `icao` fields in it only make sense for stops — which is
what settles that stops belong here and not in `poi`.

The layer's post-processing merges line strings; points and polygons pass through
untouched. `MergedLayerPostProcessTest` pins that, because a merge that quietly dropped
them would delete every station and every pedestrian square while leaving a well-formed
layer behind.

### `transport` carries ways under construction

Shortbread omits them entirely. This layout has a `construction` string field, so they are
carried: `kind` names the class the way *will* be (`construction=residential` gives
`kind=residential`), `construction` names the same value so a style can tell it apart, and
the minimum zoom is at least 12 — a road being built is not a road yet.

### Only two access fields

The layout has `bicycle` and `horse`, and no `motorcar` or `foot`. Only those two are
evaluated. Values are normalized to `yes` / `limited` / `no` by the shared Shortbread
rules, so `horse=designated` reads as `yes`.

### Shortbread layers with no counterpart

`dam_lines`, `dam_polygons`, `pier_lines`, `pier_polygons`, `bridges`, `aerialways`,
`ferries` and `boundary_labels` have no SmartMaps layer. Aerialway *stations* survive in
`transport` as `kind=aerialway_station`, and ferry terminals as `kind=ferry_terminal`.
The rest are dropped. Nothing in the layout would carry them without inventing a `kind`
the document does not mention.

### Buildings and parts share one layer

The opposite choice from `shortbread-1.1-3d`, and deliberately so. See
[BUILDINGS_3D.md](BUILDINGS_3D.md#why-smartmaps-merges-parts-and-shortbread-does-not).

### Estimated heights are on by default

The layout has no factual height field — only `render_height`, whose value exists to be
extruded rather than to be believed. So buildings with no dimensions in OpenStreetMap get
a height estimated from their building type, and there is no `height_estimated` flag to
mark them (Shortbread's 3D extension has one because it *does* claim to state facts).
What each height was derived from is counted and reported in the build log.

## Inferred attributes

The document names these and says nothing else about them. Each is one method to change if
real SmartMaps tiles turn out to mean something different.

| Attribute | Read as | Where |
|---|---|---|
| `kind` on `poi` | the value of whichever tag selected the feature — `amenity=cafe` gives `kind=cafe`. The selecting tag is still emitted alongside it | `layers/Poi.java` |
| `kind` on `landuse` / `landcover` | the Shortbread `land` or `sites` kind | `layers/Land.java` |
| `ref_prefix` | the leading alphabetic part of the first ref — `A` from `A1;E15`. Null for a bare exit number | `layers/TransportLabel.java` |
| `ref_org` | the route's `network` value | `layers/TransportLabel.java` |
| `3d` | the feature carries Simple 3D Buildings information — an explicit height, level count, min height, part flag or roof tag. An *estimated* height does not qualify; that would set the flag on every building on the planet | `layers/Building.java` |
| `roof` | the roof has a shape, height or level count worth modelling | `layers/Building.java` |
| `ele_ft` | `ele` converted to feet and rounded to whole feet; `ele` stays metres. Implausible elevations (outside −500 m … 9000 m) are dropped rather than clamped | `common/Elevation.java` |
| `construction` | what is being built, from the `construction` tag | `layers/Transport.java` |

## Which kinds are emitted

`landcover` — `bare_rock`, `beach`, `bog`, `forest`, `grass`, `grassland`, `heath`,
`marsh`, `meadow`, `sand`, `scree`, `scrub`, `shingle`, `string_bog`, `swamp`,
`wet_meadow`.

Note `natural=wood` and `landuse=forest` both give `forest`, and the split between the two
land layers is stated explicitly in `layers/LandKindRouting.java` rather than inferred
from which OSM key a kind came from — the keys do not line up with the distinction.
`SmartMapsLayerTest` checks the routing table only names kinds the land table can
actually produce, so a typo fails the build instead of silently routing to `landuse`.

`landuse` — every other land kind (`residential`, `industrial`, `commercial`, `retail`,
`garages`, `railway`, `landfill`, `brownfield`, `greenfield`, `farmyard`, `farmland`,
`orchard`, `vineyard`, `allotments`, `village_green`, `recreation_ground`,
`greenhouse_horticulture`, `plant_nursery`, `quarry`, `cemetery`, `golf_course`, `park`,
`garden`, `playground`, `miniature_golf`, `grave_yard`) plus the site kinds
(`school`, `college`, `university`, `hospital`, `prison`, `parking`, `bicycle_parking`,
`sports_centre`, `danger_area`, `construction`).

`water_polygons` and `water_label` — `ocean`, `water`, `river`, `reservoir`, `basin`,
`dock`, `canal`, `glacier`.

`water_lines` — `river`, `canal`, `stream`, `drain`, `ditch`.

`transport` — the road classes (`motorway`, `trunk`, `primary`, `secondary`, `tertiary`,
`unclassified`, `residential`, `living_street`, `service`, `pedestrian`, `track`,
`footway`, `steps`, `path`, `cycleway`, `busway`, `bus_guideway`), the rail classes
(`rail`, `narrow_gauge`, `tram`, `light_rail`, `funicular`, `subway`, `monorail`), the
aeroway classes (`runway`, `taxiway`), and the stop kinds (`aerodrome`, `helipad`,
`ferry_terminal`, `bus_station`, `station`, `halt`, `tram_stop`, `aerialway_station`,
`bus_stop`). Link roads are the parent class plus `link=true`.

`transport_label` — the same road and rail classes, with link roads spelled out
(`motorway_link` and so on), plus `motorway_junction`.

`place_label` — `capital`, `state_capital`, `city`, `town`, `village`, `hamlet`,
`suburb`, `quarter`, `neighbourhood`, `isolated_dwelling`, `farm`, `island`, `locality`.

`poi` — the Shortbread POI vocabulary: 137 values across `amenity`, `shop`, `tourism`,
`leisure`, `historic`, `man_made`, `emergency`, `office` and `highway`, listed in
`common/mapping/PoiKinds.java`.

`building` — no `kind` field; buildings are distinguished by `building:part` and the
passthrough tags.

## Names and languages

`name` is the OpenStreetMap `name` tag, verbatim. Sourdough's `--language` substitution
does not apply.

Language variants are emitted under **both** spellings the reference tileset uses:

```bash
# an explicit list, in both forms
--additional-languages de,fr

# or the preset: 43 codes, being the 24 official EU languages plus Luxembourgish, eight
# widely used non-Latin-script languages, and Latin transliterations of those
--additional-languages smartmaps
```

`--additional-languages de` emits `name:de` *and* `name_de`, both from the OSM `name:de`
tag. Carrying both costs one extra attribute per language on named features and removes a
whole class of "why is this label empty" question for a style written against either
convention. MVT dictionary-encodes values, so the duplicated value itself is nearly free;
the key is the cost.

Nothing is emitted for a language a feature does not have, and **no languages at all** are
emitted unless asked for. Every extra language is another string attribute on every named
label feature.

## Measured output

Monaco, built from the same extract as the other schemas
(`mvn test -DexcludedTestGroups= -Dtest=ShortbreadIntegrationTest`):

| | archive on disk | features | z14 total | largest z14 tile |
|---|---|---|---|---|
| `shortbread-1.1` | 429 kB | 960 kB | 686 kB | 116 kB |
| `shortbread-1.1-3d` | 436 kB | 967 kB | — | — |
| `smartmaps` | 459 kB | 1.2 MB | 907 kB | 124 kB |

About 25% more feature data than base Shortbread, which is expected and accounted for:
SmartMaps carries 3D dimensions on *every* building where base Shortbread carries none,
plus `kind` on every POI, `way_area` on every water and land polygon, and elevations. The
3D extension alone costs Shortbread only +0.7% on this extract because it touches just the
buildings that *have* dimensions tagged; here estimation fills in the rest.

Feature counts line up across the merges:

| SmartMaps | | Shortbread equivalents | |
|---|---|---|---|
| `building` | 5,282 | `buildings` 5,221 + `building_parts` 62 | 5,283 |
| `water_polygons` | 7,397 | `ocean` 7,380 + `water_polygons` 33 | 7,413 |
| `transport` | 713 | `streets` 468 + `street_polygons` 46 + `public_transport` 279 | 793 |
| `transport_label` | 613 | `street_labels` 613 + `street_labels_points` 0 | 613 |
| `landcover` + `landuse` | 113 + 185 | `land` 59 + `sites` 109 | 168 |
| `housenumber_label` | 442 | `addresses` 442 | 442 |
| `poi` | 2,485 | `pois` 2,485 | 2,485 |

The differences are merging, not dropped features. Merging happens per layer, so a merged
layer gives identical attribute sets more chances to combine: `transport` in particular
drops the `motorcar` and `foot` access attributes this layout does not have, so more roads
become mergeable. The land layers go the other way — Shortbread's `sites` are not merged
at all, and splitting one classification across two layers gives each fewer neighbours to
merge with. The single `building` against Shortbread's 5,283 is the merged model working:
a polygon tagged both `building` and `building:part` is two features there and one here.

## Verifying against real SmartMaps tiles

Nothing here has been checked against actual SmartMaps output — only against the TileJSON.
If you have access to real tiles, the two things worth comparing are:

1. **`kind` vocabularies**, layer by layer, against the lists above. Differences are a
   data change in the mapping tables, not a structural one.
2. **The inferred attributes** in the table above, particularly `3d` and `ref_prefix`.

Both are contained by design: the layer shapes and the field types are pinned by tests, so
a vocabulary correction cannot silently break the layout.
