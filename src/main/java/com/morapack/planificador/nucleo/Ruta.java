package com.morapack.planificador.nucleo;

import java.time.Instant;
import java.util.*;

public class Ruta {
    // === COMPATIBILIDAD: (se siguen completando para debug/prints) ===
    public final List<Integer> vuelosUsados = new ArrayList<>();
    public final List<String> itinerario = new ArrayList<>(); // "ORIGEN->DESTINO (h)"
    public final List<String> nodos = new ArrayList<>();       // incluye hub inicial y destino
    public double horasTotales = Double.POSITIVE_INFINITY;

    // === NUEVO: Soporte temporalizado por tramos (instancias diarias) ===
    public static class Tramo {
        public String instanceId;     // ORI-DEST-HHmm-YYYYMMDD
        public String flightId;       // ORI-DEST-HHmm (plantilla)
        public String origen;
        public String destino;
        public Instant depUtc;
        public Instant arrUtc;

        public Tramo(String instanceId, String flightId, String origen, String destino, Instant depUtc, Instant arrUtc) {
            this.instanceId = instanceId;
            this.flightId = flightId;
            this.origen = origen;
            this.destino = destino;
            this.depUtc = depUtc;
            this.arrUtc = arrUtc;
        }
    }

    public final List<Tramo> tramos = new ArrayList<>();
}
