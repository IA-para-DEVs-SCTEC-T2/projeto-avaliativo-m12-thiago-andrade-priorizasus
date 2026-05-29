package com.priorizasus.priorizasus.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Links a public service method to its specification requirement.
 *
 * <p>Every new public service method must carry this annotation so that {@code
 * SpecDriftDetectionTest} can verify spec↔code traceability.
 *
 * <p>Format: feature-prefix + three-digit number (e.g., {@code @ReqId("PM-001")}).
 *
 * @see com.priorizasus.priorizasus.harness.SpecDriftDetectionTest
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ReqId {

  /** The REQ-ID from the feature spec (e.g., "PM-001", "SA-003"). */
  String value();
}
