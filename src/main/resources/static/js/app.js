/*
 * Interface da Organização de Recursos (RF13): consome a API REST da própria aplicação.
 *
 * Regras de segurança deste arquivo (verificadas por InterfaceIT):
 *  - dados vindos da API entram na página SÓ como texto (textContent / nós de texto), nunca como
 *    HTML: um nome de sala com "<script>" aparece escrito, não executa;
 *  - o token fica no sessionStorage: some ao fechar a aba e não é enviado automaticamente pelo
 *    navegador (por isso a API não precisa de proteção CSRF);
 *  - nenhum código inline no HTML, o que permite uma Content-Security-Policy estrita.
 */
(function () {
    'use strict';

    const CHAVE_SESSAO = 'organizacao-recursos.sessao';
    const FUSO = 'America/Sao_Paulo';
    /** Brasília não tem horário de verão desde 2019: o deslocamento é fixo. */
    const DESLOCAMENTO_BRASILIA = '-03:00';

    const SECOES_POR_PERFIL = {
        SOLICITANTE: ['pesquisa', 'reservas'],
        RESPONSAVEL: ['aprovacoes', 'relatorio'],
        ADMINISTRADOR: ['reservas', 'relatorio']
    };
    const NOMES_SECAO = {
        pesquisa: 'Reservar',
        reservas: 'Minhas reservas',
        aprovacoes: 'Aprovações',
        relatorio: 'Relatório'
    };
    const NOMES_PERFIL = { SOLICITANTE: 'Solicitante', RESPONSAVEL: 'Responsável', ADMINISTRADOR: 'Administrador' };
    const NOMES_STATUS = {
        SOLICITADA: 'Aguardando aprovação',
        APROVADA: 'Aprovada',
        EM_USO: 'Em uso',
        CONCLUIDA: 'Concluída',
        REJEITADA: 'Rejeitada',
        CANCELADA: 'Cancelada',
        NAO_COMPARECEU: 'Não compareceu'
    };
    const NOMES_TIPO = { SALA: 'Sala', PROFESSOR: 'Professor', MATERIAL: 'Material' };
    const NOMES_TIPO_SALA = { AULA: 'Aula', LABORATORIO: 'Laboratório', AUDITORIO: 'Auditório', REUNIAO: 'Reunião' };
    const ALTERAVEIS = ['SOLICITADA', 'APROVADA'];

    const dataHora = new Intl.DateTimeFormat('pt-BR', { timeZone: FUSO, dateStyle: 'short', timeStyle: 'short' });
    const hora = new Intl.DateTimeFormat('pt-BR', { timeZone: FUSO, timeStyle: 'short' });
    const diaIso = new Intl.DateTimeFormat('en-CA', { timeZone: FUSO });

    const $ = (id) => document.getElementById(id);

    // ------------------------------------------------------------------ sessão

    function lerSessao() {
        try {
            const sessao = JSON.parse(sessionStorage.getItem(CHAVE_SESSAO));
            return sessao && new Date(sessao.expiraEm) > new Date() ? sessao : null;
        } catch (e) {
            return null;
        }
    }

    function salvarSessao(sessao) {
        sessionStorage.setItem(CHAVE_SESSAO, JSON.stringify(sessao));
    }

    function encerrarSessao() {
        sessionStorage.removeItem(CHAVE_SESSAO);
    }

    // ------------------------------------------------------------------ DOM (só texto, nunca HTML)

    /** Cria um elemento. Filhos que são texto viram nós de texto: nunca são interpretados como HTML. */
    function el(tag, atributos, ...filhos) {
        const elemento = document.createElement(tag);
        Object.entries(atributos || {}).forEach(([nome, valor]) => {
            if (valor === undefined || valor === null || valor === false) {
                return;
            }
            if (nome === 'classe') {
                elemento.className = valor;
            } else if (nome === 'aoClicar') {
                elemento.addEventListener('click', valor);
            } else {
                elemento.setAttribute(nome, valor === true ? '' : String(valor));
            }
        });
        filhos.flat().forEach((filho) => {
            if (filho !== undefined && filho !== null) {
                elemento.append(filho instanceof Node ? filho : String(filho));
            }
        });
        return elemento;
    }

    function avisar(texto, tipo) {
        const aviso = $('aviso');
        aviso.textContent = texto;
        aviso.dataset.tipo = tipo || 'info';
        aviso.hidden = false;
        aviso.focus();
    }

    function limparAviso() {
        $('aviso').hidden = true;
    }

    /** Desabilita o botão enquanto a requisição roda: evita reservas duplicadas por clique duplo. */
    async function ocupado(botao, acao) {
        botao.disabled = true;
        try {
            return await acao();
        } catch (erro) {
            avisar(mensagemDeErro(erro), 'erro');
            return undefined;
        } finally {
            botao.disabled = false;
        }
    }

    // ------------------------------------------------------------------ API

    class ErroApi extends Error {
        constructor(status, problema) {
            super((problema && problema.detail) || 'Não foi possível concluir a operação.');
            this.status = status;
            this.problema = problema;
        }
    }

    async function api(metodo, caminho, corpo) {
        const sessao = lerSessao();
        const cabecalhos = { Accept: 'application/json' };
        if (corpo !== undefined) {
            cabecalhos['Content-Type'] = 'application/json';
        }
        if (sessao) {
            cabecalhos.Authorization = 'Bearer ' + sessao.token;
        }
        let resposta;
        try {
            resposta = await fetch(caminho, {
                method: metodo,
                headers: cabecalhos,
                body: corpo === undefined ? undefined : JSON.stringify(corpo)
            });
        } catch (e) {
            throw new ErroApi(0, { detail: 'Não foi possível falar com o servidor. Verifique sua conexão.' });
        }
        const dados = resposta.status === 204 ? null : await resposta.json().catch(() => null);
        if (!resposta.ok) {
            if (resposta.status === 401 && sessao) {
                encerrarSessao();
                mostrarLogin();
                throw new ErroApi(401, { detail: 'Sua sessão expirou. Entre novamente.' });
            }
            throw new ErroApi(resposta.status, dados);
        }
        return dados;
    }

    /** ProblemDetail da API: o detalhe e, se houver, cada campo inválido. */
    function mensagemDeErro(erro) {
        const problema = erro.problema;
        if (problema && Array.isArray(problema.erros) && problema.erros.length > 0) {
            return erro.message + ' ' + problema.erros.map((e) => e.campo + ': ' + e.mensagem).join('; ') + '.';
        }
        return erro.message;
    }

    // ------------------------------------------------------------------ datas

    /** "2026-12-01" + "10:00" → "2026-12-01T10:00:00-03:00", o formato que a API recebe. */
    function instante(data, horaMinuto) {
        return data + 'T' + horaMinuto + ':00' + DESLOCAMENTO_BRASILIA;
    }

    function hoje() {
        return diaIso.format(new Date());
    }

    function somarDias(dataIso, dias) {
        const data = new Date(dataIso + 'T12:00:00Z');
        data.setUTCDate(data.getUTCDate() + dias);
        return data.toISOString().slice(0, 10);
    }

    function descreverPeriodo(inicio, fim) {
        return dataHora.format(new Date(inicio)) + ' às ' + hora.format(new Date(fim));
    }

    // ------------------------------------------------------------------ navegação

    const CARREGAR = {
        reservas: carregarReservas,
        aprovacoes: carregarAprovacoes
    };

    function mostrarSecao(nome) {
        document.querySelectorAll('main > section').forEach((secao) => {
            secao.hidden = secao.id !== 'secao-' + nome;
        });
        document.querySelectorAll('#menu button').forEach((botao) => {
            botao.setAttribute('aria-current', botao.dataset.secao === nome ? 'page' : 'false');
        });
        limparAviso();
        if (CARREGAR[nome]) {
            CARREGAR[nome]();
        }
    }

    function mostrarLogin() {
        $('menu').replaceChildren();
        $('menu').hidden = true;
        $('usuario').textContent = '';
        $('sair').hidden = true;
        mostrarSecao('login');
    }

    function entrar(sessao) {
        const secoes = SECOES_POR_PERFIL[sessao.perfil] || [];
        $('usuario').textContent = sessao.email + ' · ' + (NOMES_PERFIL[sessao.perfil] || sessao.perfil);
        $('sair').hidden = false;
        $('titulo-reservas').textContent = sessao.perfil === 'SOLICITANTE' ? 'Minhas reservas' : 'Reservas';
        $('menu').replaceChildren(...secoes.map((secao) => el('button', {
            type: 'button',
            'data-secao': secao,
            aoClicar: () => mostrarSecao(secao)
        }, sessao.perfil === 'SOLICITANTE' || secao !== 'reservas' ? NOMES_SECAO[secao] : 'Reservas')));
        $('menu').hidden = false;
        mostrarSecao(secoes[0] || 'login');
    }

    // ------------------------------------------------------------------ login

    $('form-login').addEventListener('submit', (evento) => {
        evento.preventDefault();
        const formulario = evento.target;
        const dados = new FormData(formulario);
        ocupado(formulario.querySelector('button'), async () => {
            const email = String(dados.get('email')).trim().toLowerCase();
            const resposta = await api('POST', '/api/auth/login', { email: email, senha: dados.get('senha') });
            salvarSessao({ token: resposta.token, perfil: resposta.perfil, email: email, expiraEm: resposta.expiraEm });
            formulario.reset();
            entrar(lerSessao());
        });
    });

    $('sair').addEventListener('click', () => {
        encerrarSessao();
        mostrarLogin();
    });

    // ------------------------------------------------------------------ reservar

    let escolha = null;

    $('form-pesquisa').addEventListener('submit', (evento) => {
        evento.preventDefault();
        const dados = new FormData(evento.target);
        const inicio = instante(dados.get('data'), dados.get('inicio'));
        const fim = instante(dados.get('data'), dados.get('fim'));
        const filtros = new URLSearchParams({ tipo: 'SALA', inicio: inicio, fim: fim, tamanho: '100' });
        ['tipoSala', 'capacidadeMinima', 'bloco'].forEach((campo) => {
            if (dados.get(campo)) {
                filtros.set(campo, dados.get(campo));
            }
        });
        $('form-reserva').hidden = true;
        ocupado(evento.submitter || evento.target.querySelector('button'), async () => {
            const pagina = await api('GET', '/api/recursos?' + filtros);
            mostrarSalas(pagina.itens, inicio, fim);
        });
    });

    function mostrarSalas(salas, inicio, fim) {
        const lista = $('resultado-salas');
        if (salas.length === 0) {
            lista.replaceChildren(el('p', { classe: 'vazio' }, 'Nenhuma sala livre com esses critérios. Tente outro horário.'));
            return;
        }
        lista.replaceChildren(...salas.map((sala) => el('article', { classe: 'cartao' },
            el('div', { classe: 'cartao__titulo' },
                el('strong', null, sala.nome),
                sala.restrito ? el('span', { classe: 'situacao', 'data-status': 'SOLICITADA' }, 'Exige aprovação') : null),
            el('p', { classe: 'detalhe' }, (NOMES_TIPO_SALA[sala.tipoSala] || sala.tipoSala) + ' · ' + sala.capacidade
                + ' lugares · Bloco ' + sala.bloco + ', ' + sala.andar + 'º andar'),
            el('div', { classe: 'acoes' }, el('button', {
                type: 'button',
                classe: 'botao',
                aoClicar: () => escolherSala(sala, inicio, fim)
            }, 'Escolher')))));
    }

    async function escolherSala(sala, inicio, fim) {
        const filtros = (tipo) => '/api/recursos?' + new URLSearchParams({ tipo: tipo, inicio: inicio, fim: fim, tamanho: '100' });
        try {
            const [professores, materiais] = await Promise.all([api('GET', filtros('PROFESSOR')), api('GET', filtros('MATERIAL'))]);
            escolha = { sala: sala, inicio: inicio, fim: fim };
            $('resumo-reserva').textContent = sala.nome + ' — ' + descreverPeriodo(inicio, fim);
            $('professor').replaceChildren(el('option', { value: '' }, 'Sem professor'),
                ...professores.itens.map((p) => el('option', { value: p.id }, p.nome)));
            $('materiais').replaceChildren(...(materiais.itens.length === 0
                ? [el('p', { classe: 'vazio' }, 'Nenhum material livre nesse horário.')]
                : materiais.itens.map((m) => el('label', null,
                    el('input', { type: 'checkbox', name: 'materiaisIds', value: m.id }),
                    m.nome + ' (' + m.patrimonio + ')'))));
            $('form-reserva').hidden = false;
            $('form-reserva').scrollIntoView({ behavior: 'smooth', block: 'start' });
        } catch (erro) {
            avisar(mensagemDeErro(erro), 'erro');
        }
    }

    $('cancelar-escolha').addEventListener('click', () => {
        $('form-reserva').hidden = true;
        escolha = null;
    });

    $('form-reserva').addEventListener('submit', (evento) => {
        evento.preventDefault();
        const dados = new FormData(evento.target);
        const pedido = {
            salaId: escolha.sala.id,
            professorId: dados.get('professorId') ? Number(dados.get('professorId')) : null,
            materiaisIds: dados.getAll('materiaisIds').map(Number),
            inicio: escolha.inicio,
            fim: escolha.fim,
            finalidade: dados.get('finalidade')
        };
        ocupado(evento.submitter || evento.target.querySelector('button'), async () => {
            const reserva = await api('POST', '/api/reservas', pedido);
            evento.target.reset();
            evento.target.hidden = true;
            $('resultado-salas').replaceChildren();
            mostrarSecao('reservas');
            avisar('Reserva registrada: ' + NOMES_STATUS[reserva.status].toLowerCase() + '.', 'sucesso');
        });
    });

    // ------------------------------------------------------------------ reservas

    function cartaoDeReserva(reserva, acoes) {
        const sessao = lerSessao();
        return el('article', { classe: 'cartao' },
            el('div', { classe: 'cartao__titulo' },
                el('strong', null, descreverPeriodo(reserva.inicio, reserva.fim)),
                el('span', { classe: 'situacao', 'data-status': reserva.status }, NOMES_STATUS[reserva.status])),
            el('p', { classe: 'detalhe' }, reserva.recursos.map((r) => r.nome).join(' · ')),
            el('p', { classe: 'detalhe' }, reserva.finalidade),
            sessao && sessao.perfil !== 'SOLICITANTE' ? el('p', { classe: 'detalhe' }, 'Solicitante: ' + reserva.solicitante) : null,
            reserva.motivo ? el('p', { classe: 'detalhe' }, 'Motivo: ' + reserva.motivo) : null,
            acoes);
    }

    async function carregarReservas() {
        const lista = $('lista-reservas');
        lista.replaceChildren(el('p', { classe: 'vazio' }, 'Carregando…'));
        try {
            const pagina = await api('GET', '/api/reservas?' + new URLSearchParams({ de: hoje(), tamanho: '100' }));
            lista.replaceChildren(...(pagina.itens.length === 0
                ? [el('p', { classe: 'vazio' }, 'Nenhuma reserva de hoje em diante.')]
                : pagina.itens.map((reserva) => cartaoDeReserva(reserva, acoesDaReserva(reserva)))));
        } catch (erro) {
            lista.replaceChildren();
            avisar(mensagemDeErro(erro), 'erro');
        }
    }

    function acoesDaReserva(reserva) {
        if (!ALTERAVEIS.includes(reserva.status)) {
            return null;
        }
        const formulario = el('form', { classe: 'acoes', hidden: true },
            el('input', { type: 'date', name: 'data', required: true, 'aria-label': 'Nova data' }),
            el('input', { type: 'time', name: 'inicio', step: 60, required: true, 'aria-label': 'Novo início' }),
            el('input', { type: 'time', name: 'fim', step: 60, required: true, 'aria-label': 'Novo fim' }),
            el('button', { type: 'submit', classe: 'botao' }, 'Salvar horário'));
        formulario.addEventListener('submit', (evento) => {
            evento.preventDefault();
            const dados = new FormData(formulario);
            ocupado(formulario.querySelector('button'), async () => {
                await api('PUT', '/api/reservas/' + reserva.id + '/horario', {
                    inicio: instante(dados.get('data'), dados.get('inicio')),
                    fim: instante(dados.get('data'), dados.get('fim'))
                });
                await carregarReservas();
                avisar('Horário alterado.', 'sucesso');
            });
        });
        const botaoCancelar = el('button', { type: 'button', classe: 'botao botao--perigo' }, 'Cancelar reserva');
        botaoCancelar.addEventListener('click', () => ocupado(botaoCancelar, async () => {
            await api('POST', '/api/reservas/' + reserva.id + '/cancelamento', {});
            await carregarReservas();
            avisar('Reserva cancelada.', 'sucesso');
        }));
        return el('div', null,
            el('div', { classe: 'acoes' },
                el('button', {
                    type: 'button',
                    classe: 'botao botao--discreto',
                    aoClicar: () => { formulario.hidden = !formulario.hidden; }
                }, 'Mudar horário'),
                botaoCancelar),
            formulario);
    }

    // ------------------------------------------------------------------ aprovações

    async function carregarAprovacoes() {
        const lista = $('lista-aprovacoes');
        lista.replaceChildren(el('p', { classe: 'vazio' }, 'Carregando…'));
        try {
            const pagina = await api('GET', '/api/reservas?' + new URLSearchParams({ status: 'SOLICITADA', tamanho: '100' }));
            lista.replaceChildren(...(pagina.itens.length === 0
                ? [el('p', { classe: 'vazio' }, 'Nenhuma reserva aguardando aprovação.')]
                : pagina.itens.map((reserva) => cartaoDeReserva(reserva, acoesDeAprovacao(reserva)))));
        } catch (erro) {
            lista.replaceChildren();
            avisar(mensagemDeErro(erro), 'erro');
        }
    }

    function acoesDeAprovacao(reserva) {
        const aprovar = el('button', { type: 'button', classe: 'botao' }, 'Aprovar');
        aprovar.addEventListener('click', () => ocupado(aprovar, async () => {
            await api('POST', '/api/reservas/' + reserva.id + '/aprovacao', {});
            await carregarAprovacoes();
            avisar('Reserva aprovada.', 'sucesso');
        }));
        const motivo = el('input', { type: 'text', maxlength: 500, placeholder: 'Motivo da rejeição', 'aria-label': 'Motivo da rejeição' });
        const rejeitar = el('button', { type: 'button', classe: 'botao botao--perigo' }, 'Rejeitar');
        rejeitar.addEventListener('click', () => ocupado(rejeitar, async () => {
            await api('POST', '/api/reservas/' + reserva.id + '/rejeicao', { motivo: motivo.value });
            await carregarAprovacoes();
            avisar('Reserva rejeitada.', 'sucesso');
        }));
        return el('div', { classe: 'acoes' }, aprovar, motivo, rejeitar);
    }

    // ------------------------------------------------------------------ relatório

    $('form-relatorio').addEventListener('submit', (evento) => {
        evento.preventDefault();
        const dados = new FormData(evento.target);
        const filtros = new URLSearchParams({ inicio: dados.get('inicio'), fim: dados.get('fim') });
        ocupado(evento.submitter || evento.target.querySelector('button'), async () => {
            mostrarRelatorio(await api('GET', '/api/relatorios/utilizacao?' + filtros));
        });
    });

    function numero(valor) {
        return Number(valor).toLocaleString('pt-BR', { maximumFractionDigits: 2 });
    }

    function mostrarRelatorio(relatorio) {
        const t = relatorio.totais;
        const total = (rotulo, valor) => el('div', null, el('dt', null, rotulo), el('dd', null, valor));
        $('totais-relatorio').replaceChildren(
            total('Reservas', numero(t.reservas)),
            total('Horas reservadas', numero(t.horasReservadas)),
            total('Não compareceu', numero(t.naoComparecimentos)),
            total('Conflitos evitados', numero(t.conflitosEvitados)));
        const celula = (rotulo, valor) => el('td', { 'data-rotulo': rotulo }, valor);
        $('tabela-relatorio').tBodies[0].replaceChildren(...relatorio.recursos.map((linha) => el('tr', null,
            celula('Recurso', linha.nome),
            celula('Tipo', NOMES_TIPO[linha.tipo] || linha.tipo),
            celula('Reservas', numero(linha.reservas)),
            celula('Horas', numero(linha.horasReservadas)),
            celula('Não compareceu', numero(linha.naoComparecimentos)),
            celula('Conflitos evitados', numero(linha.conflitosEvitados)))));
        $('tabela-relatorio').hidden = relatorio.recursos.length === 0;
        if (relatorio.recursos.length === 0) {
            avisar('Nenhuma utilização registrada nesse período.', 'info');
        }
    }

    // ------------------------------------------------------------------ início

    function valoresIniciais() {
        const amanha = somarDias(hoje(), 1);
        const pesquisa = $('form-pesquisa');
        pesquisa.elements.data.value = amanha;
        pesquisa.elements.data.min = hoje();
        pesquisa.elements.inicio.value = '08:00';
        pesquisa.elements.fim.value = '10:00';
        const relatorio = $('form-relatorio');
        relatorio.elements.inicio.value = hoje().slice(0, 8) + '01';
        relatorio.elements.fim.value = hoje();
    }

    valoresIniciais();
    const sessao = lerSessao();
    if (sessao) {
        entrar(sessao);
    } else {
        mostrarLogin();
    }
}());
