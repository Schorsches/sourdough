package fyi.osm.sourdough.shortbread;

import com.onthegomap.planetiler.ForwardingProfile;

/** Common base for Shortbread layer handlers: they all need the run configuration. */
public abstract class ShortbreadLayer implements ForwardingProfile.FeatureProcessor {

  protected final ShortbreadConfiguration config;

  protected ShortbreadLayer(ShortbreadConfiguration config) {
    this.config = config;
  }

  /**
   * The primary tile layer this handler produces. Some handlers emit into more than one
   * tile layer (a layer and its matching label layer, say); this is the one that owns
   * any post-processing.
   */
  public abstract String name();
}
