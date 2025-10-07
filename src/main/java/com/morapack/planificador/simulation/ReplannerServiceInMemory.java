package com.morapack.planificador.simulation;



import com.morapack.planificador.dominio.Aeropuerto;
import com.morapack.planificador.nucleo.GrafoVuelos;
import com.morapack.planificador.nucleo.PlanificadorAco;
import com.morapack.planificador.nucleo.Ruta;
import com.morapack.planificador.nucleo.Asignacion;
import com.morapack.planificador.dominio.Pedido;

import java.time.Instant;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Replanifica pedidos que estén "pendientes" o que hayan quedado varados por cancelaciones.
 */
public class ReplannerServiceInMemory implements ReplannerService {

    private final TimelineStore store;
    private final GrafoVuelos grafo;
    private final Map<String, Aeropuerto> aeropuertos;

    public ReplannerServiceInMemory(TimelineStore store, GrafoVuelos grafo, Map<String, Aeropuerto> aeropuertos) {
        this.store = store;
        this.grafo = grafo;
        this.aeropuertos = aeropuertos;
    }

    @Override
    public Summary replanPending(Instant now, YearMonth periodo) {
        // Heurística simple: buscar pedidos que están EN_ALMACEN a esta hora y no han llegado a destino final (no PICKUP_READY).
        SnapshotService snap = new SnapshotServiceInMemory(store.getEvents(), aeropuertos, Collections.emptyMap());
        EstadosTemporales estados = snap.stateAt(now);

        Set<String> candidatos = estados.getEstadoPedido().entrySet().stream()
                .filter(entry -> "EN_ALMACEN".equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (candidatos.isEmpty()) {
            return new Summary(0, store.getVersion());
        }

        // En un sistema real recuperaríamos los Pedido originales; aquí generamos "mock" mínimo:
        List<Pedido> pedidos = candidatos.stream()
                .map(id -> new Pedido(id, "UBBB", 1, 1, 0, 0)) // destino dummy con día/hora mínimos
                .collect(Collectors.toList());

        PlanificadorAco planner = new PlanificadorAco(grafo, aeropuertos);
        PlanResult plan = planner.compilarPlanSemanal(now, now.plusSeconds(3*24*3600), pedidos, periodo);

        List<Event> evs = TimelineBuilder.construir(plan.getAsignaciones());
        store.appendAll(evs);

        return new Summary(plan.getAsignaciones().size(), store.getVersion());
    }
}
