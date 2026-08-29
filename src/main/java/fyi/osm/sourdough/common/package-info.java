/**
 * Helpers shared by every tile schema this project produces.
 *
 * The rule for this package: <strong>nothing here may depend on a schema.</strong> No
 * imports from {@code fyi.osm.sourdough.shortbread} or {@code fyi.osm.sourdough.smartmaps},
 * and no tile-layer names. What belongs here is OpenStreetMap tag semantics, projection
 * maths, and algorithms over those — things that are true regardless of which schema is
 * being written.
 *
 * The distinction that matters most in practice is computation versus naming. The
 * building-height algorithm is shared because every schema needs the same answer; the
 * code that decides whether to call the result {@code height} or {@code render_height}
 * belongs to a schema, not here.
 *
 * Road-class ordering in {@link fyi.osm.sourdough.common.ZOrder} is a deliberate
 * inclusion: it encodes the relative importance of OSM highway values, which is general
 * rather than any one schema's invention.
 *
 * {@code PackageBoundaryTest} enforces the rule, so it cannot quietly erode.
 */
package fyi.osm.sourdough.common;
