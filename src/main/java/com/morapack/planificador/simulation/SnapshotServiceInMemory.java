package com.morapack.planificador.simulation;


import com.morapack.planificador.dominio.Aeropuerto;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reproduce los eventos hasta T y arma el snapshot: ocupaciones de almacenes,
 * cargas por instancia de vuelo, y muestra de pedidos.
 */
public class SnapshotServiceInMemory implements SnapshotService {

    private final List<Event> events;
    private final Map<String, Aeropuerto> aeropuertos;
    private final Map<String, Integer> flightCapacities; // instanceId -> capacidad total

    public SnapshotServiceInMemory(List<Event> events,
                                   Map<String, Aeropuerto> aeropuertos,
                                   Map<String, Integer> flightCapacities) {
        this.events = events;
        this.aeropuertos = aeropuertos;
        this.flightCapacities = flightCapacities != null ? flightCapacities : Collections.emptyMap();
    }

    @Override
    public EstadosTemporales stateAt(Instant at) {
        Map<String, Integer> wh = new HashMap<>(); // airport -> ocupación
        Map<String, Integer> fi = new HashMap<>(); // instanceId -> ocupación
        Map<String, String> pedidoState = new HashMap<>(); // pedido -> estado
        Map<String, List<String>> pedidosEnAeropuerto = new HashMap<>(); // aeropuerto -> lista de pedidos

        for (Event e : events) {
            if (e.ts.isAfter(at)) break;

            switch (e.type) {
                case WAIT_START -> {
                    // entra a almacén
                    wh.merge(e.airport, e.amount, Integer::sum);
                    pedidoState.put(e.pedidoId, "EN_ALMACEN");
                    pedidosEnAeropuerto.computeIfAbsent(e.airport, k -> new ArrayList<>()).add(e.pedidoId);
                }
                case WAIT_END -> {
                    // nada que balancear (permanece en almacén hasta LOAD)
                }
                case LOAD -> {
                    // sale del almacén
                    wh.merge(e.airport, -e.amount, Integer::sum);
                    fi.merge(e.flightInstanceId, e.amount, Integer::sum);
                    pedidoState.put(e.pedidoId, "EN_VUELO");
                    // Remover de la lista del aeropuerto
                    List<String> pedidosDelAeropuerto = pedidosEnAeropuerto.get(e.airport);
                    if (pedidosDelAeropuerto != null) {
                        pedidosDelAeropuerto.remove(e.pedidoId);
                    }
                }
                case ARRIVAL -> {
                    // llegada al destino del tramo: entra a almacén del destino
                    wh.merge(e.airport, e.amount, Integer::sum);
                    pedidoState.put(e.pedidoId, "EN_ALMACEN");
                    pedidosEnAeropuerto.computeIfAbsent(e.airport, k -> new ArrayList<>()).add(e.pedidoId);
                }
                case PICKUP_READY -> {
                    // cliente retira (sale de almacén)
                    wh.merge(e.airport, -Math.max(0, wh.getOrDefault(e.airport, 0)), Integer::sum);
                    pedidoState.put(e.pedidoId, "LISTO_PICKUP");
                    // Remover de la lista del aeropuerto
                    List<String> pedidosDelAeropuerto = pedidosEnAeropuerto.get(e.airport);
                    if (pedidosDelAeropuerto != null) {
                        pedidosDelAeropuerto.remove(e.pedidoId);
                    }
                }
                case CANCELLATION -> {
                    // no tocamos contadores aquí (ya los tocará replan); sirve sólo para UI
                }
            }
        }

        EstadosTemporales st = new EstadosTemporales(
            at,
            wh,        // ocupacionAeropuerto
            fi,        // ocupacionVueloInstance  
            pedidosEnAeropuerto,
            pedidoState
        );

        return st;
    }
}
