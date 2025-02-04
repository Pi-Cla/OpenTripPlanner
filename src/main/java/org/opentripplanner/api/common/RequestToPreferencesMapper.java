package org.opentripplanner.api.common;

import jakarta.validation.constraints.NotNull;
import java.util.function.Consumer;
import org.opentripplanner.framework.lang.ObjectUtils;
import org.opentripplanner.routing.algorithm.filterchain.api.TransitGeneralizedCostFilterParams;
import org.opentripplanner.routing.api.request.framework.CostLinearFunction;
import org.opentripplanner.routing.api.request.preference.ItineraryFilterPreferences;
import org.opentripplanner.routing.api.request.preference.Relax;
import org.opentripplanner.routing.api.request.preference.RoutingPreferences;
import org.opentripplanner.routing.api.request.preference.VehicleParkingPreferences;
import org.opentripplanner.routing.core.BicycleOptimizeType;

class RequestToPreferencesMapper {

  private final RoutingResource req;
  private final RoutingPreferences.Builder preferences;
  private final boolean isPlannedForNow;

  RequestToPreferencesMapper(
    RoutingResource req,
    RoutingPreferences.Builder preferences,
    boolean isPlannedForNow
  ) {
    this.req = req;
    this.preferences = preferences;
    this.isPlannedForNow = isPlannedForNow;
  }

  void map() {
    mapCar();
    mapWalk();
    mapBike();

    var boardAndAlightSlack = mapTransit();

    mapTransfer(boardAndAlightSlack);

    maptRental();
    mapItineraryFilter();
    mapSystem();
  }

  private void mapCar() {
    preferences.withCar(car -> {
      setIfNotNull(req.carReluctance, car::withReluctance);
      car.withParking(parking -> {
        mapParking(parking);
        setIfNotNull(req.carParkCost, parking::withParkCost);
        setIfNotNull(req.carParkTime, parking::withParkTime);
      });
    });
  }

  private void mapWalk() {
    preferences.withWalk(walk -> {
      setIfNotNull(req.walkReluctance, walk::withReluctance);
      setIfNotNull(req.walkSpeed, walk::withSpeed);
      setIfNotNull(req.walkBoardCost, walk::withBoardCost);
      setIfNotNull(req.walkSafetyFactor, walk::withSafetyFactor);
    });
  }

  private void mapBike() {
    preferences.withBike(bike -> {
      setIfNotNull(req.bikeSpeed, bike::withSpeed);
      setIfNotNull(req.bikeReluctance, bike::withReluctance);
      setIfNotNull(req.bikeBoardCost, bike::withBoardCost);
      setIfNotNull(req.bikeWalkingSpeed, bike::withWalkingSpeed);
      setIfNotNull(req.bikeWalkingReluctance, bike::withWalkingReluctance);
      setIfNotNull(req.bikeSwitchTime, bike::withSwitchTime);
      setIfNotNull(req.bikeSwitchCost, bike::withSwitchCost);
      setIfNotNull(req.bikeOptimizeType, bike::withOptimizeType);

      if (req.bikeOptimizeType == BicycleOptimizeType.TRIANGLE) {
        bike.withOptimizeTriangle(triangle -> {
          setIfNotNull(req.triangleTimeFactor, triangle::withTime);
          setIfNotNull(req.triangleSlopeFactor, triangle::withSlope);
          setIfNotNull(req.triangleSafetyFactor, triangle::withSafety);
        });
      }

      bike.withParking(parking -> {
        mapParking(parking);
        setIfNotNull(req.bikeParkCost, parking::withParkCost);
        setIfNotNull(req.bikeParkTime, parking::withParkTime);
      });
    });
  }

  private BoardAndAlightSlack mapTransit() {
    preferences.withTransit(tr -> {
      setIfNotNull(req.boardSlack, tr::withDefaultBoardSlackSec);
      setIfNotNull(req.alightSlack, tr::withDefaultAlightSlackSec);
      setIfNotNull(req.otherThanPreferredRoutesPenalty, tr::setOtherThanPreferredRoutesPenalty);
      setIfNotNull(req.ignoreRealtimeUpdates, tr::setIgnoreRealtimeUpdates);

      if (req.relaxTransitPriorityGroup != null) {
        tr.withTransitGroupPriorityGeneralizedCostSlack(
          CostLinearFunction.of(req.relaxTransitPriorityGroup)
        );
      } else {
        setIfNotNull(
          req.relaxTransitSearchGeneralizedCostAtDestination,
          v -> tr.withRaptor(r -> r.withRelaxGeneralizedCostAtDestination(v))
        );
      }
    });

    return new BoardAndAlightSlack(
      preferences.transit().boardSlack().defaultValueSeconds() +
      preferences.transit().alightSlack().defaultValueSeconds()
    );
  }

  private void mapTransfer(BoardAndAlightSlack boardAndAlightSlack) {
    preferences.withTransfer(transfer -> {
      setIfNotNull(req.waitReluctance, transfer::withWaitReluctance);
      setIfNotNull(req.transferPenalty, transfer::withCost);

      if (req.minTransferTime != null) {
        if (boardAndAlightSlack.value > req.minTransferTime) {
          throw new IllegalArgumentException(
            "Invalid parameters: 'minTransferTime' must be greater than or equal to board slack plus alight slack"
          );
        }
        transfer.withSlack(req.minTransferTime - boardAndAlightSlack.value);
      }

      setIfNotNull(req.nonpreferredTransferPenalty, transfer::withNonpreferredCost);
      setIfNotNull(req.maxTransfers, transfer::withMaxTransfers);
      setIfNotNull(req.maxAdditionalTransfers, transfer::withMaxAdditionalTransfers);
    });
  }

  private void maptRental() {
    preferences.withRental(rental -> {
      setIfNotNull(
        req.keepingRentedBicycleAtDestinationCost,
        rental::withArrivingInRentalVehicleAtDestinationCost
      );
      rental.withUseAvailabilityInformation(isPlannedForNow);
    });
  }

  private void mapItineraryFilter() {
    preferences.withItineraryFilter(filter -> {
      setIfNotNull(req.debugItineraryFilter, filter::withDebug);
      setIfNotNull(req.groupSimilarityKeepOne, filter::withGroupSimilarityKeepOne);
      setIfNotNull(req.groupSimilarityKeepThree, filter::withGroupSimilarityKeepThree);
      setIfNotNull(
        req.groupedOtherThanSameLegsMaxCostMultiplier,
        filter::withGroupedOtherThanSameLegsMaxCostMultiplier
      );
      filter.withTransitGeneralizedCostLimit(mapTransitGeneralizedCostFilterParams(filter));
      setIfNotNull(
        req.nonTransitGeneralizedCostLimitFunction,
        it -> filter.withNonTransitGeneralizedCostLimit(CostLinearFunction.of(it))
      );
    });
  }

  private TransitGeneralizedCostFilterParams mapTransitGeneralizedCostFilterParams(
    ItineraryFilterPreferences.Builder filter
  ) {
    var costLimitFunction = (req.transitGeneralizedCostLimitFunction == null)
      ? filter.original().transitGeneralizedCostLimit().costLimitFunction()
      : CostLinearFunction.of(req.transitGeneralizedCostLimitFunction);

    var intervalRelaxFactor = ObjectUtils.ifNotNull(
      req.transitGeneralizedCostLimitIntervalRelaxFactor,
      filter.original().transitGeneralizedCostLimit().intervalRelaxFactor()
    );

    return new TransitGeneralizedCostFilterParams(costLimitFunction, intervalRelaxFactor);
  }

  private void mapParking(VehicleParkingPreferences.Builder builder) {
    builder.withRequiredVehicleParkingTags(req.requiredVehicleParkingTags);
    builder.withBannedVehicleParkingTags(req.bannedVehicleParkingTags);
  }

  private void mapSystem() {
    preferences.withSystem(system -> {
      setIfNotNull(req.geoidElevation, system::withGeoidElevation);
    });
  }

  static <T> void setIfNotNull(T value, @NotNull Consumer<T> body) {
    if (value != null) {
      body.accept(value);
    }
  }

  static <T> void mapRelaxIfNotNull(String fx, @NotNull Consumer<Relax> body) {
    if (fx == null) {
      return;
    }
    var a = fx.split("[\\sxXuUvVtT*+]+");
    if (a.length != 2) {
      return;
    }
    body.accept(new Relax(Double.parseDouble(a[0]), Integer.parseInt(a[1])));
  }

  /**
   * The combined value of board and alight slack is used in the initialization of transfer
   * preferences.
   * <p>
   * This class is used to pass a type-safe value from one phase of the mapping to another. This
   * enforces the mapping order: 'transit' before 'transfer'. */
  private record BoardAndAlightSlack(int value) {}
}
