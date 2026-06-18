package com.autodispatch.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the modular-monolith boundaries:
 * 1. No module may depend on another module's {@code internal} package.
 * 2. {@code common} may be depended on by all modules but depends on none of them.
 */
class ModuleBoundaryTest {

    private static final String BASE = "com.autodispatch";
    private static final String[] MODULES =
            {"rider", "driver", "dispatch", "fare", "notification", "admin", "common"};

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(BASE);
    }

    @Test
    void no_module_depends_on_another_modules_internals() {
        for (String module : MODULES) {
            noClasses()
                    .that().resideOutsideOfPackage(BASE + "." + module + "..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(BASE + "." + module + ".internal..")
                    .as("no class outside module '%s' may depend on %s.%s.internal.."
                            .formatted(module, BASE, module))
                    // Modules start empty (scaffolding phase); rules must not fail on that.
                    .allowEmptyShould(true)
                    .check(classes);
        }
    }

    @Test
    void common_depends_on_no_other_module() {
        String[] otherModules = Arrays.stream(MODULES)
                .filter(m -> !m.equals("common"))
                .map(m -> BASE + "." + m + "..")
                .toArray(String[]::new);

        noClasses()
                .that().resideInAPackage(BASE + ".common..")
                .should().dependOnClassesThat().resideInAnyPackage(otherModules)
                .as("common must not depend on any other module")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void whatsapp_types_are_confined_to_the_notification_module() {
        noClasses()
                .that().resideOutsideOfPackage(BASE + ".notification..")
                .should().dependOnClassesThat(
                        com.tngtech.archunit.core.domain.JavaClass.Predicates
                                .simpleNameContaining("WhatsApp")
                                .and(com.tngtech.archunit.base.DescribedPredicate.not(
                                        com.tngtech.archunit.core.domain.JavaClass.Predicates
                                                .simpleName("WhatsAppGateway")))
                                .or(com.tngtech.archunit.core.domain.JavaClass.Predicates
                                        .simpleNameContaining("MetaApi")))
                .as("only the notification module may use WhatsApp/Meta types; "
                        + "everyone else goes through notification.api.WhatsAppGateway")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void setStatus_methods_must_be_private() {
        methods().that().haveName("setStatus")
                .should().bePrivate()
                .as("all status writes must go through transitionTo; raw setStatus must be private")
                .allowEmptyShould(true)
                .check(classes);
    }
}
