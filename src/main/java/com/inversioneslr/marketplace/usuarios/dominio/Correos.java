package com.inversioneslr.marketplace.usuarios.dominio;

/** Envío de correos. Es un componente no crítico (ASR-05): si falla, el caso de uso no se detiene. */
public interface Correos {

    /** Lo envía en segundo plano cuando termina bien la transacción en curso, o de inmediato si no hay una. */
    void enviar(String destinatario, String asunto, String texto);
}
