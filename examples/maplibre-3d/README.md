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

Run against a Luxembourg extract, centred on Luxembourg City at zoom 16 with a 60 degree
pitch, in headless Chromium:

| | |
|---|---|
| Buildings rendered in view | 1,843 |
| Building parts rendered | 17 |
| Carrying a numeric `height` | 357 (JavaScript type `number`, not `string`) |
| Sample heights | 15, 9, 9, 9, 9 metres |
| Marked `height_estimated` | 350 of those 357 |
| No height in OpenStreetMap | 1,486, all still drawn at the client's fallback |
| At zoom 18, above the tileset maximum | 286 buildings still rendered from overzoomed zoom-14 tiles |

That last row is the one worth keeping in mind: the tileset stops at zoom 14 and the
client overzooms, so there is no need to build higher zoom levels.

Note how few buildings carry a height at all, and that almost every height that exists is
derived from a level count rather than measured. That is what the OpenStreetMap data looks
like, and it is why no height is invented for the rest.

The page exposes the map as `window.__map`, which is what makes this check possible.
