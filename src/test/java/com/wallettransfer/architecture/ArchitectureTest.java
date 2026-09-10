package com.wallettransfer.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.wallettransfer", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllers_live_in_controller_packages =
            classes().that().haveSimpleNameEndingWith("Controller").should().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule controllers_do_not_access_repositories_or_models = noClasses()
            .that()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..repository..", "..model..");

    @ArchTest
    static final ArchRule services_live_in_service_packages = classes()
            .that()
            .haveSimpleNameEndingWith("Service")
            .and()
            .areAnnotatedWith("org.springframework.stereotype.Service")
            .and()
            .resideOutsideOfPackage("..security..")
            .should()
            .resideInAPackage("..service..");

    @ArchTest
    static final ArchRule repositories_live_in_repository_packages =
            classes().that().haveSimpleNameEndingWith("Repository").should().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule jpa_entities_live_in_model_packages = classes()
            .that()
            .areAnnotatedWith("jakarta.persistence.Entity")
            .should()
            .resideInAPackage("..model..");

    @ArchTest
    static final ArchRule repositories_do_not_depend_on_controllers_or_services = noClasses()
            .that()
            .resideInAPackage("..repository..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..controller..", "..service..");

    @ArchTest
    static final ArchRule wallet_repository_is_private = noClasses()
            .that()
            .resideOutsideOfPackage("com.wallettransfer.wallets..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.wallettransfer.wallets.repository..");

    @ArchTest
    static final ArchRule user_repository_is_private = noClasses()
            .that()
            .resideOutsideOfPackage("com.wallettransfer.users..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.wallettransfer.users.repository..");

    @ArchTest
    static final ArchRule authentication_repository_is_private = noClasses()
            .that()
            .resideOutsideOfPackage("com.wallettransfer.authentication..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.wallettransfer.authentication.repository..");

    @ArchTest
    static final ArchRule ledger_repository_is_private = noClasses()
            .that()
            .resideOutsideOfPackage("com.wallettransfer.ledger..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.wallettransfer.ledger.repository..");

    @ArchTest
    static final ArchRule top_level_packages_are_acyclic =
            slices().matching("com.wallettransfer.(*)..").should().beFreeOfCycles();
}
