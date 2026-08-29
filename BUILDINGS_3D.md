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
| `height` | number (m) | `height`, `building:height`, or derived from `building:levels` | a valid height exists or can be derived |
| `min_height` | number (m) | `min_height`, `building:min_height`, or `building:min_level` | greater than zero |
| `height_estimated` | boolean | — | only when `height` was derived from a level count |
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

1. **Explicit height.** `height`, else `building:height`. Units are parsed: `12`, `12 m`,
   `40 ft`, `12'3"` all work, and a decimal comma (`3,5`) is accepted.
2. **Level count.** If no valid height exists, `building:levels` × the level height, plus a
   roof height if one can be established. The result is marked `height_estimated=true`.
3. **Nothing.** If neither exists, no `height` is emitted at all.

Base height uses `min_height`, else `building:min_level` × the level height, else nothing.

Roof height prefers `roof:height`, else `roof:levels` × the level height, else nothing. A
non-flat `roof:shape` with no height information produces **no** roof height: inventing an
architectural dimension from a shape name would be a guess presented as data.

### Level height

The assumed height of one above-ground level is **3.0 m**, defined once as
`ShortbreadConfiguration.DEFAULT_LEVEL_HEIGHT_METERS` rather than buried in layer code.

## Buildings with no dimensions

Most OSM buildings have neither `height` nor `building:levels`. This implementation
**emits no height for them** and leaves the fallback to the client:

```js
'fill-extrusion-height': ['coalesce', ['get', 'height'], 8]
```

The alternative — materializing an estimated `render_height` on every building — was
rejected on two grounds. It would present a guess as though it were source data, and it
would add a number to every building on the planet, which is the single largest tile-size
lever in this schema, to save consumers one expression.

Provenance is instead carried by `height_estimated`, which appears **only** on the subset
whose height came from a level count. A building with `height` and no `height_estimated`
was measured; one with both was derived; one with neither is unknown.

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
    // Buildings with no known height still need to draw.
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

## Licensing

This extension is part of this repository and is dedicated to the public domain under CC0,
like the rest of it. Generated tiles remain derived from OpenStreetMap and are subject to
the [ODbL](https://opendatacommons.org/licenses/odbl/); if you publish a map from them you
must credit OpenStreetMap.
