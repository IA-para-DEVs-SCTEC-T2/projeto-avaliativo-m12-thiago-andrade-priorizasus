package com.priorizasus.priorizasus.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GlobalModelAttributesTest {

  @Mock private ClinicTimeZone clinicTimeZone;

  private GlobalModelAttributes globalModelAttributes;

  @BeforeEach
  void setUp() {
    globalModelAttributes = new GlobalModelAttributes(clinicTimeZone);
  }

  @Test
  @DisplayName("brtTime returns formatted time")
  void brtTimeReturnsFormatted() {
    when(clinicTimeZone.formatForDisplay(org.mockito.ArgumentMatchers.any()))
        .thenReturn("28/05/2026 15:30");

    String result = globalModelAttributes.brtTime();

    assertEquals("28/05/2026 15:30", result);
  }

  @Test
  @DisplayName("today delegates to clinicTimeZone")
  void todayDelegates() {
    var date = LocalDate.of(2026, 5, 28);
    when(clinicTimeZone.today()).thenReturn(date);

    assertEquals(date, globalModelAttributes.today());
  }

  @Test
  @DisplayName("clinicTimeZone returns the instance")
  void clinicTimeZoneReturnsInstance() {
    assertSame(clinicTimeZone, globalModelAttributes.clinicTimeZone());
  }

  @Test
  @DisplayName("patient returns new Patient instance")
  void patientReturnsNewPatient() {
    assertNotNull(globalModelAttributes.patient());
  }
}
