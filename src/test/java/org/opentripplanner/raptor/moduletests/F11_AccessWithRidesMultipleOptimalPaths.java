package org.opentripplanner.raptor.moduletests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.raptor._data.api.PathUtils.pathsToString;
import static org.opentripplanner.raptor._data.api.PathUtils.withoutCost;
import static org.opentripplanner.raptor._data.transit.TestAccessEgress.flex;
import static org.opentripplanner.raptor._data.transit.TestAccessEgress.free;
import static org.opentripplanner.raptor._data.transit.TestAccessEgress.walk;
import static org.opentripplanner.raptor._data.transit.TestRoute.route;
import static org.opentripplanner.raptor._data.transit.TestTransfer.transfer;
import static org.opentripplanner.raptor._data.transit.TestTripSchedule.schedule;
import static org.opentripplanner.raptor.moduletests.support.RaptorModuleTestConfig.TC_MIN_DURATION;
import static org.opentripplanner.raptor.moduletests.support.RaptorModuleTestConfig.TC_MIN_DURATION_REV;
import static org.opentripplanner.raptor.moduletests.support.RaptorModuleTestConfig.TC_STANDARD_ONE;
import static org.opentripplanner.raptor.moduletests.support.RaptorModuleTestConfig.TC_STANDARD_REV_ONE;
import static org.opentripplanner.raptor.moduletests.support.RaptorModuleTestConfig.multiCriteria;
import static org.opentripplanner.raptor.moduletests.support.RaptorModuleTestConfig.standard;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.raptor.RaptorService;
import org.opentripplanner.raptor._data.RaptorTestConstants;
import org.opentripplanner.raptor._data.transit.TestTransitData;
import org.opentripplanner.raptor._data.transit.TestTripSchedule;
import org.opentripplanner.raptor.api.request.RaptorRequestBuilder;
import org.opentripplanner.raptor.configure.RaptorConfig;
import org.opentripplanner.raptor.moduletests.support.RaptorModuleTestCase;
import org.opentripplanner.raptor.spi.DefaultSlackProvider;

/**
 * FEATURE UNDER TEST
 * <p>
 * This test focus on selecting the correct start on the journey, where a trip L1 competes with a
 * flex access to get to stop C. The Flex access arrives at C one minute after Trip L1 + walking,
 * but the flex arrives on-board. This should give the flex an advantage, the ability to transfer to
 * a nearby stop. In this test we manipulate the EGRESS for make the two paths optional. If we can
 * get both paths (start with flex access or trip L1) as optimal results by changing the egress,
 * then we have proven that both these results are kept in stop arrival at stop C.
 * <p>
 * Further this test also makes sure the path is constructed correctly when we have a mix of access,
 * transfer and transit at the same stop, in the same Raptor round. Two walking legs are not allowed
 * after each other.
 * <p>
 * <img src="images/F11.svg" width="548" height="206"/>
 * <p>
 * We use the same data and changes the egress walk leg to cover all cases. The egress walk leg
 * becomes optimal is it is 3 minutes, while the flex is optimal when we set the egress to 10
 * minutes. Trip L2 is faster than trip L1, but must be using the FLEX egress - since it arrive at
 * the stop C by walking.
 * <p>
 * Note! The 'earliest-departure-time' is set to 00:02, and the board and alight slacks are zero.
 */
public class F11_AccessWithRidesMultipleOptimalPaths implements RaptorTestConstants {

  private final RaptorService<TestTripSchedule> raptorService = new RaptorService<>(
    RaptorConfig.defaultConfigForTest()
  );
  private final TestTransitData data = new TestTransitData();
  private final RaptorRequestBuilder<TestTripSchedule> requestBuilder = new RaptorRequestBuilder<>();

  @BeforeEach
  public void setup() {
    data.withRoutes(
      route("L1", STOP_A, STOP_B).withTimetable(schedule("0:02 0:10")),
      route("L2", STOP_C, STOP_E).withTimetable(schedule("0:15 0:20")),
      route("L3", STOP_D, STOP_F).withTimetable(schedule("0:16 0:22"))
    );
    // We will test board- and alight-slack in a separate test
    data.withSlackProvider(new DefaultSlackProvider(D1m, D0s, D0s));

    requestBuilder
      .searchParams()
      .timetable(true)
      .earliestDepartureTime(T00_02)
      .latestArrivalTime(T00_30);

    requestBuilder.searchParams().addAccessPaths(free(STOP_A), flex(STOP_C, D11m));

    data.withTransfer(STOP_B, transfer(STOP_C, D2m)).withTransfer(STOP_C, transfer(STOP_D, D2m));

    // Set ModuleTestDebugLogging.DEBUG=true to enable debugging output
    ModuleTestDebugLogging.setupDebugLogging(data, requestBuilder);
  }

  /**
   * With 3m walk from Stop E to the destination.
   */
  static List<RaptorModuleTestCase> testCase3mWalkAccess() {
    var startFlexAccess = "Flex 11m 1x ~ C ";
    var startWalkAndL1 = "A ~ BUS L1 0:02 0:10 ~ B ~ Walk 2m ~ C ";
    var endWalkAndL3 = "~ Walk 2m ~ D ~ BUS L3 0:16 0:22 ~ F ";
    var endL2AndWalk = "~ BUS L2 0:15 0:20 ~ E ~ Walk 3m ";

    // Min-Duration 19m - this is
    var flexTransferTransit = startFlexAccess + endWalkAndL3 + "[0:02 0:22 20m 1tx $2580]";
    // Best duration 18m,
    var transitAndTransit = startWalkAndL1 + endL2AndWalk + "[0:02 0:23 21m 1tx]";
    // Min-Duration 19m
    var flexAndTransit = startFlexAccess + endL2AndWalk + "[0:03 0:23 20m 1tx $2640]";

    return RaptorModuleTestCase
      .of()
      .add(TC_MIN_DURATION, withoutCost(transitAndTransit))
      // The expected min-duration is 'transitAndTransit', but the path
      // does not have min_duration (without wait-time) and the min-duration is not
      // part of the pareto-set criteria for the path; Hence we get the wrong path.
      // By using the debugger we se that the arrival-states are correct.
      // TODO - Add the routing criteria to path and make sure the pareto functions
      //      - for the paths uses the correct criteria
      .add(TC_MIN_DURATION_REV, withoutCost(flexAndTransit))
      .add(standard().manyIterations(), withoutCost(flexTransferTransit, flexAndTransit))
      .add(TC_STANDARD_ONE, withoutCost(flexTransferTransit))
      .add(TC_STANDARD_REV_ONE, withoutCost(flexAndTransit))
      .add(multiCriteria(), flexTransferTransit, flexAndTransit)
      .build();
  }

  @ParameterizedTest
  @MethodSource("testCase3mWalkAccess")
  void test3mWalkAccess(RaptorModuleTestCase testCase) {
    requestBuilder.searchParams().addEgressPaths(free(STOP_F), walk(STOP_E, D3m));
    var request = testCase.withConfig(requestBuilder);

    var response = raptorService.route(request, data);

    assertEquals(testCase.expected(), pathsToString(response));
  }

  /**
   * With 1m walk from Stop E to the destination.
   */
  static List<RaptorModuleTestCase> testCase1mWalkAccess() {
    var startFlexAccess = "Flex 11m 1x ~ C ";
    var startWalkAndL1 = "A ~ BUS L1 0:02 0:10 ~ B ~ Walk 2m ~ C ";
    var endWalkAndL3 = "~ Walk 2m ~ D ~ BUS L3 0:16 0:22 ~ F ";
    var endL2AndWalk = "~ BUS L2 0:15 0:20 ~ E ~ Walk 1m ";

    // Min-Duration 19m - this is
    var flexTransferTransit = startFlexAccess + endWalkAndL3 + "[0:02 0:22 20m 1tx $2580]";
    // Best duration 16m,
    var transitAndTransit = startWalkAndL1 + endL2AndWalk + "[0:02 0:21 19m 1tx]";
    // Min-Duration 17m
    var flexAndTransit = startFlexAccess + endL2AndWalk + "[0:03 0:21 18m 1tx $2400]";

    return RaptorModuleTestCase
      .of()
      .add(TC_MIN_DURATION, withoutCost(transitAndTransit))
      // TODO - Expect 'transitAndTransit', see test above for details
      .add(TC_MIN_DURATION_REV, withoutCost(flexAndTransit))
      .add(standard().manyIterations(), withoutCost(flexAndTransit))
      .add(TC_STANDARD_ONE, withoutCost(transitAndTransit))
      .add(TC_STANDARD_REV_ONE, withoutCost(flexAndTransit))
      .add(multiCriteria(), flexAndTransit)
      .build();
  }

  @ParameterizedTest
  @MethodSource("testCase1mWalkAccess")
  void test1mWalkAccess(RaptorModuleTestCase testCase) {
    requestBuilder.searchParams().addEgressPaths(free(STOP_F), walk(STOP_E, D1m));
    var request = testCase.withConfig(requestBuilder);

    var response = raptorService.route(request, data);

    assertEquals(testCase.expected(), pathsToString(response));
  }
}
