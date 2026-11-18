package gerenciadordecontas.email;

import gerenciadordecontas.config.MailConfig;
import jakarta.activation.DataHandler;
import jakarta.mail.Authenticator;
import jakarta.mail.BodyPart;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.SearchTerm;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * Caixa de e-mail do bot via IMAP (ler) e SMTP (enviar), com senha de app do Gmail.
 * Cada operação abre e fecha a própria conexão: o programa roda poucas vezes por dia.
 */
public final class JakartaCaixa implements Caixa {

    private static final String HOST_IMAP = "imap.gmail.com";
    private static final String HOST_SMTP = "smtp.gmail.com";

    private final MailConfig config;

    public JakartaCaixa(MailConfig config) {
        this.config = config;
    }

    @Override
    public List<EmailRecebido> buscarNaoLidosDe(String remetente) {
        try (ConexaoImap conexao = abrirInbox(Folder.READ_ONLY)) {
            SearchTerm termo = new AndTerm(
                    new FlagTerm(new Flags(Flags.Flag.SEEN), false),
                    new FromStringTerm(remetente));
            UIDFolder porUid = (UIDFolder) conexao.inbox;

            List<EmailRecebido> emails = new ArrayList<>();
            for (Message mensagem : conexao.inbox.search(termo)) {
                MimeMessage mime = (MimeMessage) mensagem;
                emails.add(new EmailRecebido(
                        mime.getMessageID() != null ? mime.getMessageID() : "uid-" + porUid.getUID(mime),
                        mime.getSubject() != null ? mime.getSubject() : "",
                        textoDe(mime),
                        remetenteDe(mime),
                        dataDe(mime),
                        String.valueOf(porUid.getUID(mime))));
            }
            return emails;
        } catch (MessagingException e) {
            throw new IllegalStateException("Falha ao ler o e-mail: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o e-mail: " + e.getMessage(), e);
        }
    }

    @Override
    public void marcarComoLido(String handle) {
        try (ConexaoImap conexao = abrirInbox(Folder.READ_WRITE)) {
            Message mensagem = ((UIDFolder) conexao.inbox).getMessageByUID(Long.parseLong(handle));
            if (mensagem != null) {
                mensagem.setFlag(Flags.Flag.SEEN, true);
            }
        } catch (MessagingException e) {
            throw new IllegalStateException("Falha ao marcar o e-mail como lido: " + e.getMessage(), e);
        }
    }

    @Override
    public void enviar(String para, String assunto, String corpo) {
        try {
            MimeMessage mensagem = novaMensagem(para, assunto);
            mensagem.setText(corpo, "UTF-8");
            Transport.send(mensagem);
        } catch (MessagingException e) {
            throw new IllegalStateException("Falha ao enviar o e-mail: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao enviar o e-mail: " + e.getMessage(), e);
        }
    }

    @Override
    public void enviarComAnexo(String para, String assunto, String corpo, String nomeArquivo, String tipoMime,
            byte[] conteudo) {
        try {
            MimeMessage mensagem = novaMensagem(para, assunto);

            MimeBodyPart texto = new MimeBodyPart();
            texto.setText(corpo, "UTF-8");

            MimeBodyPart anexo = new MimeBodyPart();
            anexo.setDataHandler(new DataHandler(new ByteArrayDataSource(conteudo, tipoMime)));
            anexo.setFileName(nomeArquivo);

            MimeMultipart partes = new MimeMultipart("mixed");
            partes.addBodyPart(texto);
            partes.addBodyPart(anexo);
            mensagem.setContent(partes);
            Transport.send(mensagem);
        } catch (MessagingException e) {
            throw new IllegalStateException("Falha ao enviar o e-mail com anexo: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao enviar o e-mail com anexo: " + e.getMessage(), e);
        }
    }

    private MimeMessage novaMensagem(String para, String assunto) throws MessagingException, IOException {
        Properties props = new Properties();
        props.put("mail.smtp.host", HOST_SMTP);
        props.put("mail.smtp.port", "465");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "20000");
        props.put("mail.smtp.timeout", "60000");

        Session sessao = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(config.emailDoBot(), config.senhaDeApp());
            }
        });

        MimeMessage mensagem = new MimeMessage(sessao);
        mensagem.setFrom(new InternetAddress(config.emailDoBot(), "Contas Bot", "UTF-8"));
        mensagem.setRecipients(Message.RecipientType.TO, InternetAddress.parse(para));
        mensagem.setSubject(assunto, "UTF-8");
        return mensagem;
    }

    /** Conexão IMAP com a caixa de entrada aberta; fecha tudo no fim do try-with-resources. */
    private ConexaoImap abrirInbox(int modo) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "20000");
        props.put("mail.imaps.timeout", "30000");
        // peek: ler o conteúdo não marca a mensagem como lida; quem marca é o programa, depois de processar.
        props.put("mail.imaps.peek", "true");

        Store loja = Session.getInstance(props).getStore("imaps");
        loja.connect(HOST_IMAP, 993, config.emailDoBot(), config.senhaDeApp());
        Folder inbox = loja.getFolder("INBOX");
        inbox.open(modo);
        return new ConexaoImap(loja, inbox);
    }

    private static String remetenteDe(MimeMessage mensagem) throws MessagingException {
        jakarta.mail.Address[] de = mensagem.getFrom();
        if (de != null && de.length > 0 && de[0] instanceof InternetAddress endereco) {
            return endereco.getAddress() != null ? endereco.getAddress() : "";
        }
        return "";
    }

    private static Instant dataDe(MimeMessage mensagem) throws MessagingException {
        Date data = mensagem.getReceivedDate() != null ? mensagem.getReceivedDate() : mensagem.getSentDate();
        return data != null ? data.toInstant() : Instant.EPOCH;
    }

    private static String textoDe(Part parte) throws MessagingException, IOException {
        if (parte.isMimeType("text/plain")) {
            return String.valueOf(parte.getContent());
        }
        if (parte.isMimeType("multipart/*")) {
            Multipart partes = (Multipart) parte.getContent();
            String html = null;
            for (int i = 0; i < partes.getCount(); i++) {
                BodyPart item = partes.getBodyPart(i);
                if (item.isMimeType("text/plain")) {
                    return String.valueOf(item.getContent());
                }
                if (item.isMimeType("multipart/*")) {
                    String interno = textoDe(item);
                    if (!interno.isBlank()) {
                        return interno;
                    }
                }
                if (item.isMimeType("text/html") && html == null) {
                    html = String.valueOf(item.getContent());
                }
            }
            return html == null ? "" : tirarHtml(html);
        }
        if (parte.isMimeType("text/html")) {
            return tirarHtml(String.valueOf(parte.getContent()));
        }
        return "";
    }

    private static String tirarHtml(String html) {
        return html.replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private static final class ConexaoImap implements AutoCloseable {
        private final Store loja;
        private final Folder inbox;

        ConexaoImap(Store loja, Folder inbox) {
            this.loja = loja;
            this.inbox = inbox;
        }

        @Override
        public void close() throws MessagingException {
            try {
                if (inbox.isOpen()) {
                    inbox.close(false);
                }
            } finally {
                loja.close();
            }
        }
    }
}
