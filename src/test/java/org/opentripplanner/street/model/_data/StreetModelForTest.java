package org.opentripplanner.street.model._data;

import static org.opentripplanner.transit.model._data.TransitModelForTest.id;

import javax.annotation.Nonnull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opentripplanner.framework.geometry.GeometryUtils;
import org.opentripplanner.framework.geometry.SphericalDistanceLibrary;
import org.opentripplanner.framework.geometry.WgsCoordinate;
import org.opentripplanner.framework.i18n.I18NString;
import org.opentripplanner.street.model.StreetTraversalPermission;
import org.opentripplanner.street.model.edge.StreetEdge;
import org.opentripplanner.street.model.edge.StreetEdgeBuilder;
import org.opentripplanner.street.model.vertex.IntersectionVertex;
import org.opentripplanner.street.model.vertex.LabelledIntersectionVertex;
import org.opentripplanner.street.model.vertex.StreetVertex;
import org.opentripplanner.street.model.vertex.TransitEntranceVertex;
import org.opentripplanner.transit.model.site.Entrance;

public class StreetModelForTest {

  public static StreetVertex V1 = intersectionVertex("V1", 0, 0);
  public static StreetVertex V2 = intersectionVertex("V2", 1, 1);
  public static StreetVertex V3 = intersectionVertex("V3", 2, 2);
  public static StreetVertex V4 = intersectionVertex("V4", 3, 3);

  public static IntersectionVertex intersectionVertex(Coordinate c) {
    return intersectionVertex(c.x, c.y);
  }

  public static IntersectionVertex intersectionVertex(double lat, double lon) {
    var label = "%s_%s".formatted(lat, lon);
    return new LabelledIntersectionVertex(label, lat, lon, false, false);
  }

  public static IntersectionVertex intersectionVertex(String label, double lat, double lon) {
    return new LabelledIntersectionVertex(label, lat, lon, false, false);
  }

  @Nonnull
  public static TransitEntranceVertex transitEntranceVertex(String id, double lat, double lon) {
    var entrance = Entrance
      .of(id(id))
      .withCoordinate(new WgsCoordinate(lat, lon))
      .withName(I18NString.of(id))
      .build();
    return new TransitEntranceVertex(entrance);
  }

  public static StreetEdge streetEdge(StreetVertex vA, StreetVertex vB) {
    var meters = SphericalDistanceLibrary.distance(vA.getCoordinate(), vB.getCoordinate());
    return streetEdge(vA, vB, meters, StreetTraversalPermission.ALL);
  }

  public static StreetEdgeBuilder<?> streetEdgeBuilder(
    StreetVertex vA,
    StreetVertex vB,
    double length,
    StreetTraversalPermission perm
  ) {
    var labelA = vA.getLabel();
    var labelB = vB.getLabel();
    String name = String.format("%s_%s", labelA, labelB);
    Coordinate[] coords = new Coordinate[2];
    coords[0] = vA.getCoordinate();
    coords[1] = vB.getCoordinate();
    LineString geom = GeometryUtils.getGeometryFactory().createLineString(coords);

    return new StreetEdgeBuilder<>()
      .withFromVertex(vA)
      .withToVertex(vB)
      .withGeometry(geom)
      .withName(name)
      .withMeterLength(length)
      .withPermission(perm)
      .withBack(false);
  }

  public static StreetEdge streetEdge(
    StreetVertex vA,
    StreetVertex vB,
    double length,
    StreetTraversalPermission perm
  ) {
    return streetEdgeBuilder(vA, vB, length, perm).buildAndConnect();
  }

  public static StreetEdge streetEdge(
    StreetVertex from,
    StreetVertex to,
    StreetTraversalPermission permissions
  ) {
    return streetEdge(from, to, 1, permissions);
  }
}
