# 3D buildings extension

> **This is not part of Shortbread 1.1.** The attributes and the `building_parts` layer
> described here are an extension defined by this repository. Shortbread's own `buildings`
> layer has exactly one property, `dummy`. Do not describe tiles built with
> `--schema shortbread-1.1-3d` as conforming to Shortbread 1.1 alone — they conform to
> Shortbread 1.1 *plus* this extension.

```bash
java -jar target/sourdough-builder-HEAD-with-deps.jar \
  --download --area iceland --schema shortbread-1.1-3d --output shortbread-3d.pmtiles
```

## Compatibility

`shortbread-1.1-3d` is a strict superset of `shortbread-1.1`. Every layer, attribute,
geometry type and zoom range the specification requires is present and unchanged, so an
existing Shortbread style renders it exactly as it renders the base schema. The extension
only ever *adds*:

- extra attributes on `buildings`, which a style that does not know about them ignores;
- a new `building_parts` layer, which such a style never references.

`ShortbreadConformanceTest` asserts both directions: that the 3D schema still satisfies
the base schema, and that no 3D attribute or layer can leak into `shortbread-1.1`.

## Why building parts get their own layer

Simple 3D Buildings maps a complex building as a plain `building` outline plus a set of
`building:part` polygons that cover it. The two overlap by design.

Putting parts into `buildings` — which is what Sourdough's own schema does today — means
any style that draws that layer draws every complex building twice: once as its outline
and again as each of its parts. That is a visible change to existing styles, which the
whole point of an additive extension is to avoid.

With a separate layer:

- a 2D style keeps drawing `buildings` and sees no difference;
- a 3D renderer draws `building_parts` where they exist and can suppress the parent
  outline, which is what Simple 3D Buildings semantics call for.

## Attributes

All lengths are **numbers in meters**, never strings: `"12 m"` is not a value a renderer
can extrude. Attributes are **omitted when absent or when they equal the renderer's
default**, rather than being written as nulls or zeroes — each attribute is multiplied by
every building on the planet.

### On `buildings` and `building_parts`

| Attribute | Type | From | Present when |
|---|---|---|---|
| `height` | number (m) | `height`, `building:height`, `building:levels`, or the building type | always, unless estimation is turned off |
| `min_height` | number (m) | `min_height`, `building:min_height`, or `building:min_level` | greater than zero |
| `height_estimated` | boolean | — | when `height` was derived rather than measured |
| `building_levels` | integer | `building:levels` | tagged and valid |
| `roof_height` | number (m) | `roof:height`, or `roof:levels` | valid and less than `height` |
| `roof_shape` | string | `roof:shape` | tagged |
| `roof_direction` | number (deg) | `roof:direction` | a bearing 0–360, or a cardinal name like `NE` |
| `roof_orientation` | string | `roof:orientation` | `along` or `across` |
| `building_colour` | string | `building:colour` | tagged |
| `building_material` | string | `building:material` | tagged |
| `roof_colour` | string | `roof:colour` | tagged |
| `roof_material` | string | `roof:material` | tagged |

### Only on `building_parts`

| Attribute | Type | From |
|---|---|---|
| `building_part` | string | the value of `building:part` |

### Deliberately not included

| Not emitted | Why |
|---|---|
| `roof_angle` | Redundant with `roof_height`, and very sparse in OSM. |
| `roof_levels`, `building_min_level` | Only used as inputs to `roof_height` and `min_height`. |
| `render_height` | It would add a number to *every* building on the planet to save clients one `coalesce`. See "Buildings with no dimensions". |

## Height derivation

### The rule that matters most

In OSM Simple 3D Buildings, `height` is the **total** height from the ground to the top of
the roof. It already includes the roof.

So a building tagged:

```
building=yes
height=14
roof:height=2
```

is **14 m tall, not 16**. `roof_height` describes the top 2 m *of* that 14 m. Adding them
would double-count the roof. This is covered by a test that exists specifically to stop
that regression.

Roof height may only be *added* when the total was derived from a level count, because a
level count describes the facade alone:

```
building=yes
building:levels=4          ->  4 * 3.0 = 12 m facade
roof:height=2              ->  + 2 m roof
                           =  height 14, height_estimated true
```

### Precedence

Every building gets a height, from the best source available:

1. **Explicit height.** `height`, else `building:height`. Units are parsed: `12`, `12 m`,
   `40 ft`, `12'3"` all work, and a decimal comma (`3,5`) is accepted. This is the only
   case that is measurement.
2. **Level count.** `building:levels` × the level height, plus a roof height if one can be
   established. Marked `height_estimated=true`.
3. **Building type.** A typical storey count for the kind of building it is, × the level
   height. Marked `height_estimated=true`. See below.

A consumer can tell the three apart without another attribute: no `height_estimated` means
measured; `height_estimated` with `building_levels` means derived from a level count;
`height_estimated` without `building_levels` means estimated from the type.

Pass `--estimate-missing-heights false` to stop at step 2, in which case a building with no
dimensions carries no `height` at all.

Base height uses `min_height`, else `building:min_level` × the level height, else nothing.

Roof height prefers `roof:height`, else `roof:levels` × the level height, else nothing. A
non-flat `roof:shape` with no height information produces **no** roof height: inventing an
architectural dimension from a shape name would be a guess presented as data.

### Level height

The assumed height of one above-ground level is **3.0 m**, defined once as
`ShortbreadConfiguration.DEFAULT_LEVEL_HEIGHT_METERS` rather than buried in layer code.

## Buildings with no dimensions

Most OSM buildings have neither `height` nor `building:levels` — three quarters of them,
even in a well-mapped city. Those buildings are estimated from their **building type**,
because the type is essentially always present and is the one thing that separates a
garden shed from an apartment block. A single global constant would make them the same
height.

The table lives in `BuildingTypeDefaults.java` and is expressed in storeys, so it composes
with the configured level height. A sample at the default 3 m per storey:

| Building type | Storeys | Height |
|---|---|---|
| `garage`, `shed`, `hut`, `carport`, `kiosk`, `bungalow` | 1 | 3 m |
| `house`, `detached`, `terrace`, `farm`, `barn` | 2 | 6 m |
| `retail`, `commercial`, `school`, `civic` | 3 | 9 m |
| `apartments`, `office`, `hotel`, `hospital`, `university` | 4 | 12 m |
| `cathedral` | 6 | 18 m |
| anything else, including `building=yes` | 2 | 6 m |

`building=yes` is by far the most common value and says nothing, so it takes the global
fallback. Two storeys is a deliberately modest guess: too-tall defaults across a whole city
read worse than too-short ones.

**These are rendering estimates, not data.** Every one of them is marked
`height_estimated=true`, and a consumer that wants only measured heights can filter on the
absence of that flag, or turn estimation off entirely with
`--estimate-missing-heights false`.

A note on a decision that changed: earlier versions of this document argued against
materialising estimates, on the grounds that adding a number to every building on the
planet is the largest tile-size lever in the schema. Measured, that turned out to be wrong
for *this* kind of estimate. Filling in all 847,227 heightless Berlin buildings cost 0.4%
of archive size and left the largest tile unchanged, because the estimates come from a
handful of repeated values that both the MVT value dictionary and gzip compress almost to
nothing. The argument does still hold for a `render_height` carrying an arbitrary distinct
number per building, which is why that is not what this does.

## Malformed data

Planet-scale OSM contains a great deal of broken dimension tagging, and a single bad
object must never abort a build. The pipeline is *parse → validate → derive → validate →
emit*, and every parsing step returns nothing rather than throwing.

Values are checked for shape **before** conversion. Planetiler's own length parser is
lenient in a way that matters here: it reads `12;14` as 14 and `1e9` as 9, which would
turn malformed tagging into a plausible-looking height. Only a single number with at most
one unit is accepted.

Rejected or corrected:

| Input | Result |
|---|---|
| `abc`, empty, `NaN`, `Infinity`, `1e9`, `12;14`, `3,5,7` | no height |
| negative or zero height | no height |
| height above 1000 m | no height (the tallest building on earth is under 850 m) |
| `building:levels` negative, fractional-but-absurd, or above 200 | no derived height |
| `min_height` ≥ `height` | base dropped, counted |
| `roof:height` ≥ `height` | roof height dropped, counted |

Fractional level counts (`3.5`) are accepted: half storeys are real.

Nothing is clamped silently. The two cases that are corrected rather than rejected —
an inside-out base and an oversized roof — are counted so a planet run can be audited:

```
buildings.height.explicit      buildings.height.from_levels
buildings.height.absent        buildings.height.invalid
buildings.min_height.invalid   buildings.roof_height.invalid
buildings.levels.invalid       building_parts.emitted
```

These are logged once at the end of a run.

## Using it with MapLibre GL JS

`examples/maplibre-3d/index.html` is a minimal page for checking that the data works. It
is a validation tool, not a map style.

```js
map.addLayer({
  id: 'buildings-3d',
  source: 'shortbread',
  'source-layer': 'buildings',
  type: 'fill-extrusion',
  minzoom: 14,
  paint: {
    // `height` is always present unless estimation was turned off, but coalesce keeps
    // the style working either way.
    'fill-extrusion-height': ['coalesce', ['get', 'height'], 8],
    'fill-extrusion-base': ['coalesce', ['get', 'min_height'], 0],
    'fill-extrusion-color': ['coalesce', ['get', 'building_colour'], '#cfcfcf'],
    'fill-extrusion-opacity': 0.9
  }
});
```

For detailed buildings, draw `building_parts` and suppress the parent outline where parts
exist. `roof_shape`, `roof_height`, `roof_direction` and `roof_orientation` are there for
renderers that can draw real roof geometry; MapLibre's `fill-extrusion` cannot, and simply
ignores them.

Because the tileset stops at zoom 14, set `maxzoom` on the source so the client overzooms
those tiles rather than requesting ones that do not exist.

## What it costs

**The cost depends heavily on how well the region is mapped in 3D**, so one number would
be misleading. Both schemas built from the same input, in two regions:

Luxembourg — 260,034 buildings, 142 building parts:

| | `shortbread-1.1` | `shortbread-1.1-3d` | delta |
|---|---|---|---|
| Archive | 29.0 MB | 29.1 MB | +0.25% |
| Zoom-14 p95 | 42.7 kB | 42.9 kB | +0.5% |
| Zoom-14 p99 | 83.6 kB | 84.5 kB | +1.1% |
| Zoom-14 largest | 153.7 kB | 156.8 kB | +2.0% |

Berlin — 1,246,487 buildings, 41,735 building parts, and among the best Simple 3D
Buildings coverage anywhere in OpenStreetMap:

| | `shortbread-1.1` | `shortbread-1.1-3d` | delta |
|---|---|---|---|
| Archive | 69.9 MB | 72.2 MB | +3.2% |
| Zoom-14 p50 | 7.9 kB | 8.1 kB | +2.5% |
| Zoom-14 p95 | 95.5 kB | 99.6 kB | +4.3% |
| Zoom-14 p99 | 133.5 kB | 142.8 kB | +7.0% |
| Zoom-14 largest | 210.2 kB | 260.4 kB | **+23.9%** |

Filling in a height for the three quarters of buildings that have none accounts for only
0.4 percentage points of that: the same build with `--estimate-missing-heights false` comes
to +2.8% rather than +3.2%, with an identical largest tile.

So: a fraction of a percent where 3D mapping is sparse, and up to a quarter more on the
single densest tile where it is not. The largest tile measured anywhere is 260 kB, well
inside the 1 MB size Planetiler warns at, and the median tile barely moves. Plan for the
Berlin figures rather than the Luxembourg ones.

That is affordable, so the appearance attributes — `building_colour`,
`building_material`, `roof_colour`, `roof_material` — stay enabled by default rather than
sitting behind a flag. They are sparse in OpenStreetMap, so they cost little while being
the difference between a grey city and a recognisable one.

If a future change pushes the zoom-14 figures materially higher, those four are the first
candidates to drop; `height` and `min_height` are what the layer exists for.

### How much 3D data actually exists

From the same Berlin run, across 1,121,410 buildings read:

| Height source | Count | Share |
|---|---|---|
| Explicit `height` tag | 14,375 | 1.3% |
| Derived from `building:levels` | 259,808 | 23.2% |
| Estimated from the building type | 847,227 | 75.5% |
| No height emitted | 0 | — |

Only 1.3% of buildings in one of the best-mapped cities in OpenStreetMap carry a measured
height, and three quarters carry nothing at all. That is why the type-based estimate
exists, and why `height_estimated` matters: on real data it is the common case, not the
exception.

The same run exercised the malformed-data handling on real data rather than fixtures: 624
`min_height` values at or above their building's height, 65 oversized roof heights, 35
unparseable heights and 16 bad level counts, all dropped or corrected, with the build
completing normally. (A malformed height falls through to the next source rather than
leaving the building without one.)

### Building parts and their parent outline

A 3D renderer drawing parts wants to suppress the parent building outline. Simple 3D
Buildings defines a `type=building` relation that ties the two together, which would give
an exact answer without any spatial test.

Measured on Berlin, **only 16% of building parts (5,843 of 36,606) belong to such a
relation**; the rest are plain overlapping ways. A client therefore has to implement the
geometric approach regardless, and once it has, an attribute covering a sixth of cases
saves it nothing. No parent id is emitted, and no relation pass is carried on planet
builds for it.

## Licensing

This extension is part of this repository and is dedicated to the public domain under CC0,
like the rest of it. Generated tiles remain derived from OpenStreetMap and are subject to
the [ODbL](https://opendatacommons.org/licenses/odbl/); if you publish a map from them you
must credit OpenStreetMap.
