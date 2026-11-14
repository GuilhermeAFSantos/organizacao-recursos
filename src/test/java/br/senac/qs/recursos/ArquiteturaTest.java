package br.senac.qs.recursos;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Evidência automatizada de que a arquitetura em camadas é respeitada: se alguém criar uma
 * dependência proibida (ex.: o domínio importando algo da camada web), o build quebra.
 */
@AnalyzeClasses(packages = "br.senac.qs.recursos", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {

    @ArchTest
    static final ArchRule camadas = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("Web").definedBy("..recursos.web..")
            .layer("Aplicacao").definedBy("..recursos.application..")
            .layer("Dominio").definedBy("..recursos.domain..")
            .layer("Infraestrutura").definedBy("..recursos.infrastructure..")
            .whereLayer("Dominio").mayNotAccessAnyLayer()
            .whereLayer("Aplicacao").mayOnlyAccessLayers("Dominio")
            .whereLayer("Web").mayOnlyAccessLayers("Aplicacao", "Dominio")
            // A infraestrutura monta a aplicação (configuração, segurança, adaptadores),
            // por isso pode enxergar as demais camadas.
            .whereLayer("Infraestrutura").mayOnlyAccessLayers("Aplicacao", "Dominio", "Web");

    @ArchTest
    static final ArchRule dominioSemHttp = noClasses()
            .that().resideInAPackage("..recursos.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework.web..", "org.springframework.http..", "jakarta.servlet..")
            .because("regras de negócio não devem conhecer detalhes de HTTP");

    @ArchTest
    static final ArchRule controllersNaoAcessamRepositorios = noClasses()
            .that().resideInAPackage("..recursos.web..")
            .should().dependOnClassesThat().resideInAPackage("..recursos.domain.repository..")
            .because("controllers devem passar pelos casos de uso (camada application), "
                    + "onde ficam as transações e as regras");
}
