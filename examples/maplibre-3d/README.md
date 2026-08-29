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
