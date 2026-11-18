package gerenciadordecontas.estado;

import gerenciadordecontas.modelo.CelulaMudanca;
import gerenciadordecontas.planilha.Grade;
import gerenciadordecontas.planilha.SheetsGateway;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Histórico de mudanças na aba "_log" da própria planilha (assim funciona em qualquer lugar onde rodar).
 * Cada linha é uma célula alterada; várias células da mesma ação compartilham o mesmo grupo.
 * Colunas: A grupo, B quando, C descrição, D aba, E célula, F antes, G depois, H número?, I desfeito.
 */
public final class RegistroMudancas {

    public static final String ABA = "_log";

    /** Mudança revertida: descrição da ação e quantas células voltaram ao que eram. */
    public record Desfeito(String descricao, int celulas) {
    }

    private final SheetsGateway gateway;
    private final Clock relogio;
    private boolean abaPronta;

    public RegistroMudancas(SheetsGateway gateway, Clock relogio) {
        this.gateway = gateway;
        this.relogio = relogio;
    }

    public void registrar(String grupo, String descricao, List<CelulaMudanca> mudancas) {
        garantirAba();
        String quando = LocalDateTime.now(relogio).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        for (CelulaMudanca m : mudancas) {
            gateway.acrescentarLinha(ABA, List.of(
                    grupo, quando, descricao, m.aba(), m.celula(), m.antes(), m.depois(),
                    m.ehNumero() ? "sim" : "nao", ""));
        }
    }

    /** Reverte o último grupo de mudanças que ainda não foi desfeito. Vazio se não há nada a desfazer. */
    public Optional<Desfeito> desfazerUltimo() {
        garantirAba();
        List<List<String>> linhas = gateway.lerIntervalo(ABA, "A:I");

        // Linha 0 é o cabeçalho. Procura de baixo para cima a última mudança não desfeita.
        int alvo = -1;
        for (int i = linhas.size() - 1; i >= 1; i--) {
            if (!Grade.celula(linhas, i, 0).isEmpty() && !"sim".equals(Grade.celula(linhas, i, 8))) {
                alvo = i;
                break;
            }
        }
        if (alvo < 0) {
            return Optional.empty();
        }

        String grupo = Grade.celula(linhas, alvo, 0);
        String descricao = Grade.celula(linhas, alvo, 2);

        Map<String, Map<String, Object>> porAba = new LinkedHashMap<>();
        Map<String, Object> marcas = new LinkedHashMap<>();
        int total = 0;

        // Do fim para o começo: se a mesma célula mudou duas vezes no grupo, vale o "antes" mais antigo.
        for (int i = linhas.size() - 1; i >= 1; i--) {
            if (!Grade.celula(linhas, i, 0).equals(grupo) || "sim".equals(Grade.celula(linhas, i, 8))) {
                continue;
            }
            String aba = Grade.celula(linhas, i, 3);
            String celula = Grade.celula(linhas, i, 4);
            String antes = Grade.celula(linhas, i, 5);
            boolean ehNumero = "sim".equals(Grade.celula(linhas, i, 7));

            porAba.computeIfAbsent(aba, a -> new LinkedHashMap<>()).put(celula, valorParaEscrita(antes, ehNumero));
            marcas.put("I" + (i + 1), "sim");
            total++;
        }

        for (Map.Entry<String, Map<String, Object>> par : porAba.entrySet()) {
            gateway.escreverCelulas(par.getKey(), par.getValue());
        }
        gateway.escreverCelulas(ABA, marcas);

        return Optional.of(new Desfeito(descricao, total));
    }

    private static Object valorParaEscrita(String antes, boolean ehNumero) {
        if (antes.isEmpty()) {
            return "";
        }
        if (ehNumero) {
            try {
                return new BigDecimal(antes);
            } catch (NumberFormatException e) {
                return antes;
            }
        }
        return antes;
    }

    private void garantirAba() {
        if (abaPronta) {
            return;
        }
        if (gateway.garantirAba(ABA)) {
            gateway.acrescentarLinha(ABA, List.of(
                    "Grupo", "Quando", "Descrição", "Aba", "Célula", "Antes", "Depois", "Número", "Desfeito"));
        }
        abaPronta = true;
    }
}
