package com.priorizasus.priorizasus.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CategoryTest {

  @Test
  void prenatalWeightWithUltrasound36plus() {
    Category c = new Category();
    c.setType(CategoryType.PRENATAL);
    LocalDate today = LocalDate.now();
    c.setUltrasoundDate(today.minusWeeks(36));
    c.setGestationalWeeksAtUltrasound(36);
    int weight = c.getWeight(today);
    assertTrue(weight >= 1000);
  }

  @Test
  void prenatalWeightBetween28And36() {
    Category c = new Category();
    c.setType(CategoryType.PRENATAL);
    LocalDate today = LocalDate.now();
    c.setUltrasoundDate(today.minusDays(7));
    c.setGestationalWeeksAtUltrasound(30);
    int weight = c.getWeight(today);
    assertEquals(500, weight);
  }

  @Test
  void chronicHasFixedWeight() {
    Category c = new Category();
    c.setType(CategoryType.CHRONIC);
    int weight = c.getWeight(LocalDate.now());
    assertEquals(200, weight);
  }

  @Test
  void childDefaultWeight() {
    Category c = new Category();
    c.setType(CategoryType.CHILD);
    int weight = c.getWeight(LocalDate.now());
    assertEquals(700, weight);
  }

  @Test
  void prenatalWithoutUltrasoundReturnsDefaultWeight() {
    Category c = new Category();
    c.setType(CategoryType.PRENATAL);
    // no ultrasound data set
    int weight = c.getWeight(LocalDate.now());
    assertEquals(300, weight);
  }

  @Test
  void prenatalBelow28WeeksReturns300() {
    Category c = new Category();
    c.setType(CategoryType.PRENATAL);
    LocalDate today = LocalDate.now();
    c.setUltrasoundDate(today.minusDays(7));
    c.setGestationalWeeksAtUltrasound(10);
    int weight = c.getWeight(today);
    assertEquals(300, weight);
  }

  @Test
  void unknownTypeReturnsZero() {
    Category c = new Category();
    // type not set — null
    int weight = c.getWeight(LocalDate.now());
    assertEquals(0, weight);
  }
}
