# Working in this repository

Notes for anyone — human or agent — changing this codebase. Things that are non-obvious,
cost someone real time to discover, or will silently produce wrong output if ignored.

For how to *run* the builder, see [RUNNING.md](./RUNNING.md).

## What this repository is

A Planetiler profile that builds four tilesets from one codebase:

| `--schema` | Docs | Maxzoom |
|---|---|---|
| `sourdough` (default) | [SCHEMA.md](./SCHEMA.md) | 15 |
| `shortbread-1.1` | [SHORTBREAD_SCHEMA.md](./SHORTBREAD_SCHEMA.md) | 14 |
| `shortbread-1.1-3d` | + [BUILDINGS_3D.md](./BUILDINGS_3D.md) | 14 |
| `smartmaps` | [SMARTMAPS_SCHEMA.md](./SMARTMAPS_SCHEMA.md) | 14 |

```
fyi.osm.sourdough
├── layers/         Sourdough. Output is frozen — see below.
├── shortbread/     Shortbread 1.1 + the 3D extension
├── smartmaps/      the SmartMaps-compatible layout
└── common/         shared by all of them, and allowed to know about none of them
```

## The rules that bite

### Sourdough's output is frozen

`layers/` and `SCHEMA.md` describe a published schema other people build styles against.
Do not change what it emits as a side effect of work on another schema. If a shared helper
would change Sourdough's output, it is not a shared helper.

### `common/` may not know about any schema

Nothing in `common/` may reference a tile-layer name or a schema's `kind` vocabulary.
The split is **computation versus naming**: `BuildingDimensionParser` computes heights and
is shared; `Buildings3d`, which spells them `height`/`min_height`, belongs to Shortbread.

This is enforced, not merely intended — `PackageBoundaryTest` scans the sources and fails
on an import from `shortbread`/`smartmaps` or on any schema layer name appearing as a
string literal. `ZOrder`'s road-class ordering is a documented deliberate exception.

### A handler post-processes exactly one layer

This one is silent and cost a real bug. Planetiler keys post-processors by the single name
a handler returns from `name()`:

```java
layerPostProcessors.computeIfAbsent(handler.name(), ...).add(handler);   // register
var processors = layerPostProcessors.get(layerName);                     // look up
if (processors != null) { ... }                                          // else pass through
```

But a handler can *emit* into any number of layers, because emitting only names a layer at
the call site: `fc.polygon("some-other-layer")`. Nothing connects the two. A second layer
therefore receives **no post-processing and no complaint** — no exception, no warning, and
the layer is still present and well-formed. `landcover`, written by the `landuse` handler,
came out at 257 features on a Monaco extract where 113 was right.

**If you write into a layer other than your own `name()`, register its treatment
explicitly** — `SecondaryLayer` exists for this — or record that no merge is wanted.
`LayerPostProcessingTest` checks every layer with mergeable geometry across both fixed
schemas and fails until one or the other is true.

Related: a merged layer post-processes more than one geometry type. `FeatureMerge`
passes non-matching geometries through, but if you write your own post-processing, a
filter that drops them will silently delete every station and pedestrian square from
`transport`. `MergedLayerPostProcessTest` pins that.

### `Parse.meters` is dangerously lenient

Planetiler's parser reads `"12;14"` as `14` and `"1e9"` as `9`. Do not call it on raw OSM
values. `common/buildings3d/BuildingDimensionParser` gates it behind strict regexes first;
use that, or gate your own.

### Never merge buildings by attributes

`FeatureMerge.mergeMultiPolygon` on a building layer collapses a whole tile into a handful
of features — a Monaco extract went from ~5,200 buildings to 7 — and destroys per-building
extrusion heights. Buildings stay individual in every schema.

### The fixed-zoom schemas mean it

`shortbread-1.1`, `shortbread-1.1-3d` and `smartmaps` stop at zoom 14 and expect clients to
overzoom. A `--maxzoom` above 14 is **rejected**, not honoured, so an archive cannot claim
to be one of these schemas and not be. Only Sourdough treats maxzoom as a preference.

## Tests

```bash
mvn test                          # everything CI runs
mvn test -DexcludedTestGroups=    # adds the integration and differential suites
```

The extra suites need `data/sources/<area>.osm.pbf` and the water-polygons archive; they
skip rather than fail when absent. See [CI.md](./CI.md).

Two habits worth keeping, because both have already caught real defects here:

**Check against the artefact, not a transcription of it.** `SpecConformanceTest` parses the
vendored Shortbread specification and drives the profile from its tables;
`TileJsonConformanceTest` does the same against the vendored SmartMaps TileJSON. A
hand-written test can be wrong in exactly the way the implementation is wrong.

**Mutation-test a new guard.** Break the thing on purpose and confirm the test fails, then
restore. Several guards here were vacuous until this was done.

Note that `SmartMapsConformanceTest` and friends call `processFeature` directly and never
reach `postProcess` — which is why the post-processing bug above survived them.

## Adding a schema

1. Add the enum value in `Schema`. Dispatch in `Builder` is an exhaustive `switch`, so it
   will not compile until you handle it — that is deliberate.
2. Write the declarative layer table, as `ShortbreadSchema` and `SmartMapsSchema` do.
   Documentation is generated from reading it, and conformance tests drive off it.
3. If there is a specification or a published tileset, **vendor it** under
   `src/test/resources/` and assert your table against the file.
4. Put schema-neutral helpers in `common/`, not in your schema's package.
5. Register post-processing for every layer you emit into, per the rule above.

## Conventions

- Comments explain *why*, and are worth writing where a reader would otherwise assume a
  mistake. Existing code is fairly heavily commented; match it.
- Attributes are **omitted** rather than set to a default. On a planet-scale tileset an
  attribute set to its default costs bytes on every feature and tells a consumer nothing
  an absent attribute does not.
  - One scoped exception: **SmartMaps booleans** are always present, `true` or `false`,
    because omit-when-false makes `["==", ["get", "x"], false]` silently match nothing in a
    style. Booleans only, SmartMaps only; strings and numerics follow the rule above, and
    the other schemas are unaffected. See SMARTMAPS_SCHEMA.md for the full reasoning and
    `SmartMapsSchema.booleanAttributes` for the list.
- Deviations from a specification get recorded in that schema's doc, with the reason. If
  you cannot write the reason, reconsider the deviation.
- Commit messages here explain the reasoning, not just the change.
