package fyi.osm.sourdough.shortbread.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import fyi.osm.sourdough.shortbread.ShortbreadConfiguration;
import fyi.osm.sourdough.shortbread.ShortbreadLayer;
import fyi.osm.sourdough.shortbread.ShortbreadNames;
import fyi.osm.sourdough.shortbread.ShortbreadSchema;
import java.util.List;

/**
 * Shortbread `ferries`: ferry routes as lines.
 *
 * Ferries that carry motor vehicles appear two zooms earlier than the rest, but both get
 * the same `kind`, so the two classes are distinguishable only by the zoom at which they
 * show up. That is what the specification says.
 */
public class Ferries extends ShortbreadLayer implements ForwardingProfile.LayerPostProcessor {

  public Ferries(ShortbreadConfiguration config) {
    super(config);
  }

  private static final int MOTOR_VEHICLE_MIN_ZOOM = 10;
  private static final int OTHER_MIN_ZOOM = 12;
  private static final String KIND = "ferry";

  @Override
  public String name() {
    return ShortbreadSchema.FERRIES;
  }

  @Override
  public Expression filter() {
    return Expression.matchAny("route", "ferry");
  }

  @Override
  public void processFeature(SourceFeature sf, FeatureCollector fc) {
    if (!sf.canBeLine()) return;
    if (!sf.hasTag("route", "ferry")) return;

    boolean carriesMotorVehicles = !sf.hasTag("motor_vehicle", "no");

    var line = fc.line(name());
    line.setMinZoom(carriesMotorVehicles ? MOTOR_VEHICLE_MIN_ZOOM : OTHER_MIN_ZOOM);
    line.setMinPixelSize(0);
    line.setBufferPixels(4);
    line.setAttr("kind", KIND);
    ShortbreadNames.setNames(sf, line, config.languages());
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items)
    throws GeometryException {
    return FeatureMerge.mergeLineStrings(items, 0, 0.25, 4);
  }
}
