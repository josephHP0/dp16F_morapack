package com.morapack.planificador.nucleo;
import com.morapack.planificador.dominio.Aeropuerto;
import com.morapack.planificador.dominio.Pedido;
import com.morapack.planificador.nucleo.GrafoVuelos.FlightInstance;
import com.morapack.planificador.simulation.*;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orquestador "sin reloj": compila plan, construye timeline, consulta estado, cancelaciones, replan.
 * Ahora crea SnapshotServiceInMemory con capacidades:
 *  - Aeropuertos (capacidad y flag infiniteSource)
 *  - Vuelos por instancia (capacidad de cada instanceId)
 */
public class AppPlanificador {

    private final Map<String, Aeropuerto> aeropuertos; // por IATA
    private final GrafoVuelos grafo;
    private final PlanificadorAco planner;

    private final TimelineStore store = new TimelineStore();

    // recordamos última ventana para reconstruir capacities de instancias
    private Instant lastStart;
    private Instant lastEnd;

    public AppPlanificador(Map<String, Aeropuerto> aeropuertos, GrafoVuelos grafo) {
        this.aeropuertos = aeropuertos;
        this.grafo = grafo;
        this.planner = new PlanificadorAco(grafo, aeropuertos);
    }

    /** 1) Compilar plan semanal y publicar timeline */
    public String compileWeekly(Instant tStart, Instant tEnd, List<Pedido> pedidos, YearMonth periodo) {
        this.lastStart = tStart;
        this.lastEnd = tEnd;

        PlanResult plan = planner.compilarPlanSemanal(tStart, tEnd, pedidos, periodo);
        List<Event> timeline = TimelineBuilder.construir(plan.getAsignaciones());
        store.load(plan, timeline);
        return store.getVersion();
    }

    /** Capacidades por instancia para la última ventana. */
    private Map<String,Integer> buildInstanceCapacities() {
        if (lastStart == null || lastEnd == null) return Collections.emptyMap();
        List<FlightInstance> slots = grafo.expandirSlots(lastStart, lastEnd, aeropuertos);
        return slots.stream().collect(Collectors.toMap(fi -> fi.instanceId, fi -> fi.capacidad, (a,b)->a));
    }

    /** 2) Consultar estado en T (para el front) con colapso si ocurre. */
    public com.morapack.planificador.simulation.EstadosTemporales stateAt(Instant at) {
        Map<String,Integer> instCaps = buildInstanceCapacities();
        SnapshotService snapshotService = new SnapshotServiceInMemory(store.getEvents(), aeropuertos, instCaps);
        return snapshotService.stateAt(at);
    }

    /** 3) Slice de eventos para animación */
    public List<Event> eventsBetween(Instant from, Instant to) {
        return store.eventsBetween(from, to);
    }

    /** 4) Aplicar cancelaciones; devuelve pedidos afectados y nueva versión */
    public CancellationService.CancellationSummary applyCancelations(List<CancellationRecord> records) {
        CancellationService cancellationService = new CancellationServiceInMemory(store);
        CancellationService.CancellationSummary sum = cancellationService.apply(records);
        return sum;
    }

    /** 5) Replanificar pendientes y actualizar timeline (versiona) */
    public ReplannerService.Summary replanPending(Instant now, YearMonth periodo) {
        ReplannerService replannerService = new ReplannerServiceInMemory(store, grafo, aeropuertos);
        ReplannerService.Summary s = replannerService.replanPending(now, periodo);
        return s;
    }
}
