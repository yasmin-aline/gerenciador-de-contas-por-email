package gerenciadordecontas.estado;

import gerenciadordecontas.planilha.Grade;
import gerenciadordecontas.planilha.SheetsGateway;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pequeno "chave e valor" na aba "_estado" da planilha. Guarda o que já foi feito
 * (e-mails processados, avisos enviados, confirmações pendentes), para nada repetir.
 * Colunas: A chave, B valor, C quando.
 */
public final class Estado {

    public static final String ABA = "_estado";

    private final SheetsGateway gateway;
    private final Clock relogio;
    private boolean abaPronta;

    public Estado(SheetsGateway gateway, Clock relogio) {
        this.gateway = gateway;
        this.relogio = relogio;
    }

    /** Entradas cuja chave começa com o prefixo ("" lista todas). */
    public List<EntradaEstado> listar(String prefixo) {
        garantirAba();
        List<List<String>> linhas = gateway.lerIntervalo(ABA, "A:C");

        List<EntradaEstado> resultado = new ArrayList<>();
        for (int i = 1; i < linhas.size(); i++) { // linha 0 é o cabeçalho
            String chave = Grade.celula(linhas, i, 0);
            if (chave.isEmpty() || !chave.startsWith(prefixo)) {
                continue;
            }
            resultado.add(new EntradaEstado(chave, Grade.celula(linhas, i, 1), i + 1));
        }
        return resultado;
    }

    /** Valor da chave, ou vazio se a chave não existe. */
    public Optional<String> obter(String chave) {
        return exata(chave).map(EntradaEstado::valor);
    }

    public void gravar(String chave, String valor) {
        String quando = LocalDateTime.now(relogio).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        Optional<EntradaEstado> existente = exata(chave);

        if (existente.isEmpty()) {
            gateway.acrescentarLinha(ABA, List.of(chave, valor, quando));
            return;
        }

        Map<String, Object> celulas = new LinkedHashMap<>();
        celulas.put("B" + existente.get().linha(), valor);
        celulas.put("C" + existente.get().linha(), quando);
        gateway.escreverCelulas(ABA, celulas);
    }

    private Optional<EntradaEstado> exata(String chave) {
        EntradaEstado ultima = null;
        for (EntradaEstado entrada : listar(chave)) {
            if (entrada.chave().equals(chave)) {
                ultima = entrada;
            }
        }
        return Optional.ofNullable(ultima);
    }

    private void garantirAba() {
        if (abaPronta) {
            return;
        }
        if (gateway.garantirAba(ABA)) {
            gateway.acrescentarLinha(ABA, List.of("Chave", "Valor", "Quando"));
        }
        abaPronta = true;
    }
}
