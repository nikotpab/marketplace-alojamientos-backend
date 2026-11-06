-- Modelo de datos del marketplace. La garantía anti-solape vive en reserva (ADR-03).
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Usuarios y configuración
CREATE TABLE usuario (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre             varchar(120),
    apellido           varchar(120),
    correo             varchar(255) NOT NULL UNIQUE,
    telefono           varchar(30),
    hash_contrasena    varchar(255) NOT NULL,
    rol                varchar(40)  NOT NULL CHECK (rol IN ('HUESPED', 'ANFITRION', 'ADMINISTRADOR')),
    cuenta_activa      boolean      NOT NULL DEFAULT true,
    restringido        boolean      NOT NULL DEFAULT false,
    motivo_restriccion text,
    intentos_fallidos  integer      NOT NULL DEFAULT 0,
    bloqueado_hasta    timestamptz,
    creado_en          timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE tipo_alojamiento (
    id        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre    varchar(120) NOT NULL UNIQUE,
    activo    boolean      NOT NULL DEFAULT true,
    creado_en timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE servicio (
    id        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre    varchar(120) NOT NULL UNIQUE,
    creado_en timestamptz  NOT NULL DEFAULT now()
);

-- Publicaciones
CREATE TABLE politica_cancelacion (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre                varchar(120),
    horas_anticipacion    integer,
    porcentaje_devolucion numeric(5, 2) CHECK (porcentaje_devolucion BETWEEN 0 AND 100),
    creado_en             timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE configuracion_tarifa (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clave          varchar(120) NOT NULL UNIQUE,
    porcentaje     numeric(5, 2) CHECK (porcentaje BETWEEN 0 AND 100),
    noches_minimas integer,
    modificado_por uuid REFERENCES usuario (id),
    creado_en      timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE publicacion (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    anfitrion_id            uuid NOT NULL REFERENCES usuario (id),
    tipo_alojamiento_id     uuid NOT NULL REFERENCES tipo_alojamiento (id),
    politica_cancelacion_id uuid NOT NULL REFERENCES politica_cancelacion (id),
    titulo                  varchar(180),
    descripcion             text,
    ciudad                  varchar(120),
    ubicacion_textual       varchar(255),
    capacidad               integer CHECK (capacidad > 0),
    precio_noche            numeric(12, 2) CHECK (precio_noche > 0),
    estado                  varchar(40) NOT NULL DEFAULT 'BORRADOR'
        CHECK (estado IN ('BORRADOR', 'PENDIENTE_REVISION', 'ACTIVA', 'PAUSADA', 'BLOQUEADA')),
    motivo_estado           text,
    creado_en               timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE publicacion_servicio (
    publicacion_id uuid NOT NULL REFERENCES publicacion (id),
    servicio_id    uuid NOT NULL REFERENCES servicio (id),
    creado_en      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (publicacion_id, servicio_id)
);

CREATE TABLE regla_alojamiento (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    publicacion_id uuid NOT NULL REFERENCES publicacion (id),
    descripcion    text,
    creado_en      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE imagen_publicacion (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    publicacion_id uuid NOT NULL REFERENCES publicacion (id),
    ruta           text,
    orden          integer,
    creado_en      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE servicio_adicional (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    publicacion_id uuid NOT NULL REFERENCES publicacion (id),
    nombre         varchar(120),
    costo          numeric(12, 2) CHECK (costo >= 0),
    creado_en      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE periodo_disponibilidad (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    publicacion_id uuid NOT NULL REFERENCES publicacion (id),
    fecha_inicio   date        NOT NULL,
    fecha_fin      date        NOT NULL,
    tipo           varchar(40) NOT NULL CHECK (tipo IN ('DISPONIBLE', 'BLOQUEADO')),
    creado_en      timestamptz NOT NULL DEFAULT now(),
    CHECK (fecha_fin > fecha_inicio)
);

-- Reservas y pagos
CREATE TABLE reserva (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    huesped_id       uuid        NOT NULL REFERENCES usuario (id),
    publicacion_id   uuid        NOT NULL REFERENCES publicacion (id),
    fecha_llegada    date        NOT NULL,
    fecha_salida     date        NOT NULL,
    numero_huespedes integer     NOT NULL CHECK (numero_huespedes > 0),
    estado           varchar(40) NOT NULL
        CHECK (estado IN ('CONFIRMADA', 'CANCELADA', 'COMPLETADA', 'EN_REVISION')),
    noches           integer,
    precio_base      numeric(12, 2),
    tarifa_limpieza  numeric(12, 2),
    tarifa_servicio  numeric(12, 2),
    descuento        numeric(12, 2),
    total            numeric(12, 2),
    creado_en        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT reserva_fechas CHECK (fecha_salida > fecha_llegada),
    -- ADR-03: rangos [llegada, salida) para que una reserva pueda empezar el día en que otra termina.
    CONSTRAINT reserva_sin_solape EXCLUDE USING gist (
        publicacion_id WITH =,
        daterange(fecha_llegada, fecha_salida, '[)') WITH &&
    ) WHERE (estado = 'CONFIRMADA')
);

CREATE TABLE reserva_servicio_adicional (
    reserva_id            uuid NOT NULL REFERENCES reserva (id),
    servicio_adicional_id uuid NOT NULL REFERENCES servicio_adicional (id),
    costo_aplicado        numeric(12, 2),
    creado_en             timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (reserva_id, servicio_adicional_id)
);

CREATE TABLE pago (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reserva_id         uuid REFERENCES reserva (id),
    id_pago_stripe     varchar(255) UNIQUE,
    clave_idempotencia varchar(255) UNIQUE,
    monto              numeric(12, 2) NOT NULL,
    estado             varchar(40)    NOT NULL,
    creado_en          timestamptz    NOT NULL DEFAULT now()
);

CREATE TABLE solicitud_procesada (
    clave      varchar(255) PRIMARY KEY,
    huesped_id uuid        NOT NULL REFERENCES usuario (id),
    reserva_id uuid REFERENCES reserva (id),
    resultado  jsonb,
    expira_en  timestamptz,
    creado_en  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE cancelacion (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reserva_id       uuid        NOT NULL UNIQUE REFERENCES reserva (id),
    usuario_id       uuid        NOT NULL REFERENCES usuario (id),
    fecha            timestamptz,
    motivo           text,
    valor_devolucion numeric(12, 2),
    estado_final     varchar(40),
    creado_en        timestamptz NOT NULL DEFAULT now()
);

-- Notificaciones y reseñas
CREATE TABLE notificacion (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id uuid        NOT NULL REFERENCES usuario (id),
    tipo       varchar(40),
    mensaje    text,
    leida      boolean     NOT NULL DEFAULT false,
    creado_en  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE resena (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reserva_id   uuid        NOT NULL UNIQUE REFERENCES reserva (id),
    calificacion integer CHECK (calificacion BETWEEN 1 AND 5),
    contenido    text,
    oculta       boolean     NOT NULL DEFAULT false,
    ocultada_por uuid REFERENCES usuario (id),
    creado_en    timestamptz NOT NULL DEFAULT now()
);

-- Transversales
CREATE TABLE evento_pendiente (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo         varchar(80) NOT NULL,
    payload      jsonb       NOT NULL,
    estado       varchar(40) NOT NULL DEFAULT 'PENDIENTE',
    intentos     integer     NOT NULL DEFAULT 0,
    procesado_en timestamptz,
    creado_en    timestamptz NOT NULL DEFAULT now()
);

-- ADR-02: índices para la búsqueda. La disponibilidad usa el índice gist de reserva_sin_solape.
CREATE INDEX publicacion_busqueda ON publicacion (ciudad, tipo_alojamiento_id, capacidad, precio_noche) WHERE estado = 'ACTIVA';
CREATE INDEX publicacion_servicio_por_servicio ON publicacion_servicio (servicio_id);
CREATE INDEX periodo_por_publicacion ON periodo_disponibilidad (publicacion_id, tipo, fecha_inicio, fecha_fin);

CREATE INDEX publicacion_por_anfitrion ON publicacion (anfitrion_id);
CREATE INDEX reserva_por_huesped ON reserva (huesped_id);
CREATE INDEX notificacion_por_usuario ON notificacion (usuario_id, creado_en DESC);
CREATE INDEX evento_por_procesar ON evento_pendiente (creado_en) WHERE estado = 'PENDIENTE';
