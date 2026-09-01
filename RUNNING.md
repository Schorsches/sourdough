# Running the builder

How to generate vector tiles for a country or region on your own machine, in any of the
four schemas this repository supports, and how to look at the result.

If you only want the flag reference, that is [USAGE.md](./USAGE.md). This page is the
walkthrough.

---

## 1. What you need

- **Java 21 or later** — `java -version`
- **Maven** — `mvn -version`
- **Disk**: roughly 6× the size of your `.osm.pbf`, for the extract, the temporary
  feature store and the finished archive
- **RAM**: 4 GB is enough for a small country; see [Sizing](#5-sizing-what-to-expect)

No Java or Maven? Skip to [Running with Docker](#6-running-with-docker) instead.

## 2. Build the jar

```bash
mvn package
```

That produces `target/sourdough-builder-HEAD-with-deps.jar` — a single self-contained
archive with Planetiler and everything else inside it. You only repeat this after changing
the code.

## 3. Build tiles for a region

One command. `--download` fetches the input data the first time and reuses it afterwards.

```bash
java -jar target/sourdough-builder-HEAD-with-deps.jar \
  --download \
  --area luxembourg \
  --schema smartmaps
```

That writes **`data/smartmaps.pmtiles`**.

Two things are downloaded into `data/sources/`, and only once:

| File                            | What it is                                                                            | Size        |
| ------------------------------- | ------------------------------------------------------------------------------------- | ----------- |
| `<area>.osm.pbf`                | the OpenStreetMap extract, from [Geofabrik](https://www.geofabrik.de/)                | 1 MB – 4 GB |
| `water-polygons-split-3857.zip` | global coastlines, from [osmdata.openstreetmap.de](https://osmdata.openstreetmap.de/) | 886 MB      |

The coastline file is global and the same for every region, so the second build of any
area is much faster than the first.

### Picking the schema

`--schema` takes one of four values. Each writes to its own default output file, so you
can build all four for the same region without them colliding:

```bash
J="java -jar target/sourdough-builder-HEAD-with-deps.jar --area luxembourg"

$J                                # → data/sourdough.pmtiles        (the default)
$J --schema shortbread-1.1        # → data/shortbread-1.1.pmtiles
$J --schema shortbread-1.1-3d     # → data/shortbread-1.1-3d.pmtiles
$J --schema smartmaps             # → data/smartmaps.pmtiles
```

| Schema              | Use it when                                                                   | Maxzoom |
| ------------------- | ----------------------------------------------------------------------------- | ------- |
| `sourdough`         | you want everything, and you are writing your own style                       | 15      |
| `shortbread-1.1`    | you want to use an existing [Shortbread](https://shortbread-tiles.org/) style | 14      |
| `shortbread-1.1-3d` | as above, plus extruded buildings                                             | 14      |
| `smartmaps`         | you have a style written for the SmartMaps layout                             | 14      |

Add `--output my-tiles.pmtiles` to put the archive somewhere else, and `--force` to
overwrite one that already exists.

> The three fixed-zoom schemas **reject** `--maxzoom` above 14 rather than quietly
> honouring it — clients overzoom those tiles above 14, and an archive built higher would
> claim to be a schema it is not. Only `sourdough` treats maxzoom as a preference.

## 4. Naming your region

`--area` is a [Geofabrik](https://download.geofabrik.de/) extract name, resolved against
their index when the download runs:

```bash
--area monaco          # a small country
--area iceland         # a country
--area luxembourg      # a country
--area berlin          # a city-region sub-extract
--area washington      # a US state
--area alberta         # a Canadian province
```

Browse <https://download.geofabrik.de/> to find the name you want — it is the last path
segment of the download URL, without `-latest.osm.pbf`. If the name cannot be resolved the
run fails at the download step and tells you what it looked for.

### Using a file you downloaded yourself

Useful for a Geofabrik sub-region whose name is awkward, a
[BBBike](https://extract.bbbike.org/) custom extract, or an internal file. Either drop it
where the builder expects it:

```bash
mkdir -p data/sources
mv ~/Downloads/malta-latest.osm.pbf data/sources/malta.osm.pbf
java -jar target/sourdough-builder-HEAD-with-deps.jar --area malta --schema smartmaps
```

(no `--download`, because nothing needs downloading — though you will still need the
coastline zip; fetch it once with `--download --only_download`.)

Or point straight at it:

```bash
java -jar target/sourdough-builder-HEAD-with-deps.jar \
  --osm_path ~/Downloads/malta-latest.osm.pbf \
  --schema smartmaps --output malta.pmtiles
```

### Building only part of an extract

```bash
--bounds 5.9,45.8,10.5,47.8          # west,south,east,north
--polygon my-region.poly             # a .poly file, for a non-rectangular area
```

Both cut down the _output_; the whole extract is still read.

## 5. Sizing: what to expect

Measured on a modest 4-core, 16 GB container — a laptop will be comparable or better:

| Region     | Extract | Build time | `smartmaps` archive |
| ---------- | ------- | ---------- | ------------------- |
| Monaco     | 1.1 MB  | ~1.5 min   | 459 kB              |
| Berlin     | 173 MB  | ~1 min\*   | 84 MB               |
| Luxembourg | 54 MB   | ~1 min\*   | —                   |

\* after the coastline file is already downloaded. Monaco's 1.5 minutes is almost entirely
the one-off 886 MB coastline download and read.

A country-sized extract is minutes, not hours. **The whole planet is a different
undertaking** — several hours, >64 GB RAM and >1 TB of SSD. Planetiler's
[PLANET.md](https://github.com/onthegomap/planetiler/blob/main/PLANET.md) covers that
properly; the useful knobs are `--threads`, `--storage=mmap|ram|direct`,
`--nodemap_storage`, and `--tmpdir` pointed at fast disk.

### Running from WSL

On WSL, where `data/` lives matters more than the hardware does. A path under `/mnt/c` is a
Windows drive reached over a 9p mount, and Planetiler memory-maps its temporary files
(`node.db`, `multipolygon.db`, `feature.db`) — the one access pattern that mount handles
worst. Reading the 886 MB coastline archive across it took 2m12s of an otherwise 5m23s
Monaco build.

Every input and output path is resolved relative to the working directory, so no flags are
needed to fix this. Build from somewhere on the Linux filesystem instead:

```bash
mkdir -p ~/tiles && cd ~/tiles
java -jar /mnt/c/path/to/sourdough/target/sourdough-builder-HEAD-with-deps.jar \
  --download --area bangladesh --schema smartmaps
```

Sources, `data/tmp` and the finished archive all land on ext4; only the jar is read from
`/mnt/c`, once at startup. The checkout can stay on the Windows drive — `mvn package` is not
the slow part.

**Where the archive ends up.** On the Linux filesystem, which means it does not appear under
`C:\...` in File Explorer — the usual first surprise. WSL files are reachable from Windows
only through the `\\wsl.localhost\` network path:

```text
\\wsl.localhost\<distro>\home\<user>\tiles\data
```

If you would rather the finished archive sat on the Windows side, point `--output` at it.
Writing the archive is a single sequential pass, so it costs little across the mount, while
the temp I/O that actually hurts stays on ext4:

```bash
cd ~/tiles
java -jar /mnt/c/path/to/sourdough/target/sourdough-builder-HEAD-with-deps.jar \
  --download --area bangladesh --schema smartmaps \
  --output /mnt/c/path/to/sourdough/data/bangladesh.pmtiles
```

Two more things that catch people out:

- The integration and differential suites resolve `data/sources/` against the **checkout**,
  so moving your sources hides them and those suites quietly skip instead of failing.
  Symlink it back: `ln -s ~/tiles/data/sources data/sources`.
- The Linux filesystem is a virtual disk stored on `C:` that grows on demand. `df` inside
  WSL reports its virtual size, not the space actually available — `df -h /mnt/c` is the
  real budget. It also does not shrink again once a build's temp files are deleted.

### When it goes wrong

| Symptom                               | Cause and fix                                                                                                                                 |
| ------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| Fails during download, naming an area | The Geofabrik name is wrong. Check it at <https://download.geofabrik.de/>.                                                                    |
| `... has a fixed maximum zoom of 14`  | You passed `--maxzoom 15` or higher to a fixed-zoom schema. Drop the flag — clients overzoom above 14.                                        |
| `Unknown schema 'shortbread'`         | Schema ids are exact: `shortbread-1.1`, not `shortbread`. The error lists the valid values.                                                   |
| Output file already exists            | Add `--force`, or choose a different `--output`.                                                                                              |
| `OutOfMemoryError`, or very slow      | Give the JVM more heap (`java -Xmx8g -jar ...`), lower `--threads`, or set `--storage=mmap`.                                                  |
| Runs out of disk mid-build            | Budget ~6× the extract size. `--tmpdir` can point at a different volume.                                                                      |
| `--language` seems to be ignored      | It applies to `sourdough` only; the other three define `name` as the OSM tag. The run logs a warning saying so. Use `--additional-languages`. |

## 6. Running with Docker

No Java or Maven needed. The image's entrypoint _is_ the builder, so every flag above works
unchanged — you just mount a directory for `data/`.

**Build the image from this checkout.** This is the path you want:

```bash
docker build -t sourdough-builder .

mkdir -p data
docker run --rm --user "$(id -u):$(id -g)" -v "$PWD/data:/tiles/data" \
  sourdough-builder --download --area luxembourg --schema smartmaps
```

The archive appears at `data/smartmaps.pmtiles` on your machine.

> Keep the `--user` flag. The image has no `USER` of its own, so without it the container
> runs as root and leaves the downloaded sources and `data/tmp/` owned by root. Your next
> build — in Docker or not — then fails with `AccessDeniedException` on files it is not
> allowed to delete, and undoing that needs `sudo`.

> **Why not just pull a published image?** `ghcr.io/jake-low/sourdough-builder` is built
> from the _upstream_ repository, and this fork does not publish an image of its own — see
> [CI.md](./CI.md) for why forks build the image but skip the push. So the published image
> will not have the schemas added here. Build locally, or change `IMAGE_NAME` and the fork
> guards as CI.md describes if you want to publish your own.

Give Docker enough memory for larger regions (Docker Desktop → Settings → Resources); the
default is often 2 GB, which is not enough beyond a small country.

## 7. Looking at the result

A `.pmtiles` archive is read with **HTTP range requests**, so opening `index.html` from the
filesystem will not work — it needs to be served over HTTP.

This repository ships two check pages that do exactly this:

```bash
# for --schema shortbread-1.1-3d
cp data/shortbread-1.1-3d.pmtiles examples/maplibre-3d/
npx serve examples/maplibre-3d

# for --schema smartmaps
cp data/smartmaps.pmtiles examples/maplibre-smartmaps/
npx serve examples/maplibre-smartmaps
```

Open the URL it prints. Add a location hash to land somewhere specific —
`#16/49.61/6.13/-20/60` is `zoom/lat/lng/bearing/pitch`, and a pitch is what makes the 3D
buildings visible. Click any building to see its attributes and their types.

Both pages accept `?pmtiles=<url>` to read an archive from somewhere else, so you do not
have to copy the file. They are validation tools rather than map styles — see
[examples/maplibre-smartmaps/README.md](./examples/maplibre-smartmaps/README.md).

### Other viewers

- <https://protomaps.github.io/PMTiles/> — drag a local `.pmtiles` in, inspect layers and
  attributes, no server needed
- the [go-pmtiles](https://github.com/protomaps/go-pmtiles) CLI can serve an archive as
  ordinary `z/x/y` tiles, for a client that does not speak PMTiles

### Hosting it

PMTiles is a single file served from static storage — no tile server. See
[docs.protomaps.com](https://docs.protomaps.com/) for S3, R2 and CDN setups, and
[USAGE.md](./USAGE.md#deploying-and-serving-tiles) for what this project does.

## 8. Cheat sheet

```bash
mvn package                                     # build once

# smallest possible end-to-end run
java -jar target/sourdough-builder-HEAD-with-deps.jar \
  --download --area monaco --schema smartmaps

# all four schemas for one region
for s in sourdough shortbread-1.1 shortbread-1.1-3d smartmaps; do
  java -jar target/sourdough-builder-HEAD-with-deps.jar \
    --download --area luxembourg --schema "$s" --force
done

# multilingual, and only the zooms you need
java -jar target/sourdough-builder-HEAD-with-deps.jar \
  --download --area switzerland --schema smartmaps \
  --additional-languages de,fr,it \
  --minzoom 4 --output ch.pmtiles

# fetch the input data now, build later (or offline)
java -jar target/sourdough-builder-HEAD-with-deps.jar \
  --download --only_download --area iceland
```
