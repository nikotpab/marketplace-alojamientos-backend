package com.inversioneslr.marketplace.compartido;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hecho del dominio (ADR-06). Cada evento es un record en la raíz del módulo que lo emite (SCRUM-143); la auditoría y
 * las notificaciones atienden esta interfaz, así que no dependen de ningún evento concreto.
 *
 * <p>Estos datos comunes se guardan también en el JSON de event_publication, junto a los campos del record, para poder
 * consultar las entregas de cualquier evento con las mismas claves. Al leerlo de vuelta se ignoran, porque salen del record.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public interface EventoDominio {

    /** Resultado de un caso de uso que terminó bien. */
    String EXITO = "EXITO";

    /** Identifica el evento, para que entregarlo otra vez no repita sus efectos (RNF-14). */
    UUID id();

    /** El nombre del record, por ejemplo "ReservaConfirmada". Es la acción que queda en la auditoría. */
    @JsonProperty
    default String tipo() {
        return getClass().getSimpleName();
    }

    /** Quién lo provocó; null si fue el sistema. */
    @JsonProperty
    UUID actorId();

    @JsonProperty
    String entidad();

    @JsonProperty
    UUID entidadId();

    @JsonProperty
    default String resultado() {
        return EXITO;
    }

    @JsonProperty
    default Map<String, Object> detalle() {
        return Map.of();
    }

    /** Usuarios a notificar. */
    @JsonProperty
    default List<UUID> destinatarios() {
        return List.of();
    }
}
