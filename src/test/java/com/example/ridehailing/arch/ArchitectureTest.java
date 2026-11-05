package com.example.ridehailing.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.*;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.example.ridehailing", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    private static final String BASE = "com.example.ridehailing.";

    @ArchTest static final ArchRule domainIsIndependentOfInfrastructure =
            noClasses().that().resideInAPackage(BASE + "domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(BASE + "web..", BASE + "service..",
                            BASE + "kafka..", BASE + "redis..", BASE + "ws..", BASE + "security..", BASE + "dto..");

    @ArchTest static final ArchRule nothingBelowTheWebLayerDependsOnIt =
            noClasses().that().resideInAnyPackage(BASE + "service..", BASE + "redis..", BASE + "kafka..", BASE + "domain..")
                    .should().dependOnClassesThat().resideInAPackage(BASE + "web..");

    @ArchTest static final ArchRule redisAndKafkaDoNotCallServicesBackward =
            noClasses().that().resideInAPackage(BASE + "redis..")
                    .should().dependOnClassesThat().resideInAnyPackage(BASE + "kafka..", BASE + "web..", BASE + "ws..");

    @ArchTest static final ArchRule controllersLiveInWebAndAreNamedAccordingly =
            classes().that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                    .or().areAnnotatedWith(org.springframework.stereotype.Controller.class)
                    .should().resideInAnyPackage(BASE + "web..", BASE + "security..").andShould().haveSimpleNameEndingWith("Controller");

    @ArchTest static final ArchRule noFieldInjection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
    @ArchTest static final ArchRule noGenericExceptions = NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
    @ArchTest static final ArchRule noJavaUtilLogging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
    @ArchTest static final ArchRule noStandardStreams = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
}
