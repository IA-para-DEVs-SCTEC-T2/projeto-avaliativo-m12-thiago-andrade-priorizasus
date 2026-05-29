package com.priorizasus.priorizasus.config;

import com.priorizasus.priorizasus.entity.Category;
import com.priorizasus.priorizasus.entity.CategoryType;
import com.priorizasus.priorizasus.entity.Patient;
import com.priorizasus.priorizasus.entity.PatientStatus;
import java.time.LocalDate;
import java.util.List;

final class PatientSeedDataFactory {

  private static final String[] NAMES = {
    "Thiago Andrade", "Marina Silva", "Carlos Oliveira", "Ana Souza", "Rafael Lima",
    "Juliana Costa", "Fernando Pereira", "Beatriz Santos", "Lucas Almeida", "Camila Rocha",
    "Gabriel Nunes", "Isabela Vieira", "Eduardo Mendes", "Larissa Cardoso", "Pedro Henrique",
    "Natália Ribeiro", "Bruno Carvalho", "Vanessa Cruz", "Ricardo Borges", "Patrícia Freitas",
    "André Martins", "Débora Moreira", "Felipe Teixeira", "Aline Barbosa", "Diego Gonçalves",
    "Tatiane Lopes", "Marcelo Azevedo", "Sabrina Farias", "Leonardo Cunha", "Carla Duarte",
    "Alexandre Pires", "Renata Neves", "Fábio Monteiro", "Mônica Tavares", "Vitor Siqueira",
    "Jéssica Moura", "Hugo Barros", "Lorena Aguiar", "Gustavo Reis", "Priscila Bittencourt",
    "Daniel Peixoto", "Flávia Santana", "Igor Campos", "Renato Xavier", "Adriana Fontes",
    "Sérgio Melo", "Clarice Dantas", "Augusto Macedo", "Elaine Prado", "Maurício Fonseca"
  };

  private PatientSeedDataFactory() {}

  static String name(int index) {
    return NAMES[index - 1];
  }

  static Patient buildPatient(int index, LocalDate today) {
    Patient patient = new Patient();
    patient.setName(NAMES[index - 1]);
    patient.setCpf(String.format("%011d", index));
    patient.setBirthDate(today.minusYears(20 + (index % 15)));
    patient.setPhone(String.format("119%08d", 10000000 + index));
    patient.setEmail(
        String.format("%s@PRIORIZASUS.test", NAMES[index - 1].toLowerCase().replace(' ', '.')));
    patient.setAddress(String.format("Rua Teste, %d", index));
    patient.setStatus(PatientStatus.ACTIVE);
    patient.setLastConsultationDate(today.minusDays(14 + (index % 4)));
    patient.setCategories(buildCategories(index, today));

    if (index <= 10) {
      patient.setTargetDate(today.minusDays(21 + (index - 1)));
    } else if (index <= 20) {
      patient.setTargetDate(today.minusDays(11 + (index - 11)));
    } else if (index <= 30) {
      patient.setTargetDate(today.minusDays(index - 20));
    } else if (index <= 40) {
      patient.setTargetDate(today.minusDays(index - 31));
    } else {
      patient.setTargetDate(today.minusDays(index - 41));
    }

    return patient;
  }

  static List<Category> buildCategories(int index, LocalDate today) {
    if (index <= 10) {
      return List.of(createPrenatalCategory(today, 37), createCategory(CategoryType.CHRONIC));
    }
    if (index <= 20) {
      return List.of(createCategory(CategoryType.CHILD), createCategory(CategoryType.CHRONIC));
    }
    if (index <= 30) {
      return List.of(createCategory(CategoryType.CHILD));
    }
    if (index <= 40) {
      return List.of(createPrenatalCategory(today, 31));
    }
    return List.of(createPrenatalCategory(today, 24));
  }

  static Category createCategory(CategoryType type) {
    Category category = new Category();
    category.setType(type);
    return category;
  }

  static Category createPrenatalCategory(LocalDate today, int gestationalWeeks) {
    Category category = createCategory(CategoryType.PRENATAL);
    category.setUltrasoundDate(today.minusDays(7));
    category.setGestationalWeeksAtUltrasound(gestationalWeeks);
    return category;
  }
}
