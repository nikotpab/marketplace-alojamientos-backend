package com.inversioneslr.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inversioneslr.marketplace.administracion.dominio.FiltrosReporte;
import com.inversioneslr.marketplace.administracion.dominio.Reporte;
import com.inversioneslr.marketplace.administracion.dominio.RepositorioReportes;
import com.inversioneslr.marketplace.administracion.dominio.TipoReporte;
import com.inversioneslr.marketplace.compartido.Rol;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** CU-30 · RF-34. Como en DashboardIT, las reservas se hicieron en un mes que no usa ninguna otra prueba. */
class ReportesIT extends PruebaIntegracion {

    private static final String CIUDAD = "Ciudad de reporte";

    @Autowired
    RepositorioReportes reportes;

    @Test
    void elReportePorCiudadSumaLasReservasConfirmadasYCompletadasDelPeriodo() {
        UUID publicacion = publicacionEnLaCiudad();
        UUID huesped = crearUsuario(Rol.HUESPED);
        reservar(huesped, publicacion, "2030-01-01", "CONFIRMADA", 30000, "2002-07-05");
        reservar(huesped, publicacion, "2030-02-01", "COMPLETADA", 20000, "2002-07-20");
        reservar(huesped, publicacion, "2030-03-01", "CANCELADA", 90000, "2002-07-21");
        reservar(huesped, publicacion, "2030-04-01", "CONFIRMADA", 70000, "2002-08-01");

        Reporte reporte = reportes.consultar(TipoReporte.INGRESOS_DEMANDA,
                new FiltrosReporte(LocalDate.of(2002, 7, 1), LocalDate.of(2002, 7, 31), "ciudad de REPORTE"));

        assertThat(reporte.columnas()).containsExactly("Ciudad", "Reservas", "Ingresos estimados", "Total reservado");
        assertThat(reporte.filas()).containsExactly(List.of(CIUDAD, "2", "50000.00", "500000.00"));
    }

    @Test
    void exportaLasReservasEnPdf() throws Exception {
        String sesion = sesionDe(crearUsuario(Rol.ADMINISTRADOR), Rol.ADMINISTRADOR);

        byte[] pdf = exportar(sesion, "RESERVAS", "PDF")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"reporte-reservas.pdf\""))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void exportaLasPublicacionesEnExcelConSusDatos() throws Exception {
        UUID publicacion = publicacionEnLaCiudad();
        comoAplicacion.update("UPDATE publicacion SET titulo = 'Casa para el reporte' WHERE id = ?", publicacion);
        UUID administrador = crearUsuario(Rol.ADMINISTRADOR);

        byte[] excel = exportar(sesionDe(administrador, Rol.ADMINISTRADOR), "PUBLICACIONES", "EXCEL", "ciudad", CIUDAD)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(textoDelExcel(excel)).contains("Casa para el reporte", "Precio por noche");
        assertThat(comoAplicacion.queryForObject("""
                SELECT detalle ->> 'tipo' FROM auditoria.evento_auditoria WHERE actor_id = ? AND accion = 'ReporteExportado'""",
                String.class, administrador)).isEqualTo("PUBLICACIONES");
    }

    @Test
    void soloElAdministradorExporta() throws Exception {
        mvc.perform(get("/api/v1/admin/reportes").param("tipo", "USUARIOS").param("formato", "PDF")
                        .header("Authorization", sesionDe(crearUsuario(Rol.ANFITRION), Rol.ANFITRION)))
                .andExpect(status().isForbidden());
    }

    /** @param filtros pares nombre y valor */
    private ResultActions exportar(String sesion, String tipo, String formato, String... filtros) throws Exception {
        var solicitud = get("/api/v1/admin/reportes").param("tipo", tipo).param("formato", formato).header("Authorization", sesion);
        for (int i = 0; i < filtros.length; i += 2) {
            solicitud.param(filtros[i], filtros[i + 1]);
        }
        MvcResult enCurso = mvc.perform(solicitud).andExpect(request().asyncStarted()).andReturn();
        return mvc.perform(asyncDispatch(enCurso));
    }

    /** Un .xlsx es un zip de XML: basta con leer sus textos. ZipFile lo lee por el directorio central, como Excel. */
    private static String textoDelExcel(byte[] excel) throws IOException {
        Path archivo = Files.createTempFile("reporte", ".xlsx");
        try {
            Files.write(archivo, excel);
            StringBuilder texto = new StringBuilder();
            try (ZipFile zip = new ZipFile(archivo.toFile())) {
                for (ZipEntry entrada : Collections.list(zip.entries())) {
                    texto.append(new String(zip.getInputStream(entrada).readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            return texto.toString();
        } finally {
            Files.delete(archivo);
        }
    }

    private UUID publicacionEnLaCiudad() {
        UUID publicacion = crearPublicacionActiva(crearUsuario(Rol.ANFITRION));
        comoAplicacion.update("UPDATE publicacion SET ciudad = ? WHERE id = ?", CIUDAD, publicacion);
        return publicacion;
    }

    private void reservar(UUID huesped, UUID publicacion, String llegada, String estado, int tarifaServicio, String hechaEl) {
        comoAplicacion.update("""
                INSERT INTO reserva (huesped_id, publicacion_id, fecha_llegada, fecha_salida, numero_huespedes, estado,
                                     tarifa_servicio, total, creado_en)
                VALUES (?, ?, CAST(? AS date), CAST(? AS date) + 2, 1, ?, ?, ?, CAST(? AS date) + time '12:00' AT TIME ZONE 'America/Bogota')""",
                huesped, publicacion, llegada, llegada, estado, tarifaServicio, tarifaServicio * 10, hechaEl);
    }
}
