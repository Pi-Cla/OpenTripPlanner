package org.opentripplanner.gtfs.mapping;

import java.time.Duration;
import java.util.Currency;
import org.opentripplanner.ext.fares.model.FareContainer;
import org.opentripplanner.ext.fares.model.FareProduct;
import org.opentripplanner.ext.fares.model.RiderCategory;
import org.opentripplanner.routing.core.Money;

public class FareProductMapper {

  public static int NOT_SET = -999;

  public FareProduct map(org.onebusaway.gtfs.model.FareProduct fareProduct) {
    var id = AgencyAndIdMapper.mapAgencyAndId(fareProduct.getId());
    var price = new Money(
      Currency.getInstance(fareProduct.getCurrency()),
      (int) (fareProduct.getAmount() * 100)
    );

    Duration duration = null;
    if (fareProduct.getDurationUnit() != NOT_SET) {
      duration = toTemporalUnit(fareProduct.getDurationUnit(), fareProduct.getDurationAmount());
    }
    return new FareProduct(
      id,
      fareProduct.getName(),
      price,
      duration,
      mapRiderCategory(fareProduct.getRiderCategory()),
      toInternalModel(fareProduct.egetFareContainer())
    );
  }

  private static RiderCategory mapRiderCategory(
    org.onebusaway.gtfs.model.RiderCategory riderCategory
  ) {
    if (riderCategory == null) {
      return null;
    } else {
      return new RiderCategory(
        riderCategory.getId().getId(),
        riderCategory.getName(),
        riderCategory.getEligibilityUrl()
      );
    }
  }

  private static TemporalAmount toTemporalAmount(int unit, int amount) {
    return switch (unit) {
      case 0 -> Duration.ofSeconds(amount);
      case 1 -> Duration.ofMinutes(amount);
      case 2 -> Duration.ofHours(amount);
      case 3 -> Duration.ofDays(amount);
      case 4 -> Duration.ofDays(amount * 7L);
      case 5 -> Duration.ofDays(amount * 31L); // not totally right but good enough
      case 6 -> Duration.ofDays(amount * 365L);
      default -> throw new IllegalStateException("Unexpected value: " + unit);
    };
  }

  private static FareContainer toInternalModel(org.onebusaway.gtfs.model.FareContainer c) {
    if (c == null) {
      return null;
    } else {
      return new FareContainer(c.getId().getId(), c.getName());
    }
  }
}
