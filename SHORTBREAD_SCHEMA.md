# Shortbread 1.1 support

This repository can generate tiles in the [Shortbread vector tile schema][shortbread] in
addition to its own Sourdough schema. Shortbread is a lean, general-purpose schema with
an ecosystem of existing map styles, so tiles generated this way work with styles you did
not have to write yourself.

```bash
mvn package
java -jar target/sourdough-builder-HEAD-with-deps.jar \
  --download --area iceland --schema shortbread-1.1 --output shortbread.pmtiles
```

## Specification revision

| | |
|---|---|
| Version | **1.1** (released, not a draft) |
| Published at | <https://shortbread-tiles.org/schema/1.1/> |
| Repository | <https://github.com/shortbread-tiles/shortbread-docs> |
| **Pinned revision** | **`fc5c602c84a48cc6189b3bdbb36b9ed8abf57924`** |
| Revision date | 2026-08-18 |
| Retrieved | 2026-08-28 |

The specification has **no `v1.1` git tag and no GitHub release**, so a commit hash is the
only immutable way to identify it. The content commit is `b4f0cd5d…` ("Release Shortbread
1.1"); the merge commit above is the one on `main`.

Implementation is developed against that revision. Later edits to the specification do
not silently change what this code targets: the revision is recorded in
`ShortbreadSchema.SPEC_REVISION`, and moving to a newer one is a deliberate change.

## How the schema is described in code

Two layers of checking, both automatic.

`src/main/java/fyi/osm/sourdough/shortbread/ShortbreadSchema.java` holds a declarative
table of all 26 layers: geometry type, minimum zoom, and every attribute with its type.
`ShortbreadConformanceTest` drives the whole profile over a fixture corpus and checks each
emitted feature against it, so the schema is stated once rather than in code, tests and
prose separately.

Above that, **the specification document itself is vendored** at the pinned revision, in
`src/test/resources/shortbread/shortbread-1.1.md`. `SpecConformanceTest` parses its
Features tables, and for every row builds a feature carrying the OSM tags that row names,
runs the profile over it, and checks the layer emits the `kind` and the zoom the row
specifies. Around 130 rows are exercised this way, plus the 137-entry POI list and the
layer inventory.

That matters because a hand-written test can be wrong in exactly the same way the
implementation is. Checking against the document removes one opportunity for that.

### Adopting a future Shortbread version

1. Replace `src/test/resources/shortbread/shortbread-1.1.md` with the new revision and
   re-pin the commit in `ShortbreadSchema`.
2. Run `mvn test`. `SpecConformanceTest` now names every kind and zoom that moved, and
   `ShortbreadConformanceTest` names every layer or attribute that changed shape. That is
   the to-do list, derived rather than guessed.
3. Implement, and update `ShortbreadSchema` for any new layers or attributes.
4. Add a new value to the `Schema` enum (`shortbread-1.2`) so the older schema stays
   available to anyone who needs it.

Shortbread's [versioning policy][versioning] guarantees that a style written for X.Y
works against any tileset with the same major and an equal or greater minor version.
Minor versions may add layers, fields and `kind` values and may change zoom thresholds,
but may not remove or rename layers or fields, or change a layer's geometry type.

## Zoom levels

Shortbread fixes the tileset zoom range at **0 to 14**. Higher client zooms are served by
overzooming the zoom-14 tiles, which MapLibre and other renderers do automatically.

Accordingly, `--schema shortbread-1.1` defaults to `--maxzoom 14`, and **rejects a
`--maxzoom` above 14** rather than building an archive that claims to be Shortbread and is
not. Sourdough's own default of 15 is unaffected.

## Names and languages

`name` is the OSM `name` tag verbatim. Additional languages are emitted as `name_xx`
attributes sourced from `name:xx`, preserving IETF subtags (`name_ko-Latn`).

Shortbread leaves the choice of languages to the implementation, and every extra language
is another string attribute on every named label feature. **No language attributes are
emitted by default.** To add some:

```bash
# an explicit list
--additional-languages fr,de,es

# or a preset: the 43 codes used by SmartMaps Planet, being the 24 official EU
# languages plus Luxembourgish, eight widely used non-Latin-script languages, and
# Latin transliterations of those
--additional-languages smartmaps
```

Sourdough's `--language` option, which rewrites `name` itself from `name:xx`, **does not
apply** to the Shortbread schemas and logs a warning if you pass it: the specification
names the OSM `name` key as the source of that attribute.

## Deliberate deviations from the specification

The specification has two internal gaps. Both are resolved here in the direction that
produces a usable map, and both are recorded rather than hidden.

| Where | The gap | What this implementation does |
|---|---|---|
| `water_lines_labels` | 1.1 added `kind=drain` to `water_lines`, but the label layer's zoom table still lists only canals, rivers, streams and ditches. It is undefined whether named drains are labelled. | Named drains are labelled from zoom 14, the same as ditches and streams. |
| `place_labels` | The default population for capitals is given as "depends on place \*", but the footnote the asterisk refers to was never written. | A capital falls back to the default for its underlying `place` value (city 100,000, town 5,000, village 100, hamlet 50). This keeps the population sort meaningful for capitals. |

Two further specification quirks are reproduced faithfully rather than corrected, because
they are part of the data contract that existing styles rely on:

- The label layer for `street_polygons` is named `streets_polygons_labels` — plural where
  the polygon layer is singular.
- `buildings` carries a single property `dummy`, always `1`, and `street_polygons.rail` is
  documented as always false and is therefore never emitted.
- `aerialways` uses `kind=rope-tow` (hyphen) for OSM's `aerialway=rope_tow` (underscore).

## Attributes that are absent versus false

Attributes documented with a default (`false`, an empty string, or "field not available")
are **omitted** when they hold that default. A consumer cannot distinguish an absent
attribute from one set to its documented default, and omitting them is a large part of
what keeps zoom-14 tiles small.

Three attributes are always present because the schema says so: `buildings.dummy`,
`streets.kind` and `street_polygons.kind`.

## Layer inventory

All 26 layers are implemented. Every layer is built to zoom 14.

| Layer | Geometry | From zoom | Implementation |
|---|---|---|---|
| `ocean` | polygon | 0 | `layers/Ocean.java` |
| `water_polygons` | polygon | 4 | `layers/WaterPolygons.java` |
| `water_polygons_labels` | point | 4 | `layers/WaterPolygons.java` |
| `water_lines` | line | 9 | `layers/WaterLines.java` |
| `water_lines_labels` | line | 12 | `layers/WaterLines.java` |
| `dam_lines` | line | 12 | `layers/Dams.java` |
| `dam_polygons` | polygon | 12 | `layers/Dams.java` |
| `pier_lines` | line | 12 | `layers/Piers.java` |
| `pier_polygons` | polygon | 12 | `layers/Piers.java` |
| `boundaries` | line | 0 | `layers/Boundaries.java` |
| `boundary_labels` | point | 2 | `layers/BoundaryLabels.java` |
| `place_labels` | point | 4 | `layers/PlaceLabels.java` |
| `land` | polygon | 7 | `layers/Land.java`, `mapping/LandKinds.java` |
| `sites` | polygon | 14 | `layers/Sites.java`, `mapping/SiteKinds.java` |
| `buildings` | polygon | 14 | `layers/Buildings.java` |
| `addresses` | point | 14 | `layers/Addresses.java` |
| `streets` | line | 5 | `layers/Streets.java`, `mapping/StreetKinds.java` |
| `street_polygons` | polygon | 11 | `layers/StreetPolygons.java` |
| `street_labels` | line | 10 | `layers/StreetLabels.java` |
| `streets_polygons_labels` | point | 14 | `layers/StreetPolygons.java` |
| `street_labels_points` | point | 12 | `layers/StreetLabelsPoints.java` |
| `bridges` | polygon | 12 | `layers/Bridges.java` |
| `aerialways` | line | 12 | `layers/Aerialways.java` |
| `ferries` | line | 10 | `layers/Ferries.java` |
| `public_transport` | point | 11 | `layers/PublicTransport.java` |
| `pois` | point | 14 | `layers/Pois.java`, `mapping/PoiKinds.java` |

### Feature ordering

Five layers specify an order, and all five set an explicit Planetiler sort key. Nothing
relies on iteration or hash order.

| Layer | Order |
|---|---|
| `water_polygons_labels` | `way_area`, largest first |
| `boundary_labels` | `way_area`, largest first |
| `place_labels` | `population`, largest first |
| `streets` | z-order: OSM `layer`, then tunnel before ground before bridge, then road class |
| `street_polygons` | the same z-order |

### `way_area` units

The units differ between layers, and this is not an oversight in the specification:

- `water_polygons` and `water_polygons_labels`: **square meters** of the Mercator
  projection.
- `boundary_labels`: **hectares**.

Areas are measured on the original source geometry, before tile simplification.

### Relation handling

`boundaries` is the only layer whose attributes genuinely come from parent relations, and
it uses Planetiler's relation preprocessor rather than any spatial join:

- `admin_level` is the **lowest** value across the parent administrative relations, so a
  way that is both a national and a state border reads as national.
- `maritime` is true when the way itself has `maritime=yes` **or** `natural=coastline`.
- `disputed` is true when the way has `disputed=yes`, **or** it belongs to a
  `boundary=disputed` relation with no `admin_level`, **or** to one at admin level 2 or 4.

Multipolygons are handled by Planetiler's OSM reader for every polygon layer.

## Input data

Shortbread 1.1 needs exactly the two sources Sourdough already uses: an OSM PBF extract
and the [OSMCoastline water polygons][coastline]. No Natural Earth data and no land
polygons are required. The `ocean` layer is fed from the same shapefile Sourdough's
`water` layer uses, registered once per run, so switching schemas never downloads or
processes anything twice.

## Comparison with other implementations

Two other Shortbread producers were read as cross-checks. Both predate 1.1, so neither can
validate the 1.1 additions, but both were useful for checking the much larger part of the
schema that 1.0 and 1.1 share.

`shortbread-tilemaker`, the project's own reference implementation, still declares
`"version": "1.0"` and does not implement the 1.1 changes: no `motorcar` or `foot`, no
access normalization, and the older `pois` selection.

Planetiler ships a Shortbread definition of its own at
`planetiler-custommap/src/main/resources/samples/shortbread.yml`. It is also 1.0-era: no
`motorcar`, no `waterway=drain`, and hardcoded `name_en`/`name_de` attributes rather than
the generic `name_xx` mechanism 1.1 introduced. It is not usable as a dependency in any
case, since `planetiler-custommap` is not published to Maven Central.

Reading it confirmed a number of choices made here — the hectare unit for
`boundary_labels.way_area`, the boundary-label area thresholds, every `street_labels`
minimum zoom, and the approach of deduplicating addresses by testing whether the feature
is a POI. It also turned up three places where the two implementations differ.

### Where this implementation differs from Planetiler's sample

**Railway service tracks.** The specification says railways are available "with `service`
on zoom level 10+, other ways on zoom level 8+" — that is, minor service tracks appear
*later* than main lines. Planetiler's sample has this inverted, placing ways with a
`service` tag at zoom 8 and ways without one at zoom 10. This implementation follows the
specification: a service track appears at zoom 10, a main line at zoom 8. There is a test
covering the direction.

**The `dummy` property.** Planetiler's `buildings` layer emits no attributes at all. The
specification says the layer has one property, `dummy`, always `1`, so that is emitted
here — an existing style may rely on it.

**Boundary relations.** Planetiler's sample reads `admin_level` straight off the way and
carries `TODO` comments for both relation handling and the relation-derived half of
`disputed`. This implementation uses a Planetiler relation preprocessor to apply the full
1.1 rules: lowest admin_level across parents, and `disputed` from either the way's own tag
or a qualifying parent relation.

### Cross-check against the project's own taginfo

`shortbread-docs` publishes `taginfo.json`, a machine-readable list of every OSM tag the
schema consumes. A copy pinned at the same revision is vendored into
`src/test/resources/shortbread/`, and `TaginfoDifferentialTest` compares it against this
implementation's mapping tables. That is a stronger check on the 137-entry POI table and
the 43-entry land table than re-reading the prose.

The published file is itself stale — its `data_updated` field reads 2023-02-22 and its
contents describe Shortbread 1.0, so it does not reflect the released 1.1 specification it
sits alongside. The test allows for exactly the 1.0-to-1.1 delta and fails on anything
else, which turned up two defects in the file:

| taginfo says | The specification says | This implementation |
|---|---|---|
| `historic=artwork` | `tourism=artwork` (1.1.md line 851) | follows the specification |
| `landuse=plant_nursery` + a stray backtick | `landuse=plant_nursery` | follows the specification |

The test asserts that both defects are still present upstream, so that if they are fixed
the allowance is removed rather than quietly masking a future real difference.

One further difference is a judgment call rather than a correction. Planetiler includes
only OSM *nodes* in `place_labels`; this implementation also represents place *areas* by a
point. The specification says the layer "holds label points", which describes the output
geometry rather than restricting the input, and reading it as nodes-only would drop nearly
every `place=island`, which the schema lists as a feature in its own right.

## Measured output

Both schemas built from the same Luxembourg extract, which is large enough to be
representative: 260,034 buildings across cities, forest, motorway, rail and border.
Reproduce with:

```bash
mvn test -DexcludedTestGroups= -Dtest=ShortbreadIntegrationTest -Dintegration.area=luxembourg
```

| | `shortbread-1.1` | `shortbread-1.1-3d` | delta |
|---|---|---|---|
| Archive | 29.0 MB | 29.1 MB | +0.25% |
| Zoom-14 p50 | 10.8 kB | 10.8 kB | — |
| Zoom-14 p95 | 42.7 kB | 42.9 kB | +0.5% |
| Zoom-14 p99 | 83.6 kB | 84.5 kB | +1.1% |
| Zoom-14 max | 153.7 kB | 156.8 kB | **+2.0%** |
| Buildings | 260,034 | 260,034 | — |
| Building parts | — | 142 | — |

The 3D extension costs about 2% on the densest tile and a quarter of a percent overall.
The largest tile stays well under the 1 MB size Planetiler warns at. A Monaco extract gave
the same shape of answer (+2.1% on its largest zoom-14 tile), so the figure is not an
artifact of one region.

Building dimension counters from the Monaco run: 84 explicit heights, 485 derived from
level counts, 4,120 buildings with no usable dimensions, 1 malformed level count. Note how
few buildings carry any dimension data at all, which is why no height is invented for the
rest.

## Licensing

The Shortbread specification is published under CC0. This repository is also CC0; see
[LICENSE](./LICENSE).

Generated tiles are derived from OpenStreetMap, which is licensed under the [ODbL][odbl].
If you publish a map made from these tiles you must credit OpenStreetMap. See the
[attribution guidelines][attribution].

[shortbread]: https://shortbread-tiles.org/
[versioning]: https://shortbread-tiles.org/schema/versioning/
[coastline]: https://osmdata.openstreetmap.de/data/coast.html
[odbl]: https://opendatacommons.org/licenses/odbl/
[attribution]: https://osmfoundation.org/wiki/Licence/Attribution_Guidelines#Attribution_text
