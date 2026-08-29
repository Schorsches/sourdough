package fyi.osm.sourdough.smartmaps;

import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.GeometryException;
import java.util.List;

/**
 * Post-processing for a tile layer that some handler emits into but does not own.
 *
 * Planetiler keys post-processors by layer name, and a handler declares exactly one name.
 * Several SmartMaps handlers write into two layers -- this layout merges layers Shortbread
 * keeps apart -- so the second layer would silently get no post-processing at all: no
 * error, no missing layer, just unmerged geometry and a fatter tile.
 *
 * Registering one of these alongside the handler makes the second layer's treatment
 * explicit and visible in the profile's layer list.
 */
public record SecondaryLayer(String layer, Merge merge)
  implements ForwardingProfile.LayerPostProcessor {

  /** What to do with the layer's features, at a given zoom. */
  @FunctionalInterface
  public interface Merge {
    List<VectorTile.Feature> apply(int zoom, List<VectorTile.Feature> items)
      throws GeometryException;
  }

  @Override
  public String name() {
    return layer;
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items)
    throws GeometryException {
    return merge.apply(zoom, items);
  }
}
