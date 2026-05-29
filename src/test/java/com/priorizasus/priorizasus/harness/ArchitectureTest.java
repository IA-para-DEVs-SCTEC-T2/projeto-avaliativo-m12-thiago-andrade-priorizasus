package com.priorizasus.priorizasus.harness;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

/**
 * Enforces the layered Spring Boot architecture defined in STACK.md.
 *
 * <p>Rules:
 *
 * <ul>
 *   <li>Controllers must be in {@code ..controller..} package
 *   <li>Services must be in {@code ..service..} package
 *   <li>Repositories must be in {@code ..repository..} package
 *   <li>Entities must be in {@code ..entity..} package
 *   <li>Controllers must NOT access repositories directly
 *   <li>Services must NOT depend on controllers
 *   <li>{@code @Transactional} is forbidden on controllers
 *   <li>No circular package dependencies
 * </ul>
 */
class ArchitectureTest {

  private static final String BASE_PACKAGE = "com.priorizasus.priorizasus";
  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);
  }

  // ── Package location ──

  @Test
  @DisplayName("Controllers reside in ..controller.. package")
  void controllersInControllerPackage() {
    ArchRule rule =
        classes()
            .that()
            .areAnnotatedWith(Controller.class)
            .or()
            .areAnnotatedWith(RestController.class)
            .should()
            .resideInAPackage("..controller..")
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  @DisplayName("Services reside in ..service.. package")
  void servicesInServicePackage() {
    ArchRule rule =
        classes()
            .that()
            .areAnnotatedWith(Service.class)
            .should()
            .resideInAPackage("..service..")
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  @DisplayName("Repositories reside in ..repository.. package")
  void repositoriesInRepositoryPackage() {
    ArchRule rule =
        classes()
            .that()
            .areAnnotatedWith(Repository.class)
            .should()
            .resideInAPackage("..repository..");
    rule.check(classes);
  }

  @Test
  @DisplayName("JPA entities reside in ..entity.. package")
  void entitiesInEntityPackage() {
    ArchRule rule =
        classes()
            .that()
            .areAnnotatedWith(jakarta.persistence.Entity.class)
            .should()
            .resideInAPackage("..entity..");
    rule.check(classes);
  }

  // ── Layering ──

  @Test
  @DisplayName("Controllers must NOT access repositories directly")
  void controllersDoNotAccessRepositories() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..controller..")
            .should()
            .accessClassesThat()
            .resideInAPackage("..repository..")
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  @DisplayName("Services must NOT depend on controllers")
  void servicesDoNotDependOnControllers() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..service..")
            .should()
            .accessClassesThat()
            .resideInAPackage("..controller..")
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  @DisplayName("Controllers must NOT have @Transactional annotation")
  void controllersDoNotHaveTransactional() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..controller..")
            .should()
            .beAnnotatedWith(Transactional.class)
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  @DisplayName("No circular package dependencies")
  void noCircularPackageDependencies() {
    ArchRule rule =
        SlicesRuleDefinition.slices().matching(BASE_PACKAGE + ".(*)..").should().beFreeOfCycles();
    rule.check(classes);
  }

  // ── Naming conventions ──

  @Test
  @DisplayName("Service classes end with 'Service'")
  void serviceClassesEndWithService() {
    ArchRule rule =
        classes()
            .that()
            .resideInAPackage("..service..")
            .and()
            .areTopLevelClasses()
            .should()
            .haveSimpleNameEndingWith("Service")
            .allowEmptyShould(true);
    rule.check(classes);
  }

  @Test
  @DisplayName("Repository interfaces end with 'Repository'")
  void repositoryInterfacesEndWithRepository() {
    ArchRule rule =
        classes()
            .that()
            .resideInAPackage("..repository..")
            .and()
            .areInterfaces()
            .should()
            .haveSimpleNameEndingWith("Repository");
    rule.check(classes);
  }

  @Test
  @DisplayName("Controller classes end with 'Controller'")
  void controllerClassesEndWithController() {
    ArchRule rule =
        classes()
            .that()
            .resideInAPackage("..controller..")
            .and()
            .areTopLevelClasses()
            .should()
            .haveSimpleNameEndingWith("Controller")
            .allowEmptyShould(true);
    rule.check(classes);
  }
}
