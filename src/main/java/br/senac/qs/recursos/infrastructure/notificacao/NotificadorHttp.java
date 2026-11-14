package br.senac.qs.recursos.infrastructure.notificacao;

import java.time.Duration;

import br.senac.qs.recursos.application.notificacao.Notificacao;
import br.senac.qs.recursos.application.notificacao.Notificador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Adaptador da notificação (RF11): {@code POST {app.notificacao.url-base}/notificacoes} em JSON.
 *
 * <ul>
 *   <li><b>Tempo limite</b> ({@code app.notificacao.timeout}) na conexão e na leitura: um serviço
 *       lento não segura a resposta ao usuário além disso.</li>
 *   <li><b>Sem URL configurada</b>: notificação <b>simulada</b>, só registrada no log. É o padrão
 *       em desenvolvimento e nos testes que não usam o WireMock.</li>
 * </ul>
 */
@Component
public class NotificadorHttp implements Notificador {

    static final String CAMINHO = "/notificacoes";
    private static final Logger log = LoggerFactory.getLogger(NotificadorHttp.class);

    private final RestClient cliente;

    public NotificadorHttp(RestClient.Builder construtor,
                           @Value("${app.notificacao.url-base:}") String urlBase,
                           @Value("${app.notificacao.timeout:PT2S}") Duration tempoLimite) {
        if (urlBase == null || urlBase.isBlank()) {
            this.cliente = null;
            log.info("NOTIFICACAO_URL não configurada: notificações serão apenas registradas no log (simuladas).");
            return;
        }
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(tempoLimite);
        fabrica.setReadTimeout(tempoLimite);
        this.cliente = construtor.baseUrl(urlBase).requestFactory(fabrica).build();
    }

    @Override
    public void enviar(Notificacao notificacao) {
        if (cliente == null) {
            log.info("[notificação simulada] {} da reserva {}: {}", notificacao.evento(), notificacao.reservaId(),
                    notificacao.mensagem());
            return;
        }
        cliente.post().uri(CAMINHO).contentType(MediaType.APPLICATION_JSON).body(notificacao)
                .retrieve().toBodilessEntity();
    }
}
