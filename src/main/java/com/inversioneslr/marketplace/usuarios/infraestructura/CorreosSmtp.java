package com.inversioneslr.marketplace.usuarios.infraestructura;

import com.inversioneslr.marketplace.usuarios.dominio.Correos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * SMTP con Spring Mail: sirve con Gmail, Amazon SES o Mailpit en desarrollo (README, "Correos"). Se envía después del
 * commit y en otro hilo, así que el servidor de correo no frena ni tumba el registro (ASR-05).
 */
@Component
public class CorreosSmtp implements Correos {

    private static final Logger log = LoggerFactory.getLogger(CorreosSmtp.class);

    private final JavaMailSender servidor;
    private final TaskExecutor tareas;
    private final String remitente;

    public CorreosSmtp(JavaMailSender servidor, @Qualifier("applicationTaskExecutor") TaskExecutor tareas,
                       @Value("${marketplace.correo.remitente}") String remitente) {
        this.servidor = servidor;
        this.tareas = tareas;
        this.remitente = remitente;
    }

    @Override
    public void enviar(String destinatario, String asunto, String texto) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(destinatario);
        mensaje.setSubject(asunto);
        mensaje.setText(texto);
        Runnable envio = () -> enviarAhora(mensaje);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tareas.execute(envio);
                }
            });
        } else {
            tareas.execute(envio);
        }
    }

    private void enviarAhora(SimpleMailMessage mensaje) {
        try {
            servidor.send(mensaje);
        } catch (MailException e) {
            // Sin destinatario ni texto: el texto lleva un token de un solo uso.
            log.atWarn().addKeyValue("asunto", mensaje.getSubject()).log("No se pudo enviar un correo: {}", e.getClass().getSimpleName());
        }
    }
}
