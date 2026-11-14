package br.senac.qs.recursos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("StatusReserva: máquina de estados (RC05)")
class StatusReservaTest {

    /** As únicas transições permitidas (fluxo principal + alternativas). Todo o resto é recusado. */
    static final Set<String> PERMITIDAS = Set.of(
            "SOLICITADA→APROVADA", "SOLICITADA→REJEITADA", "SOLICITADA→CANCELADA",
            "APROVADA→EM_USO", "APROVADA→CANCELADA", "APROVADA→NAO_COMPARECEU",
            "EM_USO→CONCLUIDA");

    /** Todos os 7 × 7 = 49 pares (de, para), inclusive "para o mesmo estado". */
    static Stream<Arguments> todosOsPares() {
        List<Arguments> pares = new ArrayList<>();
        for (StatusReserva de : StatusReserva.values()) {
            for (StatusReserva para : StatusReserva.values()) {
                pares.add(Arguments.of(de, para, PERMITIDAS.contains(de + "→" + para)));
            }
        }
        return pares.stream();
    }

    @ParameterizedTest(name = "{0} → {1}: permitida={2}")
    @MethodSource("todosOsPares")
    @DisplayName("ES-01/ES-02: matriz completa de transições")
    void matrizCompleta(StatusReserva de, StatusReserva para, boolean permitida) {
        assertThat(de.podeIrPara(para)).isEqualTo(permitida);
    }

    @Test
    @DisplayName("a matriz tem 49 pares e exatamente 7 transições permitidas")
    void tamanhoDaMatriz() {
        assertThat(todosOsPares()).hasSize(49);
        assertThat(todosOsPares().filter(a -> (boolean) a.get()[2])).hasSize(7);
    }

    @ParameterizedTest(name = "{0} libera o horário? {1}")
    @CsvSource({
        "SOLICITADA, false", "APROVADA, false", "EM_USO, false", "CONCLUIDA, false",
        "REJEITADA, true", "CANCELADA, true", "NAO_COMPARECEU, true"
    })
    @DisplayName("só REJEITADA, CANCELADA e NAO_COMPARECEU liberam o horário para outra reserva")
    void liberaHorario(StatusReserva status, boolean libera) {
        assertThat(status.liberaHorario()).isEqualTo(libera);
    }
}
