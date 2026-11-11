package com.inversioneslr.marketplace.usuarios.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** SCRUM-109 · ASR-05: el correo sale después del commit y una falla no llega al caso de uso. */
class CorreosSmtpTest {

    private final JavaMailSender servidor = mock(JavaMailSender.class);
    private final CorreosSmtp correos = new CorreosSmtp(servidor, Runnable::run, "Marketplace <no-responder@prueba.co>");

    @AfterEach
    void limpiar() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sinTransaccionSeEnviaDeInmediatoConElRemitenteConfigurado() {
        correos.enviar("laura@prueba.co", "Asunto", "Texto");

        ArgumentCaptor<SimpleMailMessage> mensaje = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(servidor).send(mensaje.capture());
        assertThat(mensaje.getValue().getFrom()).isEqualTo("Marketplace <no-responder@prueba.co>");
        assertThat(mensaje.getValue().getTo()).containsExactly("laura@prueba.co");
    }

    @Test
    void dentroDeUnaTransaccionEsperaAlCommit() {
        TransactionSynchronizationManager.initSynchronization();

        correos.enviar("laura@prueba.co", "Asunto", "Texto");
        verify(servidor, never()).send(any(SimpleMailMessage.class));

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(servidor).send(any(SimpleMailMessage.class));
    }

    @Test
    void siLaTransaccionSeDeshaceNoSeEnvia() {
        TransactionSynchronizationManager.initSynchronization();

        correos.enviar("laura@prueba.co", "Asunto", "Texto");
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sincronizacion -> sincronizacion.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(servidor, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void unaFallaDelServidorDeCorreoNoSePropaga() {
        doThrow(new MailSendException("Servidor caído")).when(servidor).send(any(SimpleMailMessage.class));

        assertThatNoException().isThrownBy(() -> correos.enviar("laura@prueba.co", "Asunto", "Texto"));
    }
}
