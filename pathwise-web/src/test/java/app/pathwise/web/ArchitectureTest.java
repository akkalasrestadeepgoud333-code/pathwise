package app.pathwise.web;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Enforces the module boundaries described in the blueprint (§5). */
class ArchitectureTest {
    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("app.pathwise");
    }

    @Test
    void coreIsPureDomainLogic() {
        noClasses().that().resideInAPackage("app.pathwise.core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.servlet..", "java.sql..", "javax.sql..", "app.pathwise.data..", "app.pathwise.web..")
                .check(classes);
    }

    @Test
    void dataLayerKnowsNothingAboutTheWeb() {
        noClasses().that().resideInAPackage("app.pathwise.data..")
                .should().dependOnClassesThat().resideInAnyPackage("jakarta.servlet..", "app.pathwise.web..")
                .check(classes);
    }

    @Test
    void controllersDoNotTouchSqlOrDaos() {
        noClasses().that().haveSimpleNameEndingWith("Controller").or().haveSimpleNameEndingWith("ApiServlet")
                .should().dependOnClassesThat().resideInAnyPackage("java.sql..", "javax.sql..")
                .orShould().dependOnClassesThat().haveSimpleNameEndingWith("Dao")
                .check(classes);
    }

    @Test
    void daosAreUsedOnlyByServicesAndPlatformCode() {
        classes().that().haveSimpleNameEndingWith("Dao").and().resideInAPackage("app.pathwise.data..")
                .should().onlyBeAccessed().byClassesThat(
                        com.tngtech.archunit.base.DescribedPredicate.describe("a service, the data layer or platform code",
                                c -> c.getSimpleName().endsWith("Service")
                                        || c.getPackageName().startsWith("app.pathwise.data")
                                        || c.getPackageName().startsWith("app.pathwise.web.platform")
                                        || c.getPackageName().startsWith("app.pathwise.app")))
                .check(classes);
    }

    @Test
    void identityModuleDoesNotReachIntoAdminAndViceVersa() {
        noClasses().that().resideInAPackage("app.pathwise.web.identity..")
                .should().dependOnClassesThat().resideInAPackage("app.pathwise.web.admin..")
                .check(classes);
        noClasses().that().resideInAPackage("app.pathwise.web.admin..")
                .should().dependOnClassesThat().resideInAPackage("app.pathwise.data.identity..")
                .check(classes);
    }
}
