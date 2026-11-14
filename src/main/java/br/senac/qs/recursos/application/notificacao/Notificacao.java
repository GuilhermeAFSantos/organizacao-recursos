package br.senac.qs.recursos.application.notificacao;

import java.time.Instant;

import br.senac.qs.recursos.domain.model.MudancaDeEstado;
import br.senac.qs.recursos.domain.model.StatusReserva;

/**
 * O que é enviado ao serviço externo de notificação (RF11): um evento com nome estável, para quem
 * integra, e uma mensagem pronta em português, para quem lê.
 */
public record Notificacao(String evento, Long reservaId, String destinatario, String mensagem, Instant ocorridoEm) {

    public static Notificacao de(ReservaMudou evento) {
        var mudanca = evento.mudanca();
        var alteracao = MudancaDeEstado.ALTERACAO.equals(mudanca.acao());
        var nome = alteracao ? "RESERVA_HORARIO_ALTERADO" : "RESERVA_" + mudanca.novo().name();
        var mensagem = alteracao ? mensagemDeAlteracao(mudanca)
                : mensagemDeEstado(mudanca, "sua reserva de " + evento.periodo().descrever());
        return new Notificacao(nome, evento.reservaId(), evento.solicitante(), mensagem, mudanca.quando());
    }

    private static String mensagemDeAlteracao(MudancaDeEstado mudanca) {
        var aviso = mudanca.novo() == StatusReserva.SOLICITADA && mudanca.anterior() == StatusReserva.APROVADA
                ? " Ela precisa de nova aprovação." : "";
        return "Sua reserva foi alterada. " + mudanca.motivo() + aviso;
    }

    private static String mensagemDeEstado(MudancaDeEstado mudanca, String reserva) {
        var sua = Character.toUpperCase(reserva.charAt(0)) + reserva.substring(1);
        return switch (mudanca.novo()) {
            case SOLICITADA -> sua + " foi registrada e aguarda aprovação.";
            case APROVADA -> MudancaDeEstado.AUTOR_SISTEMA.equals(mudanca.autorEmail())
                    ? sua + " foi aprovada automaticamente: nenhum recurso é restrito."
                    : sua + " foi aprovada.";
            case REJEITADA -> sua + " foi rejeitada." + motivo(mudanca);
            case CANCELADA -> sua + " foi cancelada." + motivo(mudanca);
            case EM_USO -> "O uso da " + reserva + " foi iniciado.";
            case CONCLUIDA -> sua + " foi concluída.";
            case NAO_COMPARECEU -> "Foi registrado o não comparecimento na " + reserva + ".";
        };
    }

    private static String motivo(MudancaDeEstado mudanca) {
        return mudanca.motivo() == null || mudanca.motivo().isBlank() ? "" : " Motivo: " + mudanca.motivo();
    }
}
