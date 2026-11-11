package com.inversioneslr.marketplace.compartido;

import java.util.Map;

/** Regla de negocio incumplida. La presentación traduce el tipo a un estado HTTP. */
public class ExcepcionNegocio extends RuntimeException {

    public enum Tipo {
        DATOS_INVALIDOS, CREDENCIALES_INVALIDAS, CUENTA_BLOQUEADA, CUENTA_RESTRINGIDA, CUENTA_DESACTIVADA, ACCESO_DENEGADO, NO_ENCONTRADO,
        CONFLICTO, PAGO_RECHAZADO, PAGO_NO_PROCESADO, INFORMACION_INCOMPLETA, CORREO_NO_VERIFICADO
    }

    private final Tipo tipo;
    private final Map<String, String> campos;

    public ExcepcionNegocio(Tipo tipo, String mensaje) {
        this(tipo, mensaje, Map.of());
    }

    /** @param campos mensaje por campo afectado, con la causa y cómo corregirla (RNF-35) */
    public ExcepcionNegocio(Tipo tipo, String mensaje, Map<String, String> campos) {
        super(mensaje);
        this.tipo = tipo;
        this.campos = campos;
    }

    public Tipo tipo() {
        return tipo;
    }

    public Map<String, String> campos() {
        return campos;
    }
}
