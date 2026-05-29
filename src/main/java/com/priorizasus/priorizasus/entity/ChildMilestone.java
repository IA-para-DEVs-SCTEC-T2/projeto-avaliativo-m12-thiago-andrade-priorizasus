package com.priorizasus.priorizasus.entity;

/**
 * Ministry of Health puericulture milestones for CHILD category.
 *
 * <p>Each milestone has a {@link #daysFromBirth} used in {@code TargetDateCalculator} to determine
 * the next consultation deadline. Canonical term per CONTEXT.md.
 */
public enum ChildMilestone {
  DAY_7(7),
  DAY_30(30),
  MONTH_2(60),
  MONTH_4(120),
  MONTH_6(180),
  MONTH_9(270),
  MONTH_12(365),
  MONTH_18(547),
  MONTH_24(730),
  ANNUAL(-1); // dynamic: next birthday after age 2

  private final int daysFromBirth;

  ChildMilestone(int daysFromBirth) {
    this.daysFromBirth = daysFromBirth;
  }

  /** Days from birth when this milestone is due. ANNUAL returns -1 (dynamic). */
  public int getDaysFromBirth() {
    return daysFromBirth;
  }
}
