package br.senac.qs.recursos.carga;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Confere as metas do teste de carga (RNF06) no resumo do relatório do JMeter
 * ({@code statistics.json}). Usado pelo CI depois do JMeter; a lógica é testada em
 * {@code MetasDeCargaTest}.
 *
 * <p>Metas (docs/arquitetura.md): nenhuma requisição com erro; p95 abaixo de 500 ms para pesquisar
 * e listar; abaixo de 1 s para criar reserva, inclusive disputando o mesmo horário.
 *
 * <p>Uso: {@code ./mvnw test-compile exec:java -Dexec.classpathScope=test
 * -Dexec.mainClass=br.senac.qs.recursos.carga.MetasDeCarga [-Dexec.args=caminho/statistics.json]}
 */
public final class MetasDeCarga {

    static final String RESUMO_PADRAO = "target/jmeter/reports/carga-reservas/statistics.json";

    /** {@code pct2ResTime} é o percentil 95 no relatório padrão do JMeter. */
    record Meta(String transacao, double p95MaximoMs) {
    }

    static final List<Meta> METAS = List.of(
            new Meta("Pesquisar salas livres", 500),
            new Meta("Listar minhas reservas", 500),
            new Meta("Criar reserva", 1000),
            new Meta("Disputar o mesmo horário", 1000));

    /** Resultado da conferência: uma linha por meta e se todas foram cumpridas. */
    record Resultado(List<String> linhas, boolean aprovado) {
    }

    private MetasDeCarga() {
    }

    static Resultado conferir(JsonNode estatisticas) {
        var linhas = new ArrayList<String>();
        var aprovado = true;
        for (var meta : METAS) {
            var transacao = estatisticas.get(meta.transacao());
            if (transacao == null || transacao.path("sampleCount").asLong() == 0) {
                linhas.add("FALHOU  " + meta.transacao() + ": sem amostras no relatório");
                aprovado = false;
                continue;
            }
            var p95 = transacao.get("pct2ResTime").asDouble();
            var cumpriu = p95 < meta.p95MaximoMs();
            aprovado &= cumpriu;
            linhas.add("%-7s %-26s p95 = %6.0f ms (meta: < %.0f ms)".formatted(cumpriu ? "OK" : "FALHOU",
                    meta.transacao(), p95, meta.p95MaximoMs()));
        }
        var total = estatisticas.path("Total");
        var erros = total.path("errorCount").asLong(-1);
        var semErros = erros == 0;
        aprovado &= semErros;
        linhas.add("%-7s erros: %d de %d requisições (meta: 0)".formatted(semErros ? "OK" : "FALHOU", erros,
                total.path("sampleCount").asLong()));
        linhas.add("INFO    vazão total: %.1f requisições/s".formatted(total.path("throughput").asDouble()));
        return new Resultado(linhas, aprovado);
    }

    public static void main(String[] argumentos) throws IOException {
        var caminho = Path.of(argumentos.length > 0 ? argumentos[0] : RESUMO_PADRAO);
        if (!Files.exists(caminho)) {
            throw new IllegalStateException("Resumo não encontrado: " + caminho + " (o teste de carga rodou?)");
        }
        var resultado = conferir(new ObjectMapper().readTree(caminho.toFile()));
        resultado.linhas().forEach(System.out::println);
        if (!resultado.aprovado()) {
            throw new IllegalStateException("Teste de carga reprovado: alguma meta não foi cumprida.");
        }
    }
}
