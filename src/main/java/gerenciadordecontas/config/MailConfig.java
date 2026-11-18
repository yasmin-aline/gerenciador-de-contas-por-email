package gerenciadordecontas.config;

/** Caixa de e-mail do bot e o seu endereço (destinatário e único remetente aceito). */
public record MailConfig(String emailDoBot, String senhaDeApp, String destinatario) {

    public static MailConfig deAmbiente() {
        return new MailConfig(
                Env.obrigatoria("BOT_EMAIL", "o Gmail criado só para o projeto"),
                Env.obrigatoria("BOT_EMAIL_APP_PASSWORD", "a senha de app do Gmail do bot").replace(" ", ""),
                Env.obrigatoria("DESTINATARIO_EMAIL", "o seu e-mail, para onde vão os avisos"));
    }
}
