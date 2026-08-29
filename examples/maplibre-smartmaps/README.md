# MapLibre check for the SmartMaps layout

A minimal page for verifying that a `--schema smartmaps` tileset renders. It is a
validation tool, not a map style. The sibling [`../maplibre-3d`](../maplibre-3d) page does
the same job for `shortbread-1.1-3d`.

```bash
java -jar ../../target/sourdough-builder-HEAD-with-deps.jar \
  --download --area monaco --schema smartmaps \
  --output examples/maplibre-smartmaps/smartmaps.pmtiles

npx serve examples/maplibre-smartmaps
```

Then open the page. Pass `?pmtiles=<url>` to point it at an archive somewhere else, and a
`#zoom/lat/lng/bearing/pitch` hash to land somewhere specific — `#16/52.5163/13.3777/-20/60`
for the Brandenburg Gate.

What it is checking, and how it differs from the Shortbread page:

- `building` extrudes from **`render_height`**, not `height` — this layout has no factual
  height field at all;
- elevated parts sit above ground on **`render_min_height`**;
- outlines and parts share **one layer**, told apart by the `building:part` boolean, so
  one `fill-extrusion` layer draws both. Parts are tinted so the merged model is visible;
- `3d` marks the buildings carrying real Simple 3D Buildings data, as against those whose
  height was estimated from their building type;
- the sea comes from `water_polygons` as `kind=ocean` — this layout has no ocean layer;
- `landcover` and `landuse` are two layers where Shortbread has one `land`;
- zooming past 14 overzooms the zoom-14 tiles instead of failing.

Click any building to see its attributes and their JavaScript types.

## What this check has shown

Berlin, centred near the Brandenburg Gate at zoom 16 with a 60 degree pitch, in headless
Chromium:

| | |
|---|---|
| Buildings rendered in view | 4,074 |
| Carrying a numeric `render_height` | 4,074 — all of them, JavaScript type `number` |
| Building parts in view | 513, `building:part` arriving as `boolean` |
| Carrying `render_min_height` | 144; samples 22, 16.8, 16 and 10 metres |
| Marked `3d` | 3,829 |
| Marked `roof` | 3,265 |
| Sample heights | 21, 24, 33, 12, 30, 24, 18, 15 metres |
| Console errors | none |

Every building has a height because this layout has no factual height field to be honest
in — `render_height` exists to be extruded — so estimation is on by default. Across the
whole Berlin extract, 14,346 buildings had an explicit height, 259,734 got one from a
level count, and 847,209 were estimated from their building type; 36,606 building parts
were emitted. The `3d` flag is what separates the first two groups from the third.

Monaco at zoom 16 rendered 696 buildings, 30 of them parts, again all with numeric
heights. At zoom 18 — above the tileset maximum of 14 — 38 buildings still rendered from
overzoomed zoom-14 tiles, so there is no need to build higher zooms.
