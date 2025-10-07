package com.morapack.planificador.dominio;

import java.time.*;

public class Pedido {
    public final String id;            // Cliente ID (7 digits)
    public final String destinoIata;   // Airport code
    public final int paquetes;         // Quantity (3 digits)
    public final int dia;              // Día del mes (1-31) EN LA VENTANA DE SIMULACIÓN
    public final int hora;             // 0-23
    public final int minuto;           // 0-59

    // === NUEVO (no rompe nada): helpers para timestamp/SLA ===
    // Si el front o el loader necesita un Instant concreto, lo calculamos
    public Instant toInstantAt(ZoneId zoneId, int year, int month) {
        LocalDate date = LocalDate.of(year, month, this.dia);
        LocalTime time = LocalTime.of(this.hora, this.minuto, 0);
        return ZonedDateTime.of(date, time, zoneId).toInstant();
    }

    /**
     * SLA: 2 días intra-continente y 3 días inter-continente.
     * Nota: la evaluación final debe hacerse en la TZ del destino (service de constraints).
     */
    public static Duration slaDuration(boolean sameContinent) {
        return Duration.ofDays(sameContinent ? 2 : 3);
    }

    public Pedido(String id, String destinoIata, int paquetes, int dia, int hora, int minuto) {
        this.id = id;
        this.destinoIata = destinoIata;
        this.paquetes = paquetes;
        this.dia = dia;
        this.hora = hora;
        this.minuto = minuto;
    }
}
