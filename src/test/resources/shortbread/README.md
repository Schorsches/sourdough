# Vendored specification artifacts

`shortbread-1.1.md` is the Shortbread 1.1 specification itself, the Hugo source the
published page is built from, fetched from the pinned revision:

    https://raw.githubusercontent.com/shortbread-tiles/shortbread-docs/
      fc5c602c84a48cc6189b3bdbb36b9ed8abf57924/shortbread-website/content/schema/1.1.md

`SpecConformanceTest` parses its feature tables and asserts that this implementation's
mapping tables match them exactly: every `kind`, the OSM tags it comes from, and the zoom
it appears at. That makes the specification itself, rather than a transcription of it, the
thing the tests check against.

Adopting a future Shortbread version starts by replacing this file. The test then reports
every kind and zoom that changed.

`taginfo.json` is the Shortbread project's own machine-readable list of the OpenStreetMap
tags the schema consumes, fetched from the pinned specification revision:

    https://raw.githubusercontent.com/shortbread-tiles/shortbread-docs/
      fc5c602c84a48cc6189b3bdbb36b9ed8abf57924/shortbread-website/static/taginfo.json

It is vendored so the differential test runs without network access.

Note that this file is **stale relative to the 1.1 specification**: its `data_updated`
field reads 2023-02-22, and its contents match Shortbread 1.0. It still lists
`amenity=playground` and `amenity=dog_park`, which 1.1 moved to `leisure=*`, and it is
missing `waterway=drain`, `amenity=fuel` and `leisure=dog_park`, which 1.1 added.
`TaginfoDifferentialTest` accounts for exactly that delta and would fail if the difference
were anything other than the seven documented 1.0 to 1.1 changes.
