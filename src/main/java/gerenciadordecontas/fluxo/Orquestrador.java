package gerenciadordecontas.fluxo;

import gerenciadordecontas.aviso.AvisoComposer;
import gerenciadordecontas.aviso.AvisoDevido;
import gerenciadordecontas.aviso.AvisoEmail;
import gerenciadordecontas.aviso.AvisoPolicy;
import gerenciadordecontas.config.Settings;
import gerenciadordecontas.email.Caixa;
import gerenciadordecontas.email.Citacao;
import gerenciadordecontas.email.EmailRecebido;
import gerenciadordecontas.estado.Estado;
import gerenciadordecontas.modelo.Bloco;
import gerenciadordecontas.modelo.Conta;
import gerenciadordecontas.planilha.PlanilhaContas;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * O "tick": o único comando que o agendador chama. Em cada execução envia os avisos que
 * já estão na hora (e ainda não saíram) e processa as respostas novas na caixa de e-mail.
 */
public final class Orquestrador {

    private static final int MAX_TENTATIVAS = 3;

    private final PlanilhaContas planilha;
    private final Estado estado;
    private final Caixa caixa;
    private final ProcessadorDeRespostas processador;
    private final Settings settings;
    private final Clock relogio;
    private final String destinatario;
    private final Consumer<String> log;

    public Orquestrador(
            PlanilhaContas planilha,
            Estado estado,
            Caixa caixa,
            ProcessadorDeRespostas processador,
            Settings settings,
            Clock relogio,
            String destinatario,
            Consumer<String> log) {
        this.planilha = planilha;
        this.estado = estado;
        this.caixa = caixa;
        this.processador = processador;
        this.settings = settings;
        this.relogio = relogio;
        this.destinatario = destinatario;
        this.log = log;
    }

    /** Avisos devidos + respostas novas. Devolve quantas etapas falharam (0 = tudo certo). */
    public int tick() {
        int falhas = 0;
        falhas += proteger("avisos", this::enviarAvisosDevidos);
        falhas += proteger("respostas", this::processarCaixa);
        return falhas;
    }

    public void enviarAvisosDevidos() {
        LocalDate hoje = LocalDate.now(relogio);
        Set<String> enviados = new HashSet<>();
        estado.listar("aviso:").forEach(e -> enviados.add(e.chave()));

        List<AvisoDevido> devidos = AvisoPolicy.devidos(hoje, enviados::contains);
        for (AvisoDevido devido : devidos) {
            List<Conta> contas = planilha.lerMes(devido.ancora().getMonthValue());
            AvisoEmail email = AvisoComposer.montar(hoje, devido.ancora(), devido.bloco(), contas, settings);
            caixa.enviar(destinatario, email.assunto(), email.corpo());
            estado.gravar(devido.chave(), "enviado");
            log.accept("Aviso enviado: " + email.assunto());
        }

        if (devidos.isEmpty()) {
            log.accept("Nenhum aviso a enviar hoje.");
        }
    }

    /** Envia agora o aviso do próximo vencimento, sem conferir a agenda nem registrar como enviado. */
    public void enviarAvisoAgora() {
        LocalDate hoje = LocalDate.now(relogio);
        Bloco bloco = Bloco.DIA_15;
        LocalDate ancora = AvisoPolicy.ancoraDe(hoje.getYear(), hoje.getMonthValue(), bloco);
        if (hoje.isAfter(ancora)) {
            bloco = Bloco.FIM_DO_MES;
            ancora = AvisoPolicy.ancoraDe(hoje.getYear(), hoje.getMonthValue(), bloco);
        }

        List<Conta> contas = planilha.lerMes(hoje.getMonthValue());
        AvisoEmail email = AvisoComposer.montar(hoje, ancora, bloco, contas, settings);
        caixa.enviar(destinatario, email.assunto(), email.corpo());
        log.accept("Aviso enviado: " + email.assunto());
    }

    public void processarCaixa() {
        List<EmailRecebido> novas = caixa.buscarNaoLidosDe(destinatario);
        if (novas.isEmpty()) {
            log.accept("Nenhuma resposta nova.");
            return;
        }

        List<EmailRecebido> ordenadas = novas.stream().sorted(Comparator.comparing(EmailRecebido::data)).toList();
        for (EmailRecebido email : ordenadas) {
            // Segurança: só obedece ao seu endereço. O resto é ignorado e fica sem ler.
            if (!destinatario.equalsIgnoreCase(email.de())) {
                log.accept("Ignorei e-mail de outro remetente (" + email.de() + ").");
                continue;
            }

            String chave = "email:" + email.id();
            if (estado.obter(chave).isPresent()) {
                caixa.marcarComoLido(email.handle());
                continue; // já processado antes
            }

            try {
                String texto = Citacao.limpar(email.texto());
                if (!texto.isEmpty()) {
                    String resposta = processador.processar(texto);
                    caixa.enviar(destinatario, assuntoDaResposta(email.assunto()), resposta);
                    log.accept("Respondi: " + email.assunto());
                }
                estado.gravar(chave, "ok");
                caixa.marcarComoLido(email.handle());
            } catch (RuntimeException e) {
                registrarFalha(email, chave, e);
            }
        }
    }

    // Erro passageiro (rede, API fora do ar): tenta de novo no próximo tick. Depois de 3 falhas, avisa e desiste.
    private void registrarFalha(EmailRecebido email, String chave, RuntimeException erro) {
        String chaveTentativas = "tentativas:" + email.id();
        Optional<String> anterior = estado.obter(chaveTentativas);
        int tentativas = 1;
        if (anterior.isPresent()) {
            try {
                tentativas = Integer.parseInt(anterior.get().strip()) + 1;
            } catch (NumberFormatException ignorado) {
                tentativas = 1;
            }
        }
        estado.gravar(chaveTentativas, String.valueOf(tentativas));
        log.accept("Falha ao processar \"" + email.assunto() + "\" (tentativa " + tentativas + "/" + MAX_TENTATIVAS
                + "): " + erro.getMessage());

        if (tentativas < MAX_TENTATIVAS) {
            return;
        }

        caixa.enviar(
                destinatario,
                assuntoDaResposta(email.assunto()),
                "Não consegui processar sua mensagem depois de várias tentativas.\n"
                        + "Motivo: " + erro.getMessage() + "\n"
                        + "Confira a planilha antes de reenviar: se o erro foi no meio da gravação, algo pode ter "
                        + "mudado. Responda \"desfazer\" para reverter a última mudança.");
        estado.gravar(chave, "erro");
        caixa.marcarComoLido(email.handle());
    }

    private int proteger(String etapa, Runnable acao) {
        try {
            acao.run();
            return 0;
        } catch (RuntimeException e) {
            log.accept("Erro na etapa " + etapa + ": " + e.getMessage());
            return 1;
        }
    }

    private static String assuntoDaResposta(String assunto) {
        return assunto.regionMatches(true, 0, "Re:", 0, 3) ? assunto : "Re: " + assunto;
    }
}
