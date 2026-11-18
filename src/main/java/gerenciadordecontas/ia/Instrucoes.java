package gerenciadordecontas.ia;

/** Texto e formato que valem para qualquer IA usada: o que ela deve extrair da mensagem. */
final class Instrucoes {

    private Instrucoes() {
    }

    static final String SISTEMA = """
            Você lê mensagens curtas, em português do Brasil, de uma pessoa que controla as próprias contas a pagar.
            Responda SEMPRE no formato pedido, com uma ação para cada coisa que a pessoa pediu.

            Tipos de ação:
            - pagar: a pessoa diz que pagou uma conta. "conta" é o nome como ela escreveu. "valor" é o que ela diz que pagou, se disser.
            - cadastrar: ela quer incluir uma conta nova ("nova conta: IPTU, 400"). "valor" é obrigatório para cadastrar.
            - desfazer: ela quer reverter a última mudança.
            - enviar: ela pede que você mande algo para ela ("me manda a planilha", "manda o resumo de novembro",
              "qual o link?"). Preencha "enviar_o_que": "arquivo" para planilha, arquivo, excel ou anexo;
              "link" para link, endereço ou url; "resumo" para resumo, situação, "como estão as contas" ou
              "o que falta pagar". Se ela pedir "a planilha" sem dizer mais nada, use "arquivo".
              Em "mes" ponha o número do mês pedido (1 a 12); omita se ela não disse.
            - desconhecido: qualquer outra coisa, ou se não der para entender.

            Regras:
            - Valores em reais. "1.234,56" é 1234.56 e "130" é 130. Na saída use número com ponto decimal.
            - "bloco" só vale quando a pessoa escreve explicitamente "dia 15" ou "fim do mês" como o grupo da conta. Se não disser, use "nenhum". Nunca deduza o bloco de "vence dia 20".
            - "dia_vencimento" é o dia do mês que ela disser que a conta vence, se disser.
            - "confianca" vai de 0 a 1: use menos de 0,7 se o nome da conta ou o valor estiverem ambíguos.
            - Não invente contas nem valores que a pessoa não disse.
            """;

    static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "acoes": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "tipo": { "type": "string", "enum": ["pagar", "cadastrar", "desfazer", "enviar", "desconhecido"] },
                      "conta": { "type": "string", "description": "Nome da conta como a pessoa escreveu. Vazio para desfazer e enviar." },
                      "valor": { "type": "number", "description": "Valor em reais. Omita se a pessoa não disse." },
                      "bloco": { "type": "string", "enum": ["dia15", "fim_mes", "nenhum"] },
                      "dia_vencimento": { "type": "integer", "description": "Dia do mês do vencimento. Omita se não foi dito." },
                      "enviar_o_que": { "type": "string", "enum": ["link", "resumo", "arquivo", "nenhum"], "description": "Só para enviar." },
                      "mes": { "type": "integer", "description": "Mês pedido (1 a 12), só para enviar um resumo. Omita se não foi dito." },
                      "confianca": { "type": "number", "description": "De 0 a 1." }
                    },
                    "required": ["tipo", "conta", "bloco", "confianca"]
                  }
                }
              },
              "required": ["acoes"]
            }
            """;

}
