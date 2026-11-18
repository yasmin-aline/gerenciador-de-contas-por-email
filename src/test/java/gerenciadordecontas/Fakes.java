package gerenciadordecontas;

import gerenciadordecontas.email.Caixa;
import gerenciadordecontas.email.EmailRecebido;
import gerenciadordecontas.ia.InterpretadorDeTexto;
import gerenciadordecontas.modelo.Acao;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Dublês de teste: relógio, interpretador de texto e caixa de e-mail. */
public final class Fakes {

    private Fakes() {
    }

    public static BigDecimal v(String valor) {
        return new BigDecimal(valor);
    }

    /** Relógio com data e hora que o teste controla. */
    public static final class RelogioFalso extends Clock {
        private LocalDateTime agora;

        public RelogioFalso(LocalDateTime agora) {
            this.agora = agora;
        }

        public void definir(LocalDateTime novo) {
            this.agora = novo;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora.toInstant(ZoneOffset.UTC);
        }
    }

    /** Devolve as ações que o teste programou, no lugar de chamar a IA. */
    public static final class InterpretadorFalso implements InterpretadorDeTexto {
        public final List<Acao> acoes = new ArrayList<>();
        public int chamadas;
        public String ultimoTexto;

        @Override
        public List<Acao> interpretar(String texto, List<String> contasDoMes) {
            chamadas++;
            ultimoTexto = texto;
            return new ArrayList<>(acoes);
        }
    }

    public record Enviado(String para, String assunto, String corpo) {
    }

    public record Anexado(String para, String assunto, String corpo, String nomeArquivo, String tipoMime,
            byte[] conteudo) {
    }

    /** Entrega falsa: guarda o que foi pedido e pode falhar de propósito. */
    public static final class EntregadorFalso implements gerenciadordecontas.fluxo.Entregador {
        public static final String LINK = "https://docs.google.com/spreadsheets/d/ID-FALSO/edit";
        public final List<String[]> anexadas = new ArrayList<>();
        public RuntimeException falha;

        @Override
        public String linkDaPlanilha() {
            return LINK;
        }

        @Override
        public void enviarPlanilhaAnexada(String assunto, String corpo) {
            if (falha != null) {
                throw falha;
            }
            anexadas.add(new String[] {assunto, corpo});
        }
    }

    public static final class CaixaFalsa implements Caixa {
        public final List<EmailRecebido> entrada = new ArrayList<>();
        public final Set<String> lidos = new HashSet<>();
        public final List<Enviado> enviados = new ArrayList<>();
        public final List<Anexado> anexados = new ArrayList<>();

        @Override
        public List<EmailRecebido> buscarNaoLidosDe(String remetente) {
            // Devolve tudo que não foi lido, de qualquer remetente: o Orquestrador é quem confere o remetente.
            return entrada.stream().filter(m -> !lidos.contains(m.handle())).toList();
        }

        @Override
        public void marcarComoLido(String handle) {
            lidos.add(handle);
        }

        @Override
        public void enviar(String para, String assunto, String corpo) {
            enviados.add(new Enviado(para, assunto, corpo));
        }

        @Override
        public void enviarComAnexo(String para, String assunto, String corpo, String nomeArquivo, String tipoMime,
                byte[] conteudo) {
            anexados.add(new Anexado(para, assunto, corpo, nomeArquivo, tipoMime, conteudo));
        }
    }
}
