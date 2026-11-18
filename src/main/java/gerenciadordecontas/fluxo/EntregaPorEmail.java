package gerenciadordecontas.fluxo;

import gerenciadordecontas.email.Caixa;
import gerenciadordecontas.planilha.SheetsGateway;
import java.time.Clock;
import java.time.LocalDate;

/** Baixa a planilha como .xlsx e manda para o seu e-mail. */
public final class EntregaPorEmail implements Entregador {

    private static final String TIPO_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final SheetsGateway planilha;
    private final Caixa caixa;
    private final String destinatario;
    private final String planilhaId;
    private final Clock relogio;

    public EntregaPorEmail(
            SheetsGateway planilha, Caixa caixa, String destinatario, String planilhaId, Clock relogio) {
        this.planilha = planilha;
        this.caixa = caixa;
        this.destinatario = destinatario;
        this.planilhaId = planilhaId;
        this.relogio = relogio;
    }

    @Override
    public String linkDaPlanilha() {
        return "https://docs.google.com/spreadsheets/d/" + planilhaId + "/edit";
    }

    @Override
    public void enviarPlanilhaAnexada(String assunto, String corpo) {
        byte[] arquivo = planilha.exportarXlsx();
        String nome = "contas-" + LocalDate.now(relogio) + ".xlsx";
        caixa.enviarComAnexo(destinatario, assunto, corpo, nome, TIPO_XLSX, arquivo);
    }
}
