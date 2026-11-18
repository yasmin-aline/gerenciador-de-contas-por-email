package gerenciadordecontas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gerenciadordecontas.email.EmailRecebido;
import gerenciadordecontas.modelo.Acao;
import gerenciadordecontas.modelo.Bloco;
import gerenciadordecontas.modelo.TipoAcao;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class FluxoTest {

    // ---- Processador de respostas ----

    @Test
    void pagar_com_valor_proximo_do_previsto_atualiza_a_planilha_e_confirma() {
        Cenario c = Cenario.padrao();
        c.interpretador.acoes.add(Cenario.pagar("luz", "210"));

        String resposta = c.processador.processar("paguei a luz, 210");

        assertEquals("✅ Pago", c.sheets.get("Nov", "C4"));
        assertEquals("R$ 210,00", c.sheets.get("Nov", "B4"));
        assertTrue(resposta.contains("✓ Conta de Luz"));
        assertTrue(resposta.contains("previsto R$ 200,00"));
        assertTrue(resposta.contains("desfazer"));
    }

    @Test
    void pagar_sem_valor_mantem_o_valor_previsto() {
        Cenario c = Cenario.padrao();
        c.interpretador.acoes.add(Cenario.pagar("inglês", null));

        c.processador.processar("paguei o inglês");

        assertEquals("✅ Pago", c.sheets.get("Nov", "C6"));
        assertEquals("R$ 300,00", c.sheets.get("Nov", "B6"));
    }

    @Test
    void valor_muito_diferente_do_previsto_espera_confirmacao() {
        Cenario c = Cenario.padrao();
        c.interpretador.acoes.add(Cenario.pagar("luz", "400")); // previsto 200: dobrou

        String pergunta = c.processador.processar("paguei a luz, 400");

        assertTrue(pergunta.contains("confirmação"));
        assertEquals("Pendente", c.sheets.get("Nov", "C4")); // nada mudou ainda
        assertEquals("R$ 200,00", c.sheets.get("Nov", "B4"));

        String resposta = c.processador.processar("sim");

        assertEquals("✅ Pago", c.sheets.get("Nov", "C4"));
        assertEquals("R$ 400,00", c.sheets.get("Nov", "B4"));
        assertTrue(resposta.contains("✓"));
        assertEquals(1, c.interpretador.chamadas); // o "sim" não precisou da IA
    }

    @Test
    void responder_nao_cancela_a_acao_pendente() {
        Cenario c = Cenario.padrao();
        c.interpretador.acoes.add(Cenario.pagar("luz", "400"));
        c.processador.processar("paguei a luz, 400");

        String resposta = c.processador.processar("não");

        assertTrue(resposta.contains("cancelei"));
        assertEquals("Pendente", c.sheets.get("Nov", "C4"));
    }

    @Test
    void pouca_confianca_pergunta_antes_de_mexer() {
        Cenario c = Cenario.padrao();
        c.interpretador.acoes.add(Cenario.pagar("luz", "200", 0.4));

        String pergunta = c.processador.processar("acho que paguei a luz");

        assertTrue(pergunta.contains("confirmação"));
        assertEquals("Pendente", c.sheets.get("Nov", "C4"));
    }

    @Test
    void conta_nova_sem_bloco_pergunta_o_bloco_e_depois_cadastra() {
        Cenario c = Cenario.padrao();
        c.interpretador.acoes.add(Cenario.cadastrar("IPTU", "400", null, 20));

        String pergunta = c.processador.processar("nova conta: IPTU, 400, vence dia 20");

        assertTrue(pergunta.contains("Em qual bloco"));
        assertEquals("", c.sheets.get("Nov", "E6")); // nada gravado ainda

        String resposta = c.processador.processar("fim do mês");

        assertEquals("IPTU", c.sheets.get("Nov", "E6"));
        assertEquals("R$ 400,00", c.sheets.get("Nov", "F6"));
        assertEquals("⏳ Pendente", c.sheets.get("Nov", "G6"));
        assertTrue(resposta.contains("Cadastrei IPTU"));
        assertTrue(resposta.contains("não foi gravado")); // o vencimento não tem coluna na planilha
    }

    @Test
    void conta_nova_em_bloco_cheio_e_recusada_sem_inserir_linha() {
        Cenario c = Cenario.padrao();
        c.sheets.set("Nov", "A7", "Água").set("Nov", "A8", "Gás");
        c.interpretador.acoes.add(Cenario.cadastrar("IPTU", "400", Bloco.DIA_15, null));

        String resposta = c.processador.processar("nova conta: IPTU, 400, dia 15");

        assertTrue(resposta.contains("sem vaga livre"));
        assertEquals("Total", c.sheets.get("Nov", "A9")); // a linha de total não se mexeu
    }

    @Test
    void conta_que_ja_existe_nao_e_cadastrada_de_novo() {
        Cenario c = Cenario.padrao();
        c.interpretador.acoes.add(Cenario.cadastrar("Internet", "120", Bloco.DIA_15, null));

        String resposta = c.processador.processar("nova conta: Internet, 120, dia 15");

        assertTrue(resposta.contains("Já existe"));
        assertEquals("", c.sheets.get("Nov", "A7"));
    }

    @Test
    void conta_desconhecida_nao_muda_nada_e_lista_as_contas_do_mes() {
        Cenario c = Cenario.padrao();
        c.interpretador.acoes.add(Cenario.pagar("padaria", "50"));

        String resposta = c.processador.processar("paguei a padaria, 50");

        assertTrue(resposta.contains("Não achei"));
        assertTrue(resposta.contains("Conta de Luz"));
    }

    @Test
    void desfazer_restaura_status_e_valor_anteriores() {
        Cenario c = Cenario.padrao();
        c.interpretador.acoes.add(Cenario.pagar("luz", "210"));
        c.processador.processar("paguei a luz, 210");

        String resposta = c.processador.processar("desfazer");

        assertEquals("Pendente", c.sheets.get("Nov", "C4"));
        assertEquals("R$ 200,00", c.sheets.get("Nov", "B4"));
        assertTrue(resposta.contains("Desfeito"));
    }

    @Test
    void desfazer_um_cadastro_limpa_a_linha_e_um_segundo_desfazer_nao_tem_o_que_fazer() {
        Cenario c = Cenario.padrao();
        c.interpretador.acoes.add(Cenario.cadastrar("IPTU", "400", Bloco.FIM_DO_MES, null));
        c.processador.processar("nova conta: IPTU, 400, fim do mês");
        assertEquals("IPTU", c.sheets.get("Nov", "E6"));

        c.processador.processar("desfazer");

        assertEquals("", c.sheets.get("Nov", "E6"));
        assertEquals("", c.sheets.get("Nov", "F6"));
        assertEquals("", c.sheets.get("Nov", "G6"));

        String segunda = c.processador.processar("desfazer");
        assertTrue(segunda.contains("Não há nenhuma mudança"));
    }

    @Test
    void desfazer_reverte_so_a_ultima_mudanca() {
        Cenario c = Cenario.padrao();
        c.interpretador.acoes.add(Cenario.pagar("luz", "210"));
        c.processador.processar("paguei a luz, 210");
        c.interpretador.acoes.clear();
        c.interpretador.acoes.add(Cenario.pagar("inglês", "300"));
        c.processador.processar("paguei o inglês");

        c.processador.processar("desfazer");

        assertEquals("Pendente", c.sheets.get("Nov", "C6")); // a última foi desfeita
        assertEquals("✅ Pago", c.sheets.get("Nov", "C4")); // a anterior continua
    }

    // ---- Nomes repetidos entre os blocos (como "Cartão Inter" no Dia 15 e no Fim do mês) ----

    private static Cenario comCartaoInterNosDoisBlocos(String statusDoDia15) {
        Cenario c = Cenario.padrao();
        c.sheets.set("Nov", "A7", "Cartão inter").set("Nov", "B7", "R$ 785,00").set("Nov", "C7", statusDoDia15);
        return c;
    }

    @Test
    void nome_repetido_usa_a_unica_conta_que_ainda_esta_pendente() {
        Cenario c = comCartaoInterNosDoisBlocos("Pago"); // a do Dia 15 já foi paga; a do Fim do mês (E4) não
        c.interpretador.acoes.add(Cenario.pagar("cartão inter", "900"));

        String resposta = c.processador.processar("paguei o cartão inter, 900");

        assertEquals("✅ Pago", c.sheets.get("Nov", "G4"));
        assertEquals("Pago", c.sheets.get("Nov", "C7")); // a outra continua como estava
        assertEquals("R$ 785,00", c.sheets.get("Nov", "B7"));
        assertTrue(resposta.contains("bloco fim do mês"));
        assertTrue(resposta.contains("Havia mais de uma conta"));
    }

    @Test
    void nome_repetido_com_as_duas_pendentes_pergunta_o_bloco_e_nao_muda_nada() {
        Cenario c = comCartaoInterNosDoisBlocos("Pendente");
        c.interpretador.acoes.add(Cenario.pagar("cartão inter", "900"));

        String resposta = c.processador.processar("paguei o cartão inter, 900");

        assertTrue(resposta.contains("[dia 15]"));
        assertTrue(resposta.contains("[fim do mês]"));
        assertEquals("Pendente", c.sheets.get("Nov", "C7"));
        assertEquals("Pendente", c.sheets.get("Nov", "G4"));
    }

    @Test
    void nome_repetido_com_o_bloco_dito_pela_pessoa_escolhe_aquele_bloco() {
        Cenario c = comCartaoInterNosDoisBlocos("Pendente");
        c.interpretador.acoes.add(
                new Acao(TipoAcao.PAGAR, "cartão inter", Fakes.v("785"), Bloco.DIA_15, null, 0.95));

        c.processador.processar("paguei o cartão inter do dia 15, 785");

        assertEquals("✅ Pago", c.sheets.get("Nov", "C7"));
        assertEquals("Pendente", c.sheets.get("Nov", "G4"));
    }

    @Test
    void fatura_e_conta_de_com_o_mesmo_nome_tambem_sao_desempatadas() {
        Cenario c = Cenario.padrao();
        c.sheets.set("Nov", "E6", "Conta de Inglês").set("Nov", "F6", "R$ 450,00").set("Nov", "G6", "Pendente");
        // No Dia 15 já existe "Inglês" (C6 Pendente): as duas estão pendentes e a frase não diz qual.
        c.interpretador.acoes.add(Cenario.pagar("inglês", "450"));

        String resposta = c.processador.processar("paguei o inglês, 450");

        assertTrue(resposta.contains("pode ser mais de uma conta"));
        assertEquals("Pendente", c.sheets.get("Nov", "G6"));
    }

    // ---- Orquestrador ----

    private static EmailRecebido email(String id, String de, String texto) {
        return new EmailRecebido(id, "Re: [Contas] aviso", texto, de, Instant.parse("2026-11-16T18:00:00Z"), "1");
    }

    @Test
    void resposta_do_dono_atualiza_a_planilha_confirma_por_email_e_marca_como_lida() {
        Cenario c = Cenario.padrao();
        c.interpretador.acoes.add(Cenario.pagar("luz", "210"));
        c.caixa.entrada.add(email("<m1@x>", Cenario.DESTINATARIO,
                "paguei a luz, 210\n\nEm seg., 16 de nov. de 2026 às 18:00, Contas Bot <b@x.com> escreveu:\n> resumo"));

        int falhas = c.orquestrador.tick();

        assertEquals(0, falhas);
        assertEquals("✅ Pago", c.sheets.get("Nov", "C4"));
        assertEquals("paguei a luz, 210", c.interpretador.ultimoTexto); // a citação foi cortada antes da IA
        assertEquals(1, c.caixa.enviados.size());
        assertEquals(Cenario.DESTINATARIO, c.caixa.enviados.get(0).para());
        assertTrue(c.caixa.enviados.get(0).corpo().contains("✓ Conta de Luz"));
        assertTrue(c.caixa.lidos.contains("1"));
    }

    @Test
    void o_mesmo_email_nunca_e_processado_duas_vezes() {
        Cenario c = Cenario.padrao();
        c.interpretador.acoes.add(Cenario.pagar("luz", "210"));
        c.caixa.entrada.add(email("<m1@x>", Cenario.DESTINATARIO, "paguei a luz, 210"));
        c.orquestrador.tick();

        // O mesmo e-mail reaparece como não lido (ex.: falha ao marcar como lido).
        c.caixa.lidos.clear();
        c.orquestrador.tick();

        assertEquals(1, c.interpretador.chamadas);
        assertEquals(1, c.caixa.enviados.size());
    }

    @Test
    void email_de_outra_pessoa_e_ignorado() {
        Cenario c = Cenario.padrao();
        c.interpretador.acoes.add(Cenario.pagar("luz", "210"));
        c.caixa.entrada.add(email("<m2@x>", "intruso@example.com", "paguei a luz, 210"));

        c.orquestrador.tick();

        assertEquals("Pendente", c.sheets.get("Nov", "C4"));
        assertTrue(c.caixa.enviados.isEmpty());
        assertEquals(0, c.interpretador.chamadas);
    }

    @Test
    void aviso_devido_sai_uma_vez_e_nao_se_repete_no_proximo_tick() {
        Cenario c = new Cenario(LocalDateTime.of(2026, 11, 12, 8, 0)); // 3 dias antes do dia 15

        c.orquestrador.tick();
        c.orquestrador.tick();

        assertEquals(1, c.caixa.enviados.size());
        assertTrue(c.caixa.enviados.get(0).assunto().contains("Dia 15 vence em 3 dias"));
        assertTrue(c.caixa.enviados.get(0).corpo().contains("Conta de Luz"));
    }

    @Test
    void email_que_falha_e_tentado_de_novo_e_depois_de_tres_falhas_a_dona_e_avisada() {
        Cenario c = Cenario.padrao();
        c.interpretador.acoes.add(Cenario.pagar("luz", "210"));
        c.caixa.entrada.add(email("<m3@x>", Cenario.DESTINATARIO, "paguei a luz, 210"));
        c.relogio.definir(LocalDateTime.of(2026, 12, 20, 8, 0)); // dezembro: a aba Dez não existe, a leitura falha

        c.orquestrador.tick();
        c.orquestrador.tick();
        assertTrue(c.caixa.enviados.isEmpty()); // 2 falhas: ainda tentando em silêncio

        c.orquestrador.tick();

        assertEquals(1, c.caixa.enviados.size());
        assertTrue(c.caixa.enviados.get(0).corpo().contains("Não consegui processar"));
        assertTrue(c.caixa.lidos.contains("1"));
    }
}
