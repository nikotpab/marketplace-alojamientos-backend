package com.inversioneslr.marketplace.administracion.infraestructura;

import com.inversioneslr.marketplace.administracion.dominio.FormatoReporte;
import com.inversioneslr.marketplace.administracion.dominio.GeneradorReportes;
import com.inversioneslr.marketplace.administracion.dominio.Reporte;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.springframework.stereotype.Component;

/** Adaptadores de OpenPDF y FastExcel: el resto del sistema solo ve {@link GeneradorReportes}. */
@Component
public class GeneradorReportesPdfExcel implements GeneradorReportes {

    @Override
    public byte[] generar(Reporte reporte, FormatoReporte formato) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        switch (formato) {
            case PDF -> pdf(reporte, salida);
            case EXCEL -> excel(reporte, salida);
        }
        return salida.toByteArray();
    }

    private static void pdf(Reporte reporte, ByteArrayOutputStream salida) {
        Document documento = new Document(PageSize.A4.rotate());
        PdfWriter.getInstance(documento, salida);
        documento.open();
        documento.add(new Paragraph(reporte.titulo(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
        documento.add(new Paragraph(" "));
        PdfPTable tabla = new PdfPTable(reporte.columnas().size());
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);
        Font encabezado = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        Font texto = FontFactory.getFont(FontFactory.HELVETICA, 8);
        reporte.columnas().forEach(columna -> tabla.addCell(new Phrase(columna, encabezado)));
        reporte.filas().forEach(fila -> fila.forEach(valor -> tabla.addCell(new Phrase(valor, texto))));
        documento.add(tabla);
        documento.close();
    }

    private static void excel(Reporte reporte, ByteArrayOutputStream salida) {
        try (Workbook libro = new Workbook(salida, "Marketplace Alojamientos", "1.0");
             Worksheet hoja = libro.newWorksheet(reporte.titulo())) {
            for (int columna = 0; columna < reporte.columnas().size(); columna++) {
                hoja.value(0, columna, reporte.columnas().get(columna));
                hoja.style(0, columna).bold().set();
            }
            for (int fila = 0; fila < reporte.filas().size(); fila++) {
                List<String> valores = reporte.filas().get(fila);
                for (int columna = 0; columna < valores.size(); columna++) {
                    escribir(hoja, fila + 1, columna, valores.get(columna));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Los montos y las cantidades van como números, para poder sumarlos en Excel. */
    private static void escribir(Worksheet hoja, int fila, int columna, String valor) {
        try {
            hoja.value(fila, columna, new BigDecimal(valor));
        } catch (NumberFormatException e) {
            hoja.value(fila, columna, valor);
        }
    }
}
