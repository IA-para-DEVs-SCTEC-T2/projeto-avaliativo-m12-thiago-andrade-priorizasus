package com.priorizasus.priorizasus.entity;

/** Slot lifecycle states per CM-004. Canonical term per CONTEXT.md. */
public enum SlotStatus {
  AVAILABLE,
  RESERVED,
  BOOKED,
  CANCELLED,
  EXPIRED
}
