package com.morapack.planificador.nucleo;

import com.morapack.planificador.dominio.Aeropuerto;
import com.morapack.planificador.dominio.Vuelo;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Grafo de vuelos que ahora distingue entre:
 * - Plantillas diarias (Vuelo) leídas de vuelos.txt
 * - Instancias diarias (FlightInstance) generadas para un rango [tStart..tEnd]
 */
public class GrafoVuelos {

    // ==== Arista para heurísticas "clásicas" (compatibilidad) ====
    public static class Arista {
        public final String a;     // origen
        public final String b;     // destino
        public final double horas; // duración
        public final int vueloId;  // id plantilla
        public Arista(String a, String b, double h, int id) {
            this.a = a; this.b = b; this.horas = h; this.vueloId = id;
        }
    }

    // ==== NUEVO: Instancia diaria (slot) con tiempos concretos ====
   public static class FlightInstance {
    /** Id única de instancia, p.ej. "EBCI-EDDI-1037-20251101" */
    public final String instanceId;

    /** Origen/Destino (IATA) y capacidad de la instancia diaria. */
    public final String origen;
    public final String destino;
    public final int capacidad;

    /** Salida y llegada en UTC (el Grafo expande usando zonas de cada aeropuerto). */
    public final java.time.Instant arrUtc;
    public final java.time.Instant depUtc;

    // === NUEVO: estado vivo de la instancia ===
    private int ocupados = 0;
    private boolean cancelado = false;

    public FlightInstance(String instanceId,
                          String origen,
                          String destino,
                          java.time.Instant arrUtc,
                          java.time.Instant depUtc,
                          int capacidad) {
        this.instanceId = java.util.Objects.requireNonNull(instanceId, "instanceId");
        this.origen = java.util.Objects.requireNonNull(origen, "origen");
        this.destino = java.util.Objects.requireNonNull(destino, "destino");
        this.arrUtc = java.util.Objects.requireNonNull(arrUtc, "salidaUtc");
        this.depUtc = java.util.Objects.requireNonNull(depUtc, "llegadaUtc");
        this.capacidad = Math.max(0, capacidad);
    }

    /** Capacidad restante. */
    public int remaining() {
        return Math.max(0, capacidad - ocupados);
    }

    /** ¿Puede cargar 'qty' paquetes? */
    public boolean canLoad(int qty) {
        return !cancelado && qty > 0 && remaining() >= qty;
    }

    /** Marca carga a bordo (sin side-effects externos). Lanza si no hay capacidad. */
    public void load(int qty) {
        if (!canLoad(qty)) {
            throw new IllegalStateException("No hay capacidad en " + instanceId + " para " + qty + " paquetes");
        }
        ocupados += qty;
    }

    /** Descarga “virtual” (por sim). */
    public void unload(int qty) {
        if (qty <= 0) return;
        ocupados = Math.max(0, ocupados - qty);
    }

    /** Cancela esta instancia (para aplicar archivo de cancelaciones). */
    public void cancel() {
        this.cancelado = true;
    }

    public boolean isCancelled() {
        return cancelado;
    }

    public int getOcupados() {
        return ocupados;
    }

    public String legKey() {
        return origen + "-" + destino;
    }

    @Override
    public String toString() {
        return instanceId + " [" + origen + "→" + destino + "] cap=" + capacidad + " used=" + ocupados + (cancelado ? " CANCELLED" : "");
    }

    @Override
    public int hashCode() {
        return instanceId.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof FlightInstance other)) return false;
        return this.instanceId.equals(other.instanceId);
    }
}
    // ==== Datos base ====
    private final List<Vuelo> vuelos = new ArrayList<>();
    private final Map<String, List<Arista>> ady = new HashMap<>();

    // ==== Carga base ====
    public GrafoVuelos(List<Vuelo> vuelos) {
        this.vuelos.addAll(vuelos);
        for (Vuelo v : vuelos) {
            ady.computeIfAbsent(v.origen, k -> new ArrayList<>())
               .add(new Arista(v.origen, v.destino, v.horasDuracion, v.id));
        }
    }

    public List<Vuelo> getVuelos() {
        return vuelos;
    }

    public List<Arista> aristasDesde(String origen) {
        return ady.getOrDefault(origen, Collections.emptyList());
    }

    // ==== NUEVO: expansión de plantillas → instancias para la ventana semanal ====
    public List<FlightInstance> expandirSlots(Instant tStart, Instant tEnd, Map<String, Aeropuerto> aeropuertos) {
        List<FlightInstance> out = new ArrayList<>();
        // Iteramos por días dentro de la zona del ORIGEN de cada vuelo
        for (Vuelo tpl : vuelos) {
            Aeropuerto aOri = aeropuertos.get(tpl.origen);
            Aeropuerto aDes = aeropuertos.get(tpl.destino);
            if (aOri == null || aDes == null) continue;

            // Determinar zona horaria basada en el código IATA del aeropuerto
            ZoneId zOrigen = getZonaFromAirport(aOri.getIata());
            ZoneId zDestino = getZonaFromAirport(aDes.getIata());

            // Rango de fechas en la zona de origen
            LocalDate startDate = LocalDateTime.ofInstant(tStart, zOrigen).toLocalDate();
            LocalDate endDate   = LocalDateTime.ofInstant(tEnd, zOrigen).toLocalDate();

            LocalTime horaSalida = LocalTime.of(tpl.salidaMin / 60, tpl.salidaMin % 60);
            DateTimeFormatter ymd = DateTimeFormatter.BASIC_ISO_DATE; // YYYYMMDD

            for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                Instant depUtc = ZonedDateTime.of(d, horaSalida, zOrigen).toInstant();
                Instant arrUtc = depUtc.plus(Duration.ofMinutes(tpl.durationMinutes));

                String hhmm = String.format("%02d%02d", horaSalida.getHour(), horaSalida.getMinute());
                String instanceId = tpl.origen + "-" + tpl.destino + "-" + hhmm + "-" + d.format(ymd);

                out.add(new FlightInstance(
                        instanceId,
                        tpl.origen,
                        tpl.destino,
                        depUtc,
                        arrUtc,
                        tpl.capacidad // capacidad por instancia
                ));
            }
        }
        // Se podría indexar por origen para consultas rápidas; lo dejamos simple aquí
        return out;
    }

    // ==== NUEVO: utilidad para formar instanceId a partir de día/hora/O-D ====
    public static String buildInstanceId(String origen, String destino, int hora, int minuto, LocalDate fecha) {
        String hhmm = String.format("%02d%02d", hora, minuto);
        return origen + "-" + destino + "-" + hhmm + "-" + fecha.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    /**
     * Determina la zona horaria basada en el código IATA del aeropuerto.
     * Esta implementación básica usa códigos conocidos, pero podría expandirse.
     */
    private ZoneId getZonaFromAirport(String iataCode) {
        return switch (iataCode) {
            case "SPIM" -> ZoneId.of("America/Lima");           // Lima, Peru
            case "EBCI" -> ZoneId.of("Europe/Brussels");       // Brussels, Belgium
            case "UBBB" -> ZoneId.of("Asia/Baku");             // Baku, Azerbaijan
            case "EDDI" -> ZoneId.of("Europe/Berlin");         // Berlin, Germany
            case "KJFK" -> ZoneId.of("America/New_York");      // JFK, USA
            case "EGLL" -> ZoneId.of("Europe/London");         // Heathrow, UK
            default -> ZoneId.of("UTC");                       // Default UTC para códigos desconocidos
        };
    }
}
