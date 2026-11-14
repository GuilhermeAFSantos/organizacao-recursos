package br.senac.qs.recursos.application.notificacao;

/**
 * Porta de saída para o serviço externo de notificação (RF11). A aplicação só conhece esta
 * interface; o adaptador HTTP fica na infraestrutura (inversão de dependência).
 */
public interface Notificador {

    /** Pode falhar (serviço fora, lento, com erro): quem chama trata a falha. */
    void enviar(Notificacao notificacao);
}
