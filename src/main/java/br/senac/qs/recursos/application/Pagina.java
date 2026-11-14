package br.senac.qs.recursos.application;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Uma página de resultados, com os totais que a interface precisa para paginar. Contrato próprio,
 * em português, em vez de serializar o {@code Page} do Spring (cujo JSON não é estável entre versões).
 *
 * @param pagina número da página, começando em 0
 */
public record Pagina<T>(List<T> itens, int pagina, int tamanho, long totalItens, int totalPaginas) {

    public static <E, T> Pagina<T> de(Page<E> pagina, Function<E, T> conversao) {
        return new Pagina<>(pagina.getContent().stream().map(conversao).toList(), pagina.getNumber(),
                pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
    }

    public <R> Pagina<R> converter(Function<T, R> conversao) {
        return new Pagina<>(itens.stream().map(conversao).toList(), pagina, tamanho, totalItens, totalPaginas);
    }
}
