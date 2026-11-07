package com.inversioneslr.marketplace.compartido.presentacion;

import com.inversioneslr.marketplace.compartido.dominio.ExcepcionNegocio;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Traduce los errores a respuestas HTTP. Los de validación indican el campo afectado (RNF-35). */
@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    private static final Map<ExcepcionNegocio.Tipo, HttpStatus> ESTADOS = Map.of(
            ExcepcionNegocio.Tipo.DATOS_INVALIDOS, HttpStatus.BAD_REQUEST,
            ExcepcionNegocio.Tipo.CREDENCIALES_INVALIDAS, HttpStatus.UNAUTHORIZED,
            ExcepcionNegocio.Tipo.CUENTA_BLOQUEADA, HttpStatus.LOCKED,
            ExcepcionNegocio.Tipo.CUENTA_RESTRINGIDA, HttpStatus.FORBIDDEN,
            ExcepcionNegocio.Tipo.CUENTA_DESACTIVADA, HttpStatus.FORBIDDEN,
            ExcepcionNegocio.Tipo.ACCESO_DENEGADO, HttpStatus.FORBIDDEN,
            ExcepcionNegocio.Tipo.NO_ENCONTRADO, HttpStatus.NOT_FOUND,
            ExcepcionNegocio.Tipo.CONFLICTO, HttpStatus.CONFLICT,
            ExcepcionNegocio.Tipo.PAGO_RECHAZADO, HttpStatus.PAYMENT_REQUIRED,
            ExcepcionNegocio.Tipo.PAGO_NO_PROCESADO, HttpStatus.SERVICE_UNAVAILABLE);

    private final ManejadorDenegaciones denegaciones;

    public ManejadorErrores(ManejadorDenegaciones denegaciones) {
        this.denegaciones = denegaciones;
    }

    @ExceptionHandler(ExcepcionNegocio.class)
    ProblemDetail reglaDeNegocio(ExcepcionNegocio excepcion, HttpServletRequest solicitud) {
        log.atInfo()
                .addKeyValue("codigo", excepcion.tipo())
                .addKeyValue("metodo", solicitud.getMethod())
                .addKeyValue("ruta", solicitud.getRequestURI())
                .log("Solicitud rechazada: {}", excepcion.getMessage());
        if (excepcion.tipo() == ExcepcionNegocio.Tipo.ACCESO_DENEGADO) {
            denegaciones.registrar(solicitud);
        }
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(ESTADOS.get(excepcion.tipo()), excepcion.getMessage());
        problema.setProperty("codigo", excepcion.tipo());
        if (!excepcion.campos().isEmpty()) {
            problema.setProperty("campos", excepcion.campos());
        }
        return problema;
    }

    /** Caso de uso aún sin implementar en el esqueleto. */
    @ExceptionHandler(UnsupportedOperationException.class)
    ProblemDetail pendiente(UnsupportedOperationException excepcion) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_IMPLEMENTED, "Pendiente: " + excepcion.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException excepcion,
            HttpHeaders cabeceras, HttpStatusCode estado, WebRequest solicitud) {
        Map<String, String> campos = excepcion.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage, (primero, otro) -> primero));
        ProblemDetail problema = excepcion.getBody();
        problema.setDetail("Hay datos inválidos. Revisa los campos indicados.");
        problema.setProperty("codigo", ExcepcionNegocio.Tipo.DATOS_INVALIDOS);
        problema.setProperty("campos", campos);
        return handleExceptionInternal(excepcion, problema, cabeceras, estado, solicitud);
    }
}
