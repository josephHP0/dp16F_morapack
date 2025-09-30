package com.morapack.planificador.dominio;

public enum EstadoVuelo {
    PROGRAMADO,     // Vuelo planificado, se puede cancelar
    EN_DESPEGUE,    // Últimos minutos antes de partir (no cancelable)
    EN_VUELO,       // Vuelo en curso (no cancelable)
    ATERRIZADO,     // Vuelo completado
    CANCELADO       // Vuelo cancelado
}