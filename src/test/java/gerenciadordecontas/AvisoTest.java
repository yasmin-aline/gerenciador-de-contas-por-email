package gerenciadordecontas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gerenciadordecontas.aviso.AvisoComposer;
import gerenciadordecontas.aviso.AvisoDevido;
import gerenciadordecontas.aviso.AvisoEmail;
import gerenciadordecontas.aviso.AvisoPolicy;
import gerenciadordecontas.config.Settings;
import gerenciadordecontas.modelo.Bloco;
import gerenciadordecontas.modelo.Conta;
import gerenciadordecontas.planilha.PlanilhaContas;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AvisoTest {

    // Novembro de 2026: dia 15 é domingo; o último dia é 30.
    private static List<AvisoDevido> devidos(LocalDate hoje, String... jaEnviados) {
        Set<String> enviados = Set.of(jaEnviados);
        return AvisoPolicy.devidos(hoje, enviados::contains);
    }

    @Test
    void nada_e_devido_antes_do_primeiro_gatilho() {
        assertTrue(devidos(LocalDate.of(2026, 11, 11)).isEmpty());
    }

    @Test
    void tres_dias_antes_do_dia_15_sai_o_aviso_do_dia_15() {
        List<AvisoDevido> devidos = devidos(LocalDate.of(2026, 11, 12));

        assertEquals(1, devidos.size());
        assertEquals(Bloco.DIA_15, devidos.get(0).bloco());
        assertEquals(3, devidos.get(0).diasAntes());
        assertEquals("aviso:2026-11-15:3", devidos.get(0).chave());
    }

    @Test
    void o_mesmo_aviso_nunca_sai_duas_vezes() {
        assertTrue(devidos(LocalDate.of(2026, 11, 12), "aviso:2026-11-15:3").isEmpty());
    }

    @Test
    void um_dia_antes_sai_o_segundo_aviso() {
        List<AvisoDevido> devidos = devidos(LocalDate.of(2026, 11, 14), "aviso:2026-11-15:3");

        assertEquals(1, devidos.size());
        assertEquals(1, devidos.get(0).diasAntes());
    }

    @Test
    void dia_perdido_envia_so_o_aviso_mais_recente_e_uma_vez() {
        // PC desligado dia 12 e 13: no dia 14 sai um aviso só (o de 1 dia); o de 3 dias é descartado.
        List<AvisoDevido> devidos = devidos(LocalDate.of(2026, 11, 14));
        assertEquals(1, devidos.size());
        assertEquals(1, devidos.get(0).diasAntes());

        // Depois de enviado, nos dias seguintes não repete.
        assertTrue(devidos(LocalDate.of(2026, 11, 15), "aviso:2026-11-15:1").isEmpty());
    }

    @Test
    void depois_do_dia_15_so_restam_os_avisos_do_fim_do_mes() {
        assertTrue(devidos(LocalDate.of(2026, 11, 16)).isEmpty());

        List<AvisoDevido> devidos = devidos(LocalDate.of(2026, 11, 27));
        assertEquals(1, devidos.size());
        assertEquals(Bloco.FIM_DO_MES, devidos.get(0).bloco());
        assertEquals("aviso:2026-11-30:3", devidos.get(0).chave());
    }

    @Test
    void email_lista_pendencias_entradas_e_saldo_previsto() {
        List<Conta> contas = new PlanilhaContas(PlanilhaDeExemplo.novembro()).lerMes(11);

        AvisoEmail email = AvisoComposer.montar(
                LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 15), Bloco.DIA_15, contas, Settings.padrao());

        assertTrue(email.assunto().contains("vence em 3 dias"));
        assertTrue(email.assunto().contains("2 pendente(s)"));
        assertTrue(email.corpo().contains("Conta de Luz — R$ 200,00"));
        assertTrue(email.corpo().contains("Inglês — R$ 300,00"));
        assertFalse(email.corpo().contains("Internet")); // já paga
        assertTrue(email.corpo().contains("Entra em 15/11: R$ 2.200,00"));
        assertTrue(email.corpo().contains("Cartão Inter")); // pendente do outro bloco
        // entradas 4.000 − contas (200+120+300+900+450 = 1.970) = 2.030
        assertTrue(email.corpo().contains("Saldo previsto do mês: R$ 2.030,00"));
    }
}
