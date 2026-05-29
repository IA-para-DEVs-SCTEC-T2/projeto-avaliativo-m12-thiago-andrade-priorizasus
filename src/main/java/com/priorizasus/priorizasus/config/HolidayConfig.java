package com.priorizasus.priorizasus.config;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Holiday configuration per CM-002 edge case.
 *
 * <p>Binds to {@code clinic.holidays.*} properties. On holidays, no Slots are created. Empty by
 * default — holidays are configured per clinic.
 */
@ConfigurationProperties(prefix = "clinic.holidays")
public class HolidayConfig {

  /** Comma-separated holiday dates in yyyy-MM-dd format. */
  private String dates = "";

  public Set<LocalDate> getHolidayDates() {
    if (dates == null || dates.isBlank()) {
      return Collections.emptySet();
    }
    return Stream.of(dates.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(LocalDate::parse)
        .collect(Collectors.toSet());
  }

  public boolean isHoliday(LocalDate date) {
    return getHolidayDates().contains(date);
  }

  public String getDates() {
    return dates;
  }

  public void setDates(String dates) {
    this.dates = dates;
  }
}
