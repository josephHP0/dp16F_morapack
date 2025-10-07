package com.morapack.planificador.simulation;



import java.time.Instant;
import java.util.Objects;

/** Evento atómico reproducible en el timeline. */
public class Event implements Comparable<Event> {

    public enum Type {
        WAIT_START,   // inicia espera en almacén (arribo o pre-carga)
        WAIT_END,     // fin de espera, justo antes de LOAD
        LOAD,         // carga a vuelo (reserva capacidad de flight instance)
        ARRIVAL,      // arribo de flight instance
        PICKUP_READY, // dentro de ventana 2h en destino (para KPI)
        CANCELLATION  // cancelación de vuelo/instancia
    }

    public final Instant ts;          // instante UTC
    public final Type type;
    public final String airport;      // almacén implicado (WAIT_*, ARRIVAL, PICKUP_READY)
    public final String flightInstanceId; // id único del slot de vuelo (en LOAD/ARRIVAL/CANCELLATION)
    public final String pedidoId;     // pedido al que afecta (no aplica en CANCELLATION sin pedido)
    public final int amount;          // cantidad de paquetes (para LOAD/ARRIVAL/WAI*). 0 si no aplica.

    private Event(Instant ts, Type type, String airport, String flightInstanceId, String pedidoId, int amount) {
        this.ts = ts;
        this.type = type;
        this.airport = airport;
        this.flightInstanceId = flightInstanceId;
        this.pedidoId = pedidoId;
        this.amount = amount;
    }

    public static Event waitStart(Instant ts, String airport, String pedidoId, int amount) {
        return new Event(ts, Type.WAIT_START, airport, null, pedidoId, amount);
    }
    public static Event waitEnd(Instant ts, String airport, String pedidoId, int amount) {
        return new Event(ts, Type.WAIT_END, airport, null, pedidoId, amount);
    }
    public static Event load(Instant ts, String airport, String flightInstanceId, String pedidoId, int amount) {
        return new Event(ts, Type.LOAD, airport, flightInstanceId, pedidoId, amount);
    }
    public static Event arrival(Instant ts, String airport, String flightInstanceId, String pedidoId, int amount) {
        return new Event(ts, Type.ARRIVAL, airport, flightInstanceId, pedidoId, amount);
    }
    public static Event pickupReady(Instant ts, String airport, String pedidoId) {
        return new Event(ts, Type.PICKUP_READY, airport, null, pedidoId, 0);
    }
    public static Event cancellation(Instant ts, String flightInstanceId, String airport) {
        return new Event(ts, Type.CANCELLATION, airport, flightInstanceId, null, 0);
    }

    @Override
    public int compareTo(Event o) {
        int c = this.ts.compareTo(o.ts);
        if (c != 0) return c;
        // orden estable por tipo para evitar “salto” visual
        return this.type.ordinal() - o.type.ordinal();
    }

    @Override
    public String toString() {
        return ts + " " + type + " ap=" + airport + " fi=" + flightInstanceId + " pid=" + pedidoId + " amt=" + amount;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Event e)) return false;
        return Objects.equals(ts, e.ts) && type == e.type &&
                Objects.equals(airport, e.airport) &&
                Objects.equals(flightInstanceId, e.flightInstanceId) &&
                Objects.equals(pedidoId, e.pedidoId) && amount == e.amount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ts, type, airport, flightInstanceId, pedidoId, amount);
    }
}
