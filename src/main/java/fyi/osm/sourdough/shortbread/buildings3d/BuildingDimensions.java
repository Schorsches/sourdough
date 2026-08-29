package fyi.osm.sourdough.shortbread.buildings3d;

/**
 * Normalized 3D dimensions of a building or building part, in meters.
 *
 * A null field means "OpenStreetMap does not say and we will not guess". Nothing here is
 * invented: {@code height} is either an explicit tag or derived from a level count, and
 * {@code estimated} records which.
 *
 * @param height total height from ground to the top of the structure, including its roof
 * @param minHeight height of the bottom of the structure above ground, 0 for most things
 * @param roofHeight the part of {@code height} taken up by the roof
 * @param levels above-ground level count, as tagged
 * @param estimated true when {@code height} was derived from levels rather than measured
 */
public record BuildingDimensions(
  Double height,
  Double minHeight,
  Double roofHeight,
  Integer levels,
  boolean estimated
) {

  public static final BuildingDimensions EMPTY =
    new BuildingDimensions(null, null, null, null, false);

  public boolean hasHeight() {
    return height != null;
  }
}
