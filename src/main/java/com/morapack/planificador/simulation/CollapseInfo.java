package com.morapack.planificador.simulation;

import java.time.Instant;

/** Información del colapso logístico detectado durante el plegado de eventos. */
public class CollapseInfo {
    public enum Cause { WAREHOUSE_FULL, AIRCRAFT_FULL, UNSERVICED_ORDER }

    private final boolean collapsed;
    private final Cause cause;
    private final Instant at;
    private final String airportId;        // si aplica (WAREHOUSE_FULL)
    private final String flightInstanceId; // si aplica (AIRCRAFT_FULL)
    private final String pedidoId;         // opcional (si estaba involucrado)

    private CollapseInfo(boolean collapsed, Cause cause, Instant at,
                         String airportId, String flightInstanceId, String pedidoId) {
        this.collapsed = collapsed;
        this.cause = cause;
        this.at = at;
        this.airportId = airportId;
        this.flightInstanceId = flightInstanceId;
        this.pedidoId = pedidoId;
    }

    public static CollapseInfo none() {
        return new CollapseInfo(false, null, null, null, null, null);
    }
    public static CollapseInfo warehouseFull(Instant at, String airportId, String pedidoId) {
        return new CollapseInfo(true, Cause.WAREHOUSE_FULL, at, airportId, null, pedidoId);
    }
    public static CollapseInfo aircraftFull(Instant at, String instanceId, String pedidoId) {
        return new CollapseInfo(true, Cause.AIRCRAFT_FULL, at, null, instanceId, pedidoId);
    }
    public static CollapseInfo unservicedOrder(Instant at, String pedidoId) {
        return new CollapseInfo(true, Cause.UNSERVICED_ORDER, at, null, null, pedidoId);
    }

    public boolean isCollapsed() { return collapsed; }
    public Cause getCause() { return cause; }
    public Instant getAt() { return at; }
    public String getAirportId() { return airportId; }
    public String getFlightInstanceId() { return flightInstanceId; }
    public String getPedidoId() { return pedidoId; }
}
