package com.morapack.planificador.nucleo;

import com.morapack.planificador.dominio.Aeropuerto;
import com.morapack.planificador.dominio.Pedido;
import com.morapack.planificador.nucleo.GrafoVuelos.FlightInstance;
import com.morapack.planificador.simulation.PlanResult;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Planificador con ACO "congestion-aware":
 *  - Heurística penaliza congestión prevista en destino de cada tramo.
 *  - Headroom gating: bloquea/encarece llegar a hubs sin salida neta suficiente.
 *  - Reserva de salidas en hubs calientes.
 *  - Actualiza backlog por buckets horarios mientras asigna.
 *
 * API público compatible con AppPlanificador.
 */
public class PlanificadorAco {

    // --------- Dependencias e índices ---------
    private final GrafoVuelos grafo;
    private final Map<String, Aeropuerto> aeropuertos;
    private final ParametrosAco P;

    // Ventana vigente
    private Instant wStart;
    private Instant wEnd;
    private int hours; // buckets

    // Índices de slots de vuelo para la ventana
    private Map<String, FlightInstance> byId;
    private Map<String, List<FlightInstance>> outByAp; // salidas por aeropuerto (ordenadas por salida)
    private Map<String, List<FlightInstance>> inByAp;  // llegadas por aeropuerto (ordenadas por arribo)

    // Capacidad remanente por instancia de vuelo (se actualiza al asignar)
    private Map<String, Integer> capRemain;

    // Pronóstico/ocupación por aeropuerto y bucket horario (se actualiza al asignar)
    private Map<String, int[]> backlogByAp;    // unidades en almacén
    private Map<String, int[]> outCapByAp;     // capacidad de salida total por bucket (asientos)
    private Map<String, int[]> inCapByAp;      // capacidad de entrada total por bucket (informativa)

    // Feromonas por arco (instancia de vuelo)
    private final Map<String, Double> tau = new HashMap<>();

    // Fuentes infinitas (se infiere del flag del aeropuerto)
    private List<String> infiniteSources;

    public PlanificadorAco(GrafoVuelos grafo, Map<String, Aeropuerto> aeropuertos) {
        this(grafo, aeropuertos, ParametrosAco.defaults());
    }

    public PlanificadorAco(GrafoVuelos grafo, Map<String, Aeropuerto> aeropuertos, ParametrosAco parametros) {
        this.grafo = grafo;
        this.aeropuertos = aeropuertos;
        this.P = parametros == null ? ParametrosAco.defaults() : parametros;
    }

    // ============================================================
    // API principal
    // ============================================================

    public PlanResult compilarPlanSemanal(Instant tStart, Instant tEnd, List<Pedido> pedidos, YearMonth periodo) {
        inicializarVentana(tStart, tEnd);
        indexarSlots(tStart, tEnd);
        prepararPronosticoCapacidades();
        inicializarFeromonas();

        // Orden sugerido: por fecha de registro asc y, a igualdad, por cantidad desc
        pedidos.sort(Comparator
                .comparing((Pedido p) -> getFechaRegistro(p))
                .thenComparing((Pedido p) -> -getCantidad(p)));

        List<Asignacion> asignaciones = new ArrayList<>();
        Random rnd = new Random(42);

        System.out.println("🚀 Iniciando planificación ACO para " + pedidos.size() + " pedidos...");
        int pedidoCount = 0;

        for (Pedido pedido : pedidos) {
            pedidoCount++;
            if (pedidoCount % 10 == 0 || pedidoCount <= 5) {
                System.out.println("   📦 Procesando pedido " + pedidoCount + "/" + pedidos.size() + " (ID: " + pedido.id + ")");
            }
            
            String dest = getDestinoIata(pedido);
            int qty = getCantidad(pedido);
            Instant t0 = max(getFechaRegistro(pedido), wStart);

            // Elige mejor origen entre fuentes infinitas
            RouteSolution best = null;
            for (String source : infiniteSources) {
                RouteSolution sol = buscarRutaACO(source, dest, t0, qty, rnd);
                if (sol != null && (best == null || sol.totalCost < best.totalCost)) {
                    best = sol;
                }
            }

            if (best != null) {
                // Commitear: reservar capacidades y actualizar backlog
                aplicarCompromisos(best, qty);
                asignaciones.add(crearAsignacion(pedido, best));
                // Depositar feromonas
                depositarFeromonas(best, qty);
            } else {
                // Ruta no encontrada: se deja sin asignar (el simulador reportará colapso si procede)
                asignaciones.add(crearAsignacionFallida(pedido));
            }

            // Evaporación ligera por pedido para favorecer exploración
            evaporarFeromonas(P.rho * 0.25);
        }

        // Construir PlanResult con las asignaciones generadas
        return construirResultado(asignaciones);
    }

    // ============================================================
    // Búsqueda de ruta con ACO (una ejecución por pedido)
    // ============================================================

    private RouteSolution buscarRutaACO(String sourceAp, String destAp, Instant startAt, int qty, Random rnd) {
        int ants = 3;   // Muy reducido para pruebas rápidas
        int iters = 1;  // Solo 1 iteración
        RouteSolution globalBest = null;

        for (int it = 0; it < iters; it++) {
            RouteSolution iterationBest = null;
            for (int a = 0; a < ants; a++) {
                RouteSolution sol = construirRutaProbabilistica(sourceAp, destAp, startAt, qty, rnd);
                if (sol != null && (iterationBest == null || sol.totalCost < iterationBest.totalCost)) {
                    iterationBest = sol;
                }
            }
            if (iterationBest != null && (globalBest == null || iterationBest.totalCost < globalBest.totalCost)) {
                globalBest = iterationBest;
            }
            // Evaporación por iteración
            evaporarFeromonas(P.rho);
            if (iterationBest != null) depositarFeromonas(iterationBest, qty);
        }

        return globalBest;
    }

    private RouteSolution construirRutaProbabilistica(String sourceAp, String destAp, Instant startAt, int qty, Random rnd) {
        String ap = sourceAp;
        Instant currentTime = startAt;
        List<FlightInstance> path = new ArrayList<>();
        double totalCost = 0.0;

        for (int hop = 0; hop < P.maxHops; hop++) {
            final Instant t = currentTime; // Variable final para uso en lambdas
            
            // Candidatos: vuelos que salen de 'ap' con depart >= t y capacidad suficiente
            List<FlightInstance> candidates = candidatosSalientes(ap, t, qty);
            if (candidates.isEmpty()) return null;

            // Top-K por heurística (lista candidata)
            candidates.sort(Comparator.comparingDouble(fi -> heuristica(fi, destAp, t, qty)));
            if (candidates.size() > P.candidateK) {
                candidates = candidates.subList(0, P.candidateK);
            }

            // Selección probabilística por ACO
            double sum = 0.0;
            double[] w = new double[candidates.size()];
            for (int i = 0; i < candidates.size(); i++) {
                FlightInstance fi = candidates.get(i);
                double tau_ij = tau.getOrDefault(fi.instanceId, P.pheromoneInit);
                double eta_ij = 1.0 / (1.0 + heuristica(fi, destAp, t, qty)); // menor costo => mayor "atractivo"
                double weight = Math.pow(tau_ij, P.alpha) * Math.pow(eta_ij, P.beta);
                w[i] = weight;
                sum += weight;
            }
            if (sum <= 0) return null;
            double r = rnd.nextDouble() * sum;
            int chosen = 0;
            for (; chosen < w.length; chosen++) {
                r -= w[chosen];
                if (r <= 0) break;
            }
            if (chosen >= candidates.size()) chosen = candidates.size() - 1;

            FlightInstance next = candidates.get(chosen);

            // Chequeos finales: headroom y reserva de salida en destino del tramo
            if (!headroomOK(next.destino, next.arrUtc, qty)) {
                // si no hay headroom, intentamos la siguiente alternativa si existe
                boolean foundAlt = false;
                for (int i = 0; i < candidates.size(); i++) {
                    if (i == chosen) continue;
                    FlightInstance alt = candidates.get(i);
                    if (headroomOK(alt.destino, alt.arrUtc, qty)) {
                        next = alt;
                        foundAlt = true;
                        break;
                    }
                }
                if (!foundAlt) return null;
            }

            // Acumular costo y avanzar
            double c = heuristica(next, destAp, t, qty);
            totalCost += c;
            path.add(next);
            ap = next.destino;
            currentTime = next.arrUtc.plus(P.dwellMin); // dwell mínimo para encadenar

            if (ap.equals(destAp)) {
                return new RouteSolution(path, totalCost);
            }
        }
        return null;
    }

    // ============================================================
    // Heurística "congestion-aware" y gating
    // ============================================================

    private double heuristica(FlightInstance fi, String destFinal, Instant currentTime, int qty) {
        // Normalizaciones
        double timeNorm = Math.max(0, Duration.between(currentTime, fi.arrUtc).toHours()) /
                (double) Math.max(1, P.etaRef.toHours());

        // Espera estimada de conexión en el aeropuerto de llegada (miramos la siguiente salida disponible)
        double waitNorm = estimateWaitNorm(fi.destino, fi.arrUtc);

        // Riesgo SLA: aproximación con deadline de 72h si no hay información continental
        double slaHours = 72.0;
        double arrivalToFinalETA = estimateFastestETA(fi.destino, destFinal, fi.arrUtc.plus(P.dwellMin));
        double riskSLA = arrivalToFinalETA > slaHours ? (arrivalToFinalETA - slaHours) / slaHours : 0.0;

        // Congestión prevista en destino del tramo en su hora de arribo
        double u = occupancyFraction(fi.destino, fi.arrUtc, qty);
        double congPenalty = P.congestionPenalty(u);

        // Hops penaliza levemente
        double hopsPenalty = P.w_hops; // se suma por tramo

        return P.w_time * timeNorm + P.w_wait * waitNorm + P.w_sla * riskSLA + P.w_cong * congPenalty + hopsPenalty;
    }

    private boolean headroomOK(String airport, Instant arr, int qty) {
        Aeropuerto ap = aeropuertos.get(airport);
        if (ap == null) return false;
        if (isInfinite(ap)) return true;

        int capAlmacen = Math.max(1, ap.getCapacidadAlmacen()); // evitar div/0
        int bi = bucket(arr);
        int bEnd = Math.min(hours - 1, bi + (int) (P.headroomHorizon.toHours()));
        int[] backlog = backlogByAp.get(airport);
        int[] outCap = outCapByAp.get(airport);

        long stock = 0;
        for (int b = bi; b <= bEnd; b++) stock = Math.max(stock, backlog[b] + qty); // pico
        // headroom neto en la ventana = sum(salidas) - pico_stock (aprox conservadora)
        long salidas = 0;
        for (int b = bi; b <= bEnd; b++) salidas += outCap[b];

        boolean warehouseOK = (backlog[bi] + qty) <= capAlmacen; // no romper capacidad instantánea
        boolean flowOK = salidas >= stock; // hay “escape” suficiente en H

        return warehouseOK && flowOK;
    }

    private double estimateWaitNorm(String airport, Instant arr) {
        // próxima salida ≥ arr + dwellMin
        Instant t = arr.plus(P.dwellMin);
        List<FlightInstance> outs = outByAp.getOrDefault(airport, List.of());
        FlightInstance next = null;
        for (FlightInstance x : outs) {
            if (!x.depUtc.isBefore(t)) { next = x; break; }
        }
        if (next == null) return 1.0; // nada a la vista → espera mala
        long waitH = Duration.between(arr, next.depUtc).toHours();
        return Math.min(2.0, waitH / (double) Math.max(1, P.waitRef.toHours())); // acotar
    }

    private double occupancyFraction(String airport, Instant atArr, int incomingQty) {
        Aeropuerto ap = aeropuertos.get(airport);
        if (ap == null || isInfinite(ap)) return 0.0;
        int cap = Math.max(1, ap.getCapacidadAlmacen());
        int b = bucket(atArr);
        int occ = backlogByAp.get(airport)[b] + incomingQty;
        return Math.min(1.5, occ / (double) cap); // permitimos >1 para penalización fuerte
    }

    // ETA más rápida (aprox) ignorando capacidades: greedy al siguiente salto más temprano
    private double estimateFastestETA(String from, String to, Instant startAt) {
        if (from.equals(to)) return 0.0;
        // Búsqueda limitada a 6 hops y 48h
        Instant limit = startAt.plus(Duration.ofHours(48));
        String ap = from;
        Instant t = startAt;

        for (int hop = 0; hop < 6; hop++) {
            List<FlightInstance> outs = outByAp.getOrDefault(ap, List.of());
            FlightInstance best = null;
            for (FlightInstance fi : outs) {
                if (fi.depUtc.isBefore(t)) continue;
                if (fi.arrUtc.isAfter(limit)) continue;
                if (best == null || fi.arrUtc.isBefore(best.arrUtc)) best = fi;
            }
            if (best == null) break;
            ap = best.destino;
            t = best.arrUtc.plus(P.dwellMin);
            if (ap.equals(to)) {
                return Duration.between(startAt, best.arrUtc).toHours();
            }
        }
        return 72.0; // penalizar si no encontramos algo razonable
    }

    // ============================================================
    // Candidatos y chequeos de capacidad
    // ============================================================

    private List<FlightInstance> candidatosSalientes(String ap, Instant t, int qty) {
        List<FlightInstance> outs = outByAp.getOrDefault(ap, List.of());
        List<FlightInstance> res = new ArrayList<>();
        for (FlightInstance fi : outs) {
            if (fi.depUtc.isBefore(t)) continue;

            // Capacidad disponible en el vuelo considerando la reserva para tránsito
            int rem = capRemain.getOrDefault(fi.instanceId, fi.capacidad);
            int reserved = (int) Math.floor(fi.capacidad * P.reserveTransitRatio);
            int usable = Math.max(0, rem - reserved);

            if (usable >= qty) {
                res.add(fi);
            }
        }
        return res;
    }

    // ============================================================
    // Commit de asignaciones y mantenimiento de estados
    // ============================================================

    private void aplicarCompromisos(RouteSolution sol, int qty) {
        // 1) Reducir capacidad de vuelos
        for (FlightInstance fi : sol.path) {
            int rem = capRemain.getOrDefault(fi.instanceId, fi.capacidad);
            capRemain.put(fi.instanceId, Math.max(0, rem - qty));
        }
        // 2) Actualizar backlog por aeropuerto entre ARRIVAL y próxima DEPARTURE usada en la ruta
        //    Para cada hop, el paquete ocupa espacio en el almacén de destino desde la llegada
        //    hasta el despegue del siguiente tramo (o 2h si es destino final).
        for (int i = 0; i < sol.path.size(); i++) {
            FlightInstance fi = sol.path.get(i);
            String airport = fi.destino;
            Instant from = fi.arrUtc;
            Instant to;
            if (i < sol.path.size() - 1) {
                to = sol.path.get(i + 1).depUtc; // hasta la salida real del siguiente
            } else {
                to = fi.arrUtc.plus(Duration.ofHours(2)); // ventana de pickup en destino final
            }
            incrementarBacklog(airport, from, to, qty);
        }
    }

    private void incrementarBacklog(String airport, Instant from, Instant to, int qty) {
        if (!backlogByAp.containsKey(airport)) return;
        int bi = bucket(from);
        int bj = bucket(to);
        int[] arr = backlogByAp.get(airport);
        for (int b = Math.max(0, bi); b <= Math.min(hours - 1, bj); b++) {
            arr[b] += qty;
        }
    }

    // ============================================================
    // Feromonas
    // ============================================================

    private void inicializarFeromonas() {
        for (String id : byId.keySet()) { 
            tau.put(id, P.pheromoneInit);
        }
    }

    private void evaporarFeromonas(double rho) {
        for (Map.Entry<String, Double> e : tau.entrySet()) {
            double v = e.getValue();
            v = Math.max(P.pheromoneFloor, (1.0 - rho) * v);
            e.setValue(v);
        }
    }

    private void depositarFeromonas(RouteSolution sol, int qty) {
        // Depósito inverso al costo, ponderado por cantidad (más “señal” para paquetes grandes)
        double delta = qty / Math.max(1.0, sol.totalCost + 1e-6);
        for (FlightInstance fi : sol.path) {
            double v = tau.getOrDefault(fi.instanceId, P.pheromoneInit);
            tau.put(fi.instanceId, v + delta);
        }
    }

    // ============================================================
    // Preparación de ventana/índices y forecast de capacidades
    // ============================================================

    private void inicializarVentana(Instant tStart, Instant tEnd) {
        this.wStart = tStart;
        this.wEnd = tEnd;
        this.hours = (int) Math.max(1, Duration.between(tStart, tEnd).toHours());
        // Fuentes infinitas
        this.infiniteSources = aeropuertos.values().stream()
                .filter(this::isInfinite)
                .map(Aeropuerto::getIata)
                .toList();
    }

    private void indexarSlots(Instant tStart, Instant tEnd) {
        List<FlightInstance> slots = grafo.expandirSlots(tStart, tEnd, aeropuertos);

        // byId
        this.byId = new HashMap<>();
        for (FlightInstance fi : slots) {
            byId.put(fi.instanceId, fi);
        }

        // salidas por aeropuerto (ordenadas)
        this.outByAp = slots.stream()
                .collect(Collectors.groupingBy(fi -> fi.origen));
        for (List<FlightInstance> list : outByAp.values()) {
            list.sort(Comparator.comparing(fi -> fi.depUtc));
        }

        // llegadas por aeropuerto (ordenadas)
        this.inByAp = slots.stream()
                .collect(Collectors.groupingBy(fi -> fi.destino));
        for (List<FlightInstance> list : inByAp.values()) {
            list.sort(Comparator.comparing(fi -> fi.arrUtc    ));
        }

        // capacidades remanentes iniciales
        this.capRemain = new HashMap<>();
        for (FlightInstance fi : slots) {
            capRemain.put(fi.instanceId, fi.capacidad);
        }
    }

    private void prepararPronosticoCapacidades() {
        this.backlogByAp = new HashMap<>();
        this.outCapByAp = new HashMap<>();
        this.inCapByAp = new HashMap<>();

        for (String ap : aeropuertos.keySet()) {
            backlogByAp.put(ap, new int[hours]);
            outCapByAp.put(ap, new int[hours]);
            inCapByAp.put(ap, new int[hours]);
        }

        // poblar in/out por bucket a partir de los slots
        for (List<FlightInstance> outs : outByAp.values()) {
            for (FlightInstance fi : outs) {
                int b = bucket(fi.depUtc);
                int[] arr = outCapByAp.get(fi.origen);
                if (b >= 0 && b < hours) arr[b] += fi.capacidad;
            }
        }
        for (List<FlightInstance> ins : inByAp.values()) {
            for (FlightInstance fi : ins) {
                int b = bucket(fi.arrUtc);
                int[] arr = inCapByAp.get(fi.destino);
                if (b >= 0 && b < hours) arr[b] += fi.capacidad;
            }
        }
    }

    // ============================================================
    // Utilidades de tiempo y acceso a entidades
    // ============================================================

    private int bucket(Instant t) {
        if (t.isBefore(wStart)) return 0;
        if (t.isAfter(wEnd) || t.equals(wEnd)) return hours - 1;
        int b = (int) Duration.between(wStart, t).toHours();
        // Garantizar que el bucket esté dentro del rango válido [0, hours-1]
        return Math.min(b, hours - 1);
    }

    private boolean isInfinite(Aeropuerto ap) {
        // TODO: si tu getter/flag difiere, cámbialo aquí
        return ap.isInfiniteSource() || ap.getCapacidadAlmacen() >= Integer.MAX_VALUE / 4;
    }

    private Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    // ============================================================
    // Construcción de objetos externos (Adaptar si tus POJOs difieren)
    // ============================================================

    private Asignacion crearAsignacion(Pedido pedido, RouteSolution sol) {
        // TODO: ajusta a tu constructor real de Asignacion.
        // Crear una ruta a partir de los FlightInstance
        Ruta ruta = new Ruta();
        for (var fi : sol.path) {
            ruta.tramos.add(new Ruta.Tramo(fi.instanceId, fi.origen + "-" + fi.destino, 
                                          fi.origen, fi.destino, fi.depUtc, fi.arrUtc));
        }
        
        // Asumir que se asignaron todos los paquetes del pedido
        return new Asignacion(pedido, null, ruta, pedido.paquetes, 0);
    }

    private Asignacion crearAsignacionFallida(Pedido pedido) {
        // Marcamos una asignación vacía; el simulador sabrá que no se encontró ruta
        return new Asignacion(pedido, null, null, 0, pedido.paquetes);
    }

    private PlanResult construirResultado(List<Asignacion> asignaciones) {
        // TODO: adapta si tu PlanResult tiene otro constructor o builder.
        return new PlanResult(asignaciones);
    }

    // ============================================================
    // Getters tolerantes (evita romper por nombres distintos)
    // ============================================================

    private String getDestinoIata(Pedido p) {
        return p.destinoIata; // Acceso directo al campo público
    }

    private int getCantidad(Pedido p) {
        return p.paquetes; // Acceso directo al campo público
    }

    private Instant getFechaRegistro(Pedido p) {
        return LocalDateTime.of(2025, 11, p.dia, p.hora, p.minuto)
                .atZone(ZoneId.systemDefault())
                .toInstant(); // Construir Instant a partir de los campos fecha/hora
    }

    // ============================================================
    // Tipos internos
    // ============================================================

    private static class RouteSolution {
        final List<FlightInstance> path;
        final double totalCost;
        RouteSolution(List<FlightInstance> path, double totalCost) {
            this.path = path;
            this.totalCost = totalCost;
        }
    }
}
