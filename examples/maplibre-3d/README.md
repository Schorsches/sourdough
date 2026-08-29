# MapLibre 3D buildings check

A minimal page for verifying that a `shortbread-1.1-3d` tileset renders. It is a
validation tool, not a map style.

```bash
java -jar ../../target/sourdough-builder-HEAD-with-deps.jar \
  --download --area monaco --schema shortbread-1.1-3d \
  --output examples/maplibre-3d/shortbread-1.1-3d.pmtiles

npx serve examples/maplibre-3d
```

Then open the page. Pass `?pmtiles=<url>` to point it at an archive somewhere else.

What it is checking:

- building polygons extrude at all;
- `height` and `min_height` arrive as numbers, not strings;
- elevated `building_parts` sit above the ground rather than on it;
- buildings with no height in OSM still draw, at the client's fallback height —
  they are tinted grey, and level-derived heights are tinted brown, so the three
  cases are visually distinguishable;
- zooming past 14 overzooms the zoom-14 tiles instead of failing.

Click any building to see its attributes and their JavaScript types.

## What this check has shown

Run against a Berlin extract, centred near the Brandenburg Gate at zoom 16 with a 60
degree pitch, in headless Chromium:

| | |
|---|---|
| Buildings rendered in view | 767 |
| Carrying a numeric `height` | 767 — all of them, JavaScript type `number` |
| Marked `height_estimated` | 746 |
| Building parts rendered | 212 |
| Parts with a numeric `min_height` | yes, samples 23, 23, 18 and 2.5 metres |
| Roof shapes present | flat, gabled, hipped, gambrel, quadruple_saltbox |
| Sample heights | 12, 24, 18, 27, 6, 24 metres |

Only 21 of those 767 buildings carry a height that anyone measured; the rest are derived
from a level count or estimated from the building type, which is what
`height_estimated` marks. On an earlier Luxembourg run, 1,486 of 1,843 buildings in view
had no height at all and relied on the client's fallback — the type-based estimate is what
changed that.

An earlier run also confirmed overzooming: at zoom 18, above the tileset maximum of 14,
286 buildings still rendered from overzoomed zoom-14 tiles. There is no need to build
higher zoom levels.

The page exposes the map as `window.__map`, which is what makes this check possible.
