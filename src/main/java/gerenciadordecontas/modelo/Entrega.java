package gerenciadordecontas.modelo;

/** O que a pessoa pediu para receber por e-mail. */
public enum Entrega {
    /** O endereço da planilha. */
    LINK,
    /** Resumo do mês em texto, no corpo do e-mail. */
    RESUMO,
    /** A planilha inteira, em anexo (.xlsx). */
    ARQUIVO
}
