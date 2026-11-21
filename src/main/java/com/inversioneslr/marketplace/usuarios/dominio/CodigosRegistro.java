package com.inversioneslr.marketplace.usuarios.dominio;

import java.time.Instant;
import java.util.Optional;

/**
 * El código de 6 dígitos que verifica el correo antes de crear la cuenta. Hay uno vigente por correo y solo
 * se guarda su hash.
 */
public interface CodigosRegistro {

    /** Reemplaza el código vigente de ese correo. */
    void guardar(String correo, String hashCodigo, Instant expira);

    /** Cuándo se envió el último código a ese correo, para no enviar correos seguidos. */
    Optional<Instant> ultimoEnvio(String correo);

    /**
     * Gasta un intento del código vigente antes de comprobarlo, así que ni con solicitudes simultáneas se prueban más
     * códigos que el máximo. Vacío si no hay código, si venció o si ya gastó todos sus intentos.
     */
    Optional<Intento> gastarIntento(String correo, int maximoIntentos, Instant ahora);

    /** Lo borra si sigue siendo ese código. Falso si otra solicitud lo usó o lo reemplazó antes. */
    boolean usar(String correo, String hashCodigo);

    /** @param numero cuántos intentos lleva contando este */
    record Intento(String hashCodigo, int numero) {
    }
}
