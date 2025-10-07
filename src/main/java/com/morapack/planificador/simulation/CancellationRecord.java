package com.morapack.planificador.simulation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** Registro de cancelación: día, hora local del origen, O/D y HH:mm del vuelo. */
public class CancellationRecord {
    public final String origen;
    public final String destino;
    public final int hora;   // 0..23
    public final int minuto; // 0..59
    public final LocalDate fecha; // fecha local del ORIGEN en que aplica
    public final Instant effectiveAtUtc; // instante UTC equivalente (dep calculado en zona del ORIGEN)
    public final String instanceId; // ORI-DEST-HHmm-YYYYMMDD

    public CancellationRecord(String origen, String destino, int hora, int minuto, LocalDate fecha, ZoneId tzOrigen) {
        this.origen = origen;
        this.destino = destino;
        this.hora = hora;
        this.minuto = minuto;
        this.fecha = fecha;
        // Construimos el instante UTC exacto
        this.effectiveAtUtc = fecha.atTime(LocalTime.of(hora, minuto)).atZone(tzOrigen).toInstant();
        String hhmm = String.format("%02d%02d", hora, minuto);
        this.instanceId = origen + "-" + destino + "-" + hhmm + "-" + fecha.toString().replace("-", "");
    }
}
