package gerenciadordecontas.aviso;

import gerenciadordecontas.modelo.Bloco;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Quando avisar: 3 dias e 1 dia antes do dia 15 (quando ela recebe) e antes do fim do mês.
 * Se o PC estava desligado no dia certo, o aviso sai no primeiro tick depois, uma vez só.
 */
public final class AvisoPolicy {

    /** Do mais próximo para o mais distante. */
    private static final int[] DIAS_ANTES = {1, 3};

    private AvisoPolicy() {
    }

    public static String chaveDe(LocalDate ancora, int diasAntes) {
        return "aviso:" + ancora + ":" + diasAntes;
    }

    public static LocalDate ancoraDe(int ano, int mes, Bloco bloco) {
        return bloco == Bloco.DIA_15 ? LocalDate.of(ano, mes, 15) : YearMonth.of(ano, mes).atEndOfMonth();
    }

    /** @param jaEnviado diz se a chave de um aviso já foi registrada como enviada */
    public static List<AvisoDevido> devidos(LocalDate hoje, Predicate<String> jaEnviado) {
        List<AvisoDevido> devidos = new ArrayList<>();

        for (Bloco bloco : List.of(Bloco.DIA_15, Bloco.FIM_DO_MES)) {
            LocalDate ancora = ancoraDe(hoje.getYear(), hoje.getMonthValue(), bloco);
            if (hoje.isAfter(ancora)) {
                continue; // o vencimento deste mês já passou
            }

            // Gatilho mais recente já alcançado: o de menor "dias antes" cuja data é hoje ou passada.
            for (int dias : DIAS_ANTES) {
                if (ancora.minusDays(dias).isAfter(hoje)) {
                    continue;
                }
                String chave = chaveDe(ancora, dias);
                if (!jaEnviado.test(chave)) {
                    devidos.add(new AvisoDevido(ancora, bloco, dias, chave));
                }
                break; // só o gatilho mais recente conta; os mais antigos ficam descartados
            }
        }
        return devidos;
    }
}
