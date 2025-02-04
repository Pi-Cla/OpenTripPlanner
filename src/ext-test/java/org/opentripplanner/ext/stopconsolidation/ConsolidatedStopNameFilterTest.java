package org.opentripplanner.ext.stopconsolidation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.opentripplanner.ext.stopconsolidation.TestStopConsolidationModel.STOP_C;
import static org.opentripplanner.ext.stopconsolidation.TestStopConsolidationModel.STOP_D;
import static org.opentripplanner.model.plan.PlanTestConstants.T11_05;
import static org.opentripplanner.model.plan.PlanTestConstants.T11_12;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.ext.stopconsolidation.internal.DefaultStopConsolidationRepository;
import org.opentripplanner.ext.stopconsolidation.internal.DefaultStopConsolidationService;
import org.opentripplanner.ext.stopconsolidation.model.ConsolidatedStopGroup;
import org.opentripplanner.model.plan.Place;
import org.opentripplanner.model.plan.PlanTestConstants;
import org.opentripplanner.model.plan.TestItineraryBuilder;

class ConsolidatedStopNameFilterTest {

  @Test
  void changeNames() {
    var transitModel = TestStopConsolidationModel.buildTransitModel();

    var groups = List.of(new ConsolidatedStopGroup(STOP_C.getId(), List.of(STOP_D.getId())));
    var repo = new DefaultStopConsolidationRepository();
    repo.addGroups(groups);

    var service = new DefaultStopConsolidationService(repo, transitModel);
    var filter = new ConsolidatedStopNameFilter(service);

    var itinerary = TestItineraryBuilder
      .newItinerary(Place.forStop(STOP_C))
      .bus(TestStopConsolidationModel.ROUTE, 1, T11_05, T11_12, Place.forStop(STOP_C))
      .bus(1, T11_05, T11_12, PlanTestConstants.E)
      .bus(1, T11_05, T11_12, PlanTestConstants.F)
      .build();

    var filtered = filter.filter(List.of(itinerary));
    assertFalse(filtered.isEmpty());

    var updatedLeg = filtered.get(0).getLegs().get(0);
    assertEquals(STOP_D.getName(), updatedLeg.getFrom().name);
    assertEquals(STOP_D.getName(), updatedLeg.getTo().name);
  }
}
