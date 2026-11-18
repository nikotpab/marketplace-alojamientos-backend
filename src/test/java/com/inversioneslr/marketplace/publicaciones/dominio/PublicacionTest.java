package com.inversioneslr.marketplace.publicaciones.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inversioneslr.marketplace.compartido.ExcepcionNegocio;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicacionTest {

    @Test
    void unBorradorCompletoSolicitaActivacion() {
        Publicacion pendiente = completa(EstadoPublicacion.BORRADOR).solicitarActivacion();

        assertThat(pendiente.estado()).isEqualTo(EstadoPublicacion.PENDIENTE_REVISION);
        assertThat(pendiente.motivoEstado()).isNull();
    }

    @Test
    void noSeEnviaARevisionSiNoEsBorrador() {
        assertThatThrownBy(() -> completa(EstadoPublicacion.ACTIVA).solicitarActivacion())
                .isInstanceOfSatisfying(ExcepcionNegocio.class,
                        e -> assertThat(e.tipo()).isEqualTo(ExcepcionNegocio.Tipo.CONFLICTO));
    }

    @Test
    void unBorradorIncompletoIndicaLosCamposFaltantes() {
        Publicacion incompleto = new Publicacion(UUID.randomUUID(), UUID.randomUUID(),
                null, null, "", null, null, null, null, null,
                List.of(), List.of(), List.of(), EstadoPublicacion.BORRADOR, null);

        assertThatThrownBy(incompleto::solicitarActivacion)
                .isInstanceOfSatisfying(ExcepcionNegocio.class, e -> {
                    assertThat(e.tipo()).isEqualTo(ExcepcionNegocio.Tipo.INFORMACION_INCOMPLETA);
                    assertThat(e.campos()).containsKeys(
                            "titulo", "descripcion", "ubicacion", "tipo",
                            "capacidad", "precioNoche", "imagenes", "politicaCancelacion");
                });
    }

    @Test
    void laRevisionAprueba() {
        assertThat(completa(EstadoPublicacion.PENDIENTE_REVISION).aprobar().estado())
                .isEqualTo(EstadoPublicacion.ACTIVA);
    }

    @Test
    void laRevisionRechazaConMotivo() {
        Publicacion borrador = completa(EstadoPublicacion.PENDIENTE_REVISION).rechazar("Fotos incorrectas");

        assertThat(borrador.estado()).isEqualTo(EstadoPublicacion.BORRADOR);
        assertThat(borrador.motivoEstado()).isEqualTo("Fotos incorrectas");
    }

    @Test
    void noSePuedeRechazarSinMotivo() {
        assertThatThrownBy(() -> completa(EstadoPublicacion.PENDIENTE_REVISION).rechazar(""))
                .isInstanceOfSatisfying(ExcepcionNegocio.class,
                        e -> assertThat(e.tipo()).isEqualTo(ExcepcionNegocio.Tipo.DATOS_INVALIDOS));
    }

    @Test
    void unaActivaSePuedePausar() {
        assertThat(completa(EstadoPublicacion.ACTIVA).pausar().estado())
                .isEqualTo(EstadoPublicacion.PAUSADA);
    }

    @Test
    void noSePuedePausarSiNoEstaActiva() {
        assertThatThrownBy(() -> completa(EstadoPublicacion.PAUSADA).pausar())
                .isInstanceOfSatisfying(ExcepcionNegocio.class,
                        e -> assertThat(e.tipo()).isEqualTo(ExcepcionNegocio.Tipo.CONFLICTO));
    }

    @Test
    void unaPausadaSeReactiva() {
        assertThat(completa(EstadoPublicacion.PAUSADA).reactivar().estado())
                .isEqualTo(EstadoPublicacion.ACTIVA);
    }

    @Test
    void unaBloqueadaNoLaPuedeReactivarElAnfitrion() {
        assertThatThrownBy(() -> completa(EstadoPublicacion.BLOQUEADA).reactivar())
                .isInstanceOfSatisfying(ExcepcionNegocio.class,
                        e -> assertThat(e.tipo()).isEqualTo(ExcepcionNegocio.Tipo.CONFLICTO));
    }

    @Test
    void seBloqueanActivasYPausadasConMotivo() {
        assertThat(completa(EstadoPublicacion.ACTIVA).bloquear("Infracción").estado())
                .isEqualTo(EstadoPublicacion.BLOQUEADA);
        assertThat(completa(EstadoPublicacion.PAUSADA).bloquear("Infracción").estado())
                .isEqualTo(EstadoPublicacion.BLOQUEADA);
    }

    @Test
    void noSePuedeBloquearUnBorrador() {
        assertThatThrownBy(() -> completa(EstadoPublicacion.BORRADOR).bloquear("Motivo"))
                .isInstanceOfSatisfying(ExcepcionNegocio.class,
                        e -> assertThat(e.tipo()).isEqualTo(ExcepcionNegocio.Tipo.CONFLICTO));
    }

    private static Publicacion completa(EstadoPublicacion estado) {
        return new Publicacion(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                "Casa con patio", "Amplia y cómoda", "Cartagena", "Centro histórico, Cartagena",
                4, new BigDecimal("200000"),
                List.of(), List.of(), List.of("foto.jpg"), estado, null);
    }
}
