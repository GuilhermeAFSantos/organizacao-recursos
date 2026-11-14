package br.senac.qs.recursos.carga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A régua que aprova ou reprova o teste de carga (RNF06) precisa estar certa nas fronteiras. */
@DisplayName("Metas do teste de carga")
class MetasDeCargaTest {

    static final ObjectMapper JSON = new ObjectMapper();

    /** Resumo no formato do statistics.json do JMeter, com todas as transações dentro da meta. */
    static ObjectNode resumoAprovado() {
        var resumo = JSON.createObjectNode();
        transacao(resumo, "Pesquisar salas livres", 15);
        transacao(resumo, "Listar minhas reservas", 16);
        transacao(resumo, "Criar reserva", 29);
        transacao(resumo, "Disputar o mesmo horário", 22);
        resumo.putObject("Total").put("sampleCount", 12073).put("errorCount", 0).put("throughput", 96.3);
        return resumo;
    }

    static void transacao(ObjectNode resumo, String nome, double p95) {
        resumo.putObject(nome).put("sampleCount", 100).put("errorCount", 0).put("pct2ResTime", p95);
    }

    @Test
    @DisplayName("todas as transações dentro da meta e nenhum erro: aprovado")
    void aprovado() {
        var resultado = MetasDeCarga.conferir(resumoAprovado());

        assertThat(resultado.aprovado()).isTrue();
        assertThat(resultado.linhas()).filteredOn(l -> l.startsWith("FALHOU")).isEmpty();
    }

    @ParameterizedTest(name = "{0}: p95 de {1} ms → aprovado = {2}")
    @CsvSource(delimiter = '|', textBlock = """
        Pesquisar salas livres   | 499.9 | true
        Pesquisar salas livres   | 500   | false
        Listar minhas reservas   | 500   | false
        Criar reserva            | 999   | true
        Criar reserva            | 1000  | false
        Disputar o mesmo horário | 1000  | false
        """)
    @DisplayName("fronteira do p95: a meta é estrita (igual ao limite já reprova)")
    void fronteiras(String nome, double p95, boolean aprovado) {
        var resumo = resumoAprovado();
        transacao(resumo, nome, p95);

        assertThat(MetasDeCarga.conferir(resumo).aprovado()).isEqualTo(aprovado);
    }

    @Test
    @DisplayName("um único erro reprova")
    void umErro() {
        var resumo = resumoAprovado();
        ((ObjectNode) resumo.get("Total")).put("errorCount", 1);

        var resultado = MetasDeCarga.conferir(resumo);

        assertThat(resultado.aprovado()).isFalse();
        assertThat(resultado.linhas()).anyMatch(l -> l.startsWith("FALHOU  erros: 1"));
    }

    @Test
    @DisplayName("transação sem amostras (o cenário não rodou) reprova, em vez de passar em branco")
    void semAmostras() {
        var resumo = resumoAprovado();
        resumo.remove("Disputar o mesmo horário");

        assertThat(MetasDeCarga.conferir(resumo).aprovado()).isFalse();
    }

    @Test
    @DisplayName("pela linha de comando: lê o arquivo; reprovado lança erro (o build falha); sem arquivo também")
    void linhaDeComando(@TempDir Path pasta) throws Exception {
        var aprovado = pasta.resolve("aprovado.json");
        JSON.writeValue(aprovado.toFile(), resumoAprovado());
        var reprovado = pasta.resolve("reprovado.json");
        var resumo = resumoAprovado();
        transacao(resumo, "Criar reserva", 5000);
        JSON.writeValue(reprovado.toFile(), resumo);

        MetasDeCarga.main(new String[] {aprovado.toString()});
        assertThatThrownBy(() -> MetasDeCarga.main(new String[] {reprovado.toString()}))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reprovado");
        assertThatThrownBy(() -> MetasDeCarga.main(new String[] {pasta.resolve("nao-existe.json").toString()}))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("não encontrado");
    }
}
