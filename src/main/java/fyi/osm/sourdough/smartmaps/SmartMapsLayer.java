package fyi.osm.sourdough.smartmaps;

import com.onthegomap.planetiler.ForwardingProfile;

/** Common base for SmartMaps layer handlers: they all need the run configuration. */
public abstract class SmartMapsLayer implements ForwardingProfile.FeatureProcessor {

  protected final SmartMapsConfiguration config;

  protected SmartMapsLayer(SmartMapsConfiguration config) {
    this.config = config;
  }

  /**
   * The primary tile layer this handler produces. Several SmartMaps handlers emit into
   * more than one tile layer, because SmartMaps merges layers that Shortbread keeps
   * apart; this is the one that owns any post-processing.
   */
  public abstract String name();
}
