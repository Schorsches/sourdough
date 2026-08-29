package fyi.osm.sourdough.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.reader.SourceFeature;
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadLayer;
import fyi.osm.sourdough.shortbread.ShortbreadSchema;
import java.util.List;
import java.util.Map;

/** Shortbread `aerialways`: cable cars, gondolas and ski lifts, from zoom 12. */
public class Aerialways extends ShortbreadLayer {

  public Aerialways(ShortbreadConfiguration config) {
    super(config);
  }

  private static final int MIN_ZOOM = 12;

  /**
   * OSM aerialway value to Shortbread kind. Almost all are identical, but the schema
   * spells rope tows with a hyphen while OSM uses an underscore.
   */
  private static final Map<String, String> KINDS = Map.ofEntries(
    Map.entry("cable_car", "cable_car"),
    Map.entry("gondola", "gondola"),
    Map.entry("goods", "goods"),
    Map.entry("chair_lift", "chair_lift"),
    Map.entry("drag_lift", "drag_lift"),
    Map.entry("t-bar", "t-bar"),
    Map.entry("j-bar", "j-bar"),
    Map.entry("platter", "platter"),
    Map.entry("rope_tow", "rope-tow")
  );

  @Override
  public String name() {
    return ShortbreadSchema.AERIALWAYS;
  }

  @Override
  public Expression filter() {
    return Expression.matchAny("aerialway", List.copyOf(KINDS.keySet()));
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    if (!sf.canBeLine()) return;
    var kind = KINDS.get(sf.getString("aerialway"));
    if (kind == null) return;

    fc.line(name())
      .setMinZoom(MIN_ZOOM)
      .setMinPixelSize(0)
      .setBufferPixels(4)
      .setAttr("kind", kind);
  }
}
