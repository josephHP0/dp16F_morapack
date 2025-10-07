package com.morapack.planificador.dominio;

public class Vuelo {
    public final int id;             // ID plantilla (del archivo)
    public final String origen;      // IATA origen
    public final String destino;     // IATA destino
    public final int salidaMin;      // minutos desde 00:00
    public final int llegadaMin;     // minutos desde 00:00 (con wrap si cruza medianoche)
    public int capacidad;            // capacidad por instancia (se conserva aquí como meta de plantilla)
    public final double horasDuracion; // compatibilidad previa (duración/60.0)
    public boolean esContinental;    // si origen y destino están en el mismo continente

    // === NUEVO: identidad y duración explícita en minutos para facilitar instancias ===
    public final String flightId;    // identidad estable de la plantilla (p.ej., "SPIM-SCEL-0311")
    public final int durationMinutes; // duración entera en minutos (sin wrap, ya normalizada)

    public Vuelo(int id, String origen, String destino, int salidaMin, int llegadaMin,
                 int capacidad, int duracion, boolean esContinental) {
        this.id = id;
        this.origen = origen;
        this.destino = destino;
        this.salidaMin = salidaMin;
        this.llegadaMin = llegadaMin;
        this.capacidad = capacidad;
        this.horasDuracion = duracion / 60.0;
        this.durationMinutes = duracion;
        this.esContinental = esContinental;
        // flightId determinista por plantilla: ORI-DEST-HHmm(salida)
        this.flightId = origen + "-" + destino + "-" + String.format("%02d%02d", (salidaMin/60), (salidaMin%60));
    }
}
