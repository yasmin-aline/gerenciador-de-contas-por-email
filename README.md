# Gerenciador de Contas por E-mail

Programa em Java 17 (Maven) que lê a planilha de contas no Google Sheets, avisa por e-mail o que vence e o saldo previsto, e entende respostas em texto livre ("paguei a luz, 210") para dar baixa, cadastrar conta nova e desfazer. Roda no seu PC, pelo Agendador de Tarefas.

## Como funciona

1. O Agendador chama `java -jar gerenciadordecontas.jar tick` ao ligar o PC e às 08:00, 13:00 e 20:00.
2. O `tick` faz duas coisas:
   - **Avisos:** 3 dias e 1 dia antes do dia 15 e do fim do mês. Se o PC estava desligado no dia certo, o aviso sai no primeiro `tick` depois, uma vez só.
   - **Respostas:** lê no Gmail do bot as mensagens novas **do seu endereço**, a IA extrai conta, valor e ação, o programa casa o nome com a planilha, grava e responde com o que mudou.
3. Cada mudança vai para a aba `_log` da planilha (é daí que sai o "desfazer"). O que já foi feito (e-mails processados, avisos enviados, confirmações pendentes) fica na aba `_estado`. As duas abas são criadas sozinhas e não mexem nas somas do Resumo.

### O que você pode escrever no e-mail de resposta

| Você escreve | O que acontece |
|---|---|
| `paguei a internet, deu 130, e a luz, 210` | Marca as duas como Pago e troca o valor pelo que você pagou |
| `nova conta: IPTU, 400, fim do mês` | Cadastra numa vaga livre do bloco, como Pendente |
| `nova conta: IPTU, 400` | O bot pergunta o bloco; responda `dia 15` ou `fim do mês` |
| `desfazer` | Reverte a última mudança |
| `sim` / `não` | Responde a uma confirmação pedida pelo bot |
| `me manda a planilha` | Manda um e-mail separado com a planilha inteira em anexo (.xlsx) |
| `qual o link da planilha?` | Responde com o endereço da planilha |
| `resumo do mês` / `resumo de outubro` | Responde com o que já foi pago, o que falta e o saldo previsto |

Pedidos de envio não alteram nada na planilha e podem vir junto com outras ações ("paguei a luz, 210, e me manda o resumo"). Resumo e link já saem na própria resposta; o anexo vai num e-mail à parte.

O bot **pede confirmação antes de gravar** quando a IA tem pouca certeza ou quando o valor pago foge mais de 30% do previsto. Se o bloco estiver sem vaga, avisa e não insere linha (para não quebrar as somas do Resumo). Se o nome for ambíguo ("cartão" com dois cartões), pergunta em vez de chutar.

## Estrutura

```
src/main/java/gerenciadordecontas/
  modelo/     Conta, Bloco, Status, Acao (registros e enums simples)
  planilha/   leitura/gravação do Google Sheets (SheetsGateway + implementação real)
  nomes/      como "luz" vira "Conta de Luz" (matcher, apelidos)
  aviso/      quando avisar (AvisoPolicy) e os textos: aviso (AvisoComposer) e resumo do mês (ResumoComposer)
  email/      IMAP/SMTP (Caixa + JakartaCaixa) e corte de texto citado
  ia/         chamada à API do Gemini (ou da Anthropic): só extrai conta, valor e ação
  estado/     abas _log (desfazer) e _estado (o que já foi feito)
  fluxo/      ProcessadorDeRespostas, Orquestrador (o que o "tick" faz) e EntregaPorEmail (link e anexo)
  config/     .env, variáveis de ambiente e valores padrão
  Cli, Main   comandos: tick, aviso, processar, diagnostico, abas, ler-celula
src/test/     testes JUnit 5 com planilha, e-mail e IA falsos (rodam sem rede)
scripts/      instalar-tarefa.ps1 (agenda) e processar-agora.cmd (atalho)
apelidos.json "energia" -> "luz" etc. (copie junto com o jar)
```

## Configuração (uma vez)

### 1. Planilha no Google Sheets
1. Envie o `.xlsx` ao Drive, abra e use **Arquivo > Salvar como Planilha Google**.
2. Em **Arquivo > Configurações**, ponha a localidade em **Brasil** (o programa lê "R$ 1.234,56").
3. Confira se o **Resumo** dá os mesmos totais do Excel.
4. A coluna **Pago?** é uma lista com as opções Pago, Pendente e Não pago. O programa grava os textos exatamente como estão definidos em `Status.java` (`PAGO` ao dar baixa, `PENDENTE` em conta nova) e também lê "Pago" e "Pendente" sem nenhum ícone na frente. Se o texto das opções da sua lista for diferente, ajuste `Status.java`.
5. Copie o ID da planilha: o trecho da URL entre `/d/` e `/edit`.

### 2. Google Cloud (Sheets API e, para o anexo, Drive API)
1. Crie um projeto em console.cloud.google.com e ative a **Google Sheets API**. Para o bot poder mandar a planilha em anexo, ative também a **Google Drive API** (o programa só pede permissão de leitura do Drive, e só para baixar a planilha compartilhada com ele). Sem a Drive API tudo funciona, menos o anexo: o bot avisa e manda o link.
2. Em **IAM e administrador > Contas de serviço**, crie uma conta e gere uma chave **JSON**. Salve em `secrets/service-account.json`.
3. Compartilhe a planilha, como **Editor**, com o e-mail da conta de serviço (termina em `.iam.gserviceaccount.com`).

A Gmail API **não** é necessária: o e-mail usa IMAP/SMTP com senha de app, que não expira como o token OAuth em modo de teste.

### 3. Gmail do bot
1. Crie um Gmail só para o projeto.
2. Ative a verificação em duas etapas e crie uma **senha de app** (myaccount.google.com/apppasswords).
3. Se o Gmail pedir, ative o acesso IMAP nas configurações.

### 4. Chave da IA (Gemini)
1. Em aistudio.google.com/apikey, crie uma chave nova só para este projeto.
2. Ponha em `GEMINI_API_KEY` no `.env`. Em `GEMINI_MODEL`, use um modelo que a sua chave consiga usar; o padrão é `gemini-flash-latest`.
Se o Gemini responder 503 ("alta demanda") ou 429, o programa tenta de novo 3 vezes, esperando 3 s e depois 6 s. Se continuar, usa o modelo em `GEMINI_MODEL_FALLBACK`, caso você tenha configurado um.
3. Para escolher o modelo mais rápido da sua chave, rode `java -jar target/gerenciadordecontas.jar ia-modelos`: ele lista os modelos que a chave pode usar, mede o tempo de cada um e sugere o que pôr em `GEMINI_MODEL`.
4. Teste sem mexer na planilha: `java -jar target/gerenciadordecontas.jar ia "paguei a luz, 210"`. Ele mostra o que a IA entendeu.

Atenção: na camada gratuita do Gemini, o Google pode usar o conteúdo enviado para melhorar os produtos dele. Aqui vão só as respostas ("paguei a luz, 210") e os nomes das contas do mês, nunca a planilha inteira. Se isso incomodar, ative o faturamento no projeto do Google ou use `ANTHROPIC_API_KEY` no lugar (pago, centavos por mês).

Sem nenhuma chave, os avisos funcionam, mas as respostas em texto livre não.

### 5. `.env`
Copie `.env.example` para `.env` e preencha. O `.env` e a pasta `secrets/` estão no `.gitignore`.

## Rodar e conferir

Precisa de JDK 17+ e Maven. Na raiz do projeto:

```powershell
mvn test
mvn package
java -jar target/gerenciadordecontas.jar abas
java -jar target/gerenciadordecontas.jar ia-modelos
java -jar target/gerenciadordecontas.jar ia "paguei a luz, 210"
java -jar target/gerenciadordecontas.jar diagnostico Nov
java -jar target/gerenciadordecontas.jar aviso
java -jar target/gerenciadordecontas.jar tick
```

- `diagnostico Nov` mostra o que o programa leu do mês. **Confira com a planilha antes de usar.**
- `aviso` manda agora o aviso do próximo vencimento para o seu e-mail.
- `tick` é o que o agendador roda.

## Instalar no Agendador de Tarefas

```powershell
mvn package
mkdir C:\GerenciadorDeContas
copy target\gerenciadordecontas.jar C:\GerenciadorDeContas\
copy .env C:\GerenciadorDeContas\.env
copy apelidos.json C:\GerenciadorDeContas\
copy -Recurse secrets C:\GerenciadorDeContas\secrets
.\scripts\instalar-tarefa.ps1
```

O agendador usa `javaw.exe` (sem janela) com a pasta `C:\GerenciadorDeContas` como pasta de trabalho; o log fica em `C:\GerenciadorDeContas\gerenciadordecontas.log`. A tarefa roda ao entrar no Windows e às 08:00, 13:00 e 20:00, e recupera execuções perdidas com o PC desligado. Para processar uma resposta na hora, use o atalho `scripts\processar-agora.cmd` (crie um atalho dele no desktop).

## Suposições sobre a sua planilha (confira)

- Abas de mês `Jan` a `Dez`; blocos nas colunas A-C (Dia 15) e E-G (Fim do mês).
- Vagas nas linhas 5-9 (Jan a Abr), 4-7 (Mai) e 4-8 (Jun a Dez). O programa acha cada conta **pelo nome** dentro dessas linhas e nunca lê os totais e as anotações abaixo. Se o seu layout tiver outra faixa, ajuste `MesLayout.vagas`.
- **Saldo previsto** = entradas (o que entra no dia 15 + o que entra no fim do mês, definidos em `RECEBE_DIA15` e `RECEBE_FIM_MES` no `.env`) − soma de todas as contas do mês. Compare com o Resumo da planilha; se a fórmula for outra, é só ajustar em `AvisoComposer` (`aviso/`).
- A planilha **não tem coluna de vencimento**, então "vence dia 20" é entendido mas não gravado: o vencimento vem do bloco (dia 15 ou fim do mês).
- Apelidos ("energia" para "Conta de Luz") ficam em `apelidos.json`.

## Segurança

- O bot só obedece ao e-mail em `DESTINATARIO_EMAIL`; mensagens de outros remetentes são ignoradas.
- Chaves ficam só no `.env` e em `secrets/`, nunca no código. Não suba esses arquivos para o repositório (o `.gitignore` já os exclui).
- O texto das suas respostas vai para a API de IA (Gemini) só para extrair conta, valor e ação.

## Observação sobre as dependências

As versões das bibliotecas estão no `pom.xml`. Se o Maven reclamar de alguma (principalmente `google-api-services-sheets`, hoje em `v4-rev20230815-2.0.0`), troque pela versão mais recente listada em search.maven.org e rode `mvn test` de novo.
