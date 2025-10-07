package com.morapack.planificador.app;


import com.morapack.planificador.dominio.Aeropuerto;
import com.morapack.planificador.dominio.Pedido;
import com.morapack.planificador.dominio.Vuelo;
import com.morapack.planificador.util.UtilArchivos;
import com.morapack.planificador.nucleo.GrafoVuelos;
import com.morapack.planificador.nucleo.AppPlanificador;
import com.morapack.planificador.nucleo.GrafoVuelos.FlightInstance;
import com.morapack.planificador.simulation.*;

import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reproductor "en tiempo real" (playback) por consola.
 *
 * Configurable con args:
 *   args[0] = ruta aeropuertos.txt (por defecto "aeropuertos.txt")
 *   args[1] = ruta vuelos.txt      (por defecto "vuelos.txt")
 *   args[2] = ruta pedidos.txt     (por defecto "pedidos2.txt")
 *   args[3] = ruta cancel.txt      (opcional; por defecto "cancelaciones.txt" si existe)
 *   args[4] = velocidad (sim-minutos por segundo real, ej. 30) (opcional; default 60)
 *   args[5] = tick (minutos de salto sim por iteración, ej. 60) (opcional; default 60)
 *
 * Muestra:
 *  - Eventos nuevos entre ticks
 *  - Top almacenes (ocupación)
 *  - Top vuelos (ocupación/ capacidad / %)
 *  - Colapso cuando ocurra (y corta)
 */
public class RealTimeConsole {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    public static void main(String[] args) throws Exception {
        // ====== Paths ======
        Path aeropTxt = Path.of(args.length > 0 ? args[0] : "data/aeropuertos.txt");
        Path vuelosTxt = Path.of(args.length > 1 ? args[1] : "data/vuelos.txt");
        Path pedidosTxt = Path.of(args.length > 2 ? args[2] : "data/pedidos_mensual.txt");
        Path cancelTxt  = Path.of(args.length > 3 ? args[3] : "data/cancelaciones.txt");

        // ====== Velocidad y tick ======
        int simMinutesPerSecond = args.length > 4 ? Integer.parseInt(args[4]) : 60; // 1 seg = 60 min simulados
        int tickMinutes = args.length > 5 ? Integer.parseInt(args[5]) : 60;         // cada iteración avanza 60 min sim
        long sleepMs = Math.max(10, Math.round((tickMinutes * 1000.0) / simMinutesPerSecond));

        // ====== Ventana semanal ======
        // Ajusta al periodo de tus archivos (p.ej., pedidos2.txt día 22..27)
        YearMonth periodo = YearMonth.of(2025, 11);
        Instant tStart = ZonedDateTime.of(2025, 11, 01, 10, 0, 0, 0, ZoneOffset.UTC).toInstant();
        Instant tEnd   = ZonedDateTime.of(2025, 11, 07, 23, 59, 59, 0, ZoneOffset.UTC).toInstant();

        // ====== Carga de datos ======
        Map<String, Aeropuerto> aeropuertos = UtilArchivos.leerAeropuertos(aeropTxt);
        List<Vuelo> vuelos = UtilArchivos.leerVuelos(vuelosTxt);
        List<Pedido> pedidos = UtilArchivos.leerPedidos(pedidosTxt, periodo);
        List<CancellationRecord> cancels = java.nio.file.Files.exists(cancelTxt)
                ? UtilArchivos.leerCancelaciones(cancelTxt, aeropuertos, periodo)
                : Collections.emptyList();

        // ====== Backend autoritativo ======
        GrafoVuelos grafo = new GrafoVuelos(vuelos);
        AppPlanificador app = new AppPlanificador(aeropuertos, grafo);

        // Compilar plan semanal (multi-origen: SPIM/EBCI/UBBB según continente)
        String ver = app.compileWeekly(tStart, tEnd, pedidos, periodo);
        System.out.println("▶ Plan compilado (version " + ver + ")");
        System.out.println();

        // Aplicar cancelaciones si existen (recortan timeline y marcan pendientes)
        if (!cancels.isEmpty()) {
            var sum = app.applyCancelations(cancels);
            System.out.println("⚠ Cancelaciones aplicadas → versión " + sum.newVersion() +
                    " | pedidos afectados: " + sum.afectados());
            // (Opcional) replanificar inmediatamente:
            var rep = app.replanPending(tStart.plus(Duration.ofHours(12)), periodo);
            System.out.println("↻ Replanificación parcial → versión " + rep.newVersion() +
                    " | replanificados: " + rep.replanificados());
            System.out.println();
        }

        // Para KPIs de vuelos necesitamos capacidad por instancia
        Map<String,Integer> instanceCap = buildInstanceCap(grafo, aeropuertos, tStart, tEnd);

        // ====== Loop de reproducción ======
        Instant cursor = tStart;
        Instant prev = cursor;

        while (!cursor.isAfter(tEnd)) {
            // Slice de eventos nuevos
            List<Event> nuevos = app.eventsBetween(prev, cursor);
            // Estado en T
            EstadosTemporales st = app.stateAt(cursor);

            // Pintar dashboard
            printFrame(cursor, nuevos, st, aeropuertos, instanceCap);

            // Colapso → cortar
            if (st.getCollapse() != null && st.getCollapse().isCollapsed()) {
                System.out.println();
                System.out.println("⛔ COLAPSO @ " + TS.format(st.getCollapse().getAt())
                        + " causa=" + st.getCollapse().getCause()
                        + (st.getCollapse().getAirportId()!=null? " airport="+st.getCollapse().getAirportId():"")
                        + (st.getCollapse().getFlightInstanceId()!=null? " flight="+st.getCollapse().getFlightInstanceId():""));
                break;
            }

            // Avanzar
            prev = cursor;
            cursor = cursor.plus(Duration.ofMinutes(tickMinutes));
            Thread.sleep(sleepMs);
        }

        System.out.println();
        System.out.println("✔ Fin de reproducción.");
    }

    // ================== Helpers de UI/Consola ==================

    private static void printFrame(Instant t, List<Event> eventos, EstadosTemporales st,
                                   Map<String, Aeropuerto> aeropuertos, Map<String,Integer> instCap) {
        String title = "=== " + TS.format(t) + " ===";
        System.out.println(title);

        // Eventos nuevos (resumen por tipo)
        Map<Event.Type, Long> byType = eventos.stream()
                .collect(Collectors.groupingBy(e -> e.type, Collectors.counting()));
        if (eventos.isEmpty()) {
            System.out.println("  (sin eventos nuevos)");
        } else {
            System.out.println("  Eventos: " + byType.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey()+":"+e.getValue())
                    .collect(Collectors.joining("  ")));
            // Lista corta de eventos (máx 8)
            eventos.stream().limit(8).forEach(e -> {
                String who = e.pedidoId!=null? (" pedido="+e.pedidoId):"";
                String loc = e.airport!=null? (" ap="+e.airport):"";
                String fl  = e.flightInstanceId!=null? (" fi="+e.flightInstanceId):"";
                System.out.println("   • " + e.type + loc + fl + who + " @" + TS.format(e.ts));
            });
            if (eventos.size() > 8) System.out.println("   … +" + (eventos.size()-8) + " más");
        }

        // Top almacenes (excluye ∞ para ranking, pero los muestra)
        Map<String,Integer> occA = new HashMap<>(st.getOcupacionAeropuerto());
        // Orden por ocupación desc
        List<Map.Entry<String,Integer>> topA = occA.entrySet().stream()
                .sorted((a,b)->Integer.compare(b.getValue(), a.getValue()))
                .limit(6).collect(Collectors.toList());

        System.out.println("  Almacenes TOP:");
        for (Map.Entry<String,Integer> e : topA) {
            Aeropuerto ap = aeropuertos.get(e.getKey());
            boolean inf = (ap != null && ap.isInfiniteSource());
            int cap = (ap != null ? ap.getCapacidadAlmacen() : 0);
            String capStr = inf ? "∞" : (cap > 0 ? String.valueOf(cap) : "?");
            String pct = (!inf && cap > 0) ? String.format(" (%.0f%%)", 100.0 * e.getValue() / cap) : "";
            System.out.printf("   - %s: %d / %s%s%n", e.getKey(), e.getValue(), capStr, pct);
        }
        if (topA.isEmpty()) System.out.println("   - (sin ocupaciones)");

        // Top vuelos por ocupación (%)
        Map<String,Integer> occF = new HashMap<>(st.getOcupacionVueloInstance());
        List<Map.Entry<String,Integer>> topF = occF.entrySet().stream()
                .sorted((a,b)->Integer.compare(b.getValue(), a.getValue()))
                .limit(6).collect(Collectors.toList());

        System.out.println("  Vuelos TOP:");
        for (Map.Entry<String,Integer> e : topF) {
            int cap = instCap.getOrDefault(e.getKey(), 0);
            String pct = (cap > 0) ? String.format(" (%.0f%%)", 100.0 * e.getValue() / cap) : "";
            System.out.printf("   - %s: %d / %s%s%n", e.getKey(), e.getValue(), cap>0?String.valueOf(cap):"?", pct);
        }
        if (topF.isEmpty()) System.out.println("   - (sin ocupaciones)");

        // Estado de algunos pedidos
        Map<String,String> estP = st.getEstadoPedido();
        if (!estP.isEmpty()) {
            System.out.println("  Pedidos (muestra): " + estP.entrySet().stream().limit(8)
                    .map(e -> e.getKey()+":"+e.getValue()).collect(Collectors.joining("  ")));
        }

        System.out.println(); // línea en blanco
    }

    private static Map<String,Integer> buildInstanceCap(GrafoVuelos grafo, Map<String,Aeropuerto> aeropuertos,
                                                        Instant tStart, Instant tEnd) {
        List<FlightInstance> slots = grafo.expandirSlots(tStart, tEnd, aeropuertos);
        return slots.stream().collect(Collectors.toMap(fi -> fi.instanceId, fi -> fi.capacidad, (a,b)->a));
    }
}

