package gerenciadordecontas.email;

import java.util.List;

/** Caixa de e-mail do bot: lê as respostas e envia avisos e confirmações. */
public interface Caixa {

    List<EmailRecebido> buscarNaoLidosDe(String remetente);

    void marcarComoLido(String handle);

    void enviar(String para, String assunto, String corpo);

    /** Envia um e-mail com um arquivo em anexo. */
    void enviarComAnexo(String para, String assunto, String corpo, String nomeArquivo, String tipoMime,
            byte[] conteudo);
}
