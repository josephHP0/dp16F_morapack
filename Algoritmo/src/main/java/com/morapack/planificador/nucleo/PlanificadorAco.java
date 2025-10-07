package com.morapack.planificador.nucleo;

import com.morapack.planificador.dominio.*;
import com.morapack.planificador.util.UtilArchivos;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Planificador ACO con:
 * - Simulación semanal (día de inicio + número de días).
 * - Soporte de cancelaciones por día (dd.ORIGEN-DESTINO-HH:MM).
 */
public class PlanificadorAco {

    // Región por IATA calculada desde el archivo de aeropuertos
    private static final Map<String,String> REGION_BY_IATA = new HashMap<>();

    // Hubs por región
    public static final Map<String,String> HUBS = Map.of(
            "SPIM","AM",  // Lima
            "EBCI","EU",  // Bruselas
            "UBBB","AS"   // Bakú
    );

    // ===== Tipos/auxiliares para cancelaciones =====
    /** Registro de una cancelación concreta: día, origen, destino, salida (minutos). */
    static final class Cancelacion {
        final int dia;            // 1..31
        final String origen;      // IATA
        final String destino;     // IATA
        final int salidaMin;      // minutos desde 00:00

        Cancelacion(int dia, String origen, String destino, int salidaMin) {
            this.dia = dia;
            this.origen = origen;
            this.destino = destino;
            this.salidaMin = salidaMin;
        }
    }

    /** Carga cancelaciones desde archivo (líneas: dd.ORIGEN-DESTINO-HH:MM). */
    public static List<Cancelacion> cargarCancelaciones(Path path) throws IOException {
        List<Cancelacion> out = new ArrayList<>();
        if (path == null || !Files.exists(path)) return out;
        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                // dd.ORIGEN-DESTINO-HH:MM
                // Ej: 03.SKBO-SEQM-14:22
                String[] partes = line.split("\\.");
                if (partes.length != 2) continue;
                int dia = Integer.parseInt(partes[0]);
                String[] segs = partes[1].split("-");
                if (segs.length != 3) continue;
                String origen = segs[0].trim();
                String destino = segs[1].trim();
                String hhmm = segs[2].trim();
                int salidaMin = hhmmAmin(hhmm);
                out.add(new Cancelacion(dia, origen, destino, salidaMin));
            }
        }
        return out;
    }

    private static int hhmmAmin(String hhmm) {
        String[] p = hhmm.split(":");
        int h = Integer.parseInt(p[0]);
        int m = Integer.parseInt(p.length > 1 ? p[1] : "0");
        return h * 60 + m;
    }

    /** Dado el listado de vuelos y cancelaciones, construye: día -> set(ids de vuelo cancelados ese día). */
    public static Map<Integer, Set<Integer>> mapearCancelacionesAIdsPorDia(
            List<Vuelo> vuelos,
            List<Cancelacion> cancelaciones
    ) {
        Map<Integer, Set<Integer>> porDia = new HashMap<>();
        if (cancelaciones == null || cancelaciones.isEmpty()) return porDia;

        // Indexar por (origen, destino, salidaMin) -> lista ids
        Map<String, List<Integer>> idx = new HashMap<>();
        for (Vuelo v : vuelos) {
            String key = v.origen + "|" + v.destino + "|" + v.salidaMin;
            idx.computeIfAbsent(key, k -> new ArrayList<>()).add(v.id);
        }

        for (Cancelacion c : cancelaciones) {
            String key = c.origen + "|" + c.destino + "|" + c.salidaMin;
            List<Integer> ids = idx.getOrDefault(key, Collections.emptyList());
            if (!ids.isEmpty()) {
                porDia.computeIfAbsent(c.dia, k -> new HashSet<>()).addAll(ids);
            }
        }
        return porDia;
    }

    // ====== utilidades de región / SLA (como tenías) ======
    static String regionDe(String iata) {
        return REGION_BY_IATA.getOrDefault(iata, "EU");
    }

    static String hubParaDestino(String destino) {
        String region = regionDe(destino);
        switch (region) {
            case "AM": return "SPIM";   // Lima
            case "EU": return "EBCI";   // Bruselas
            case "AS": return "UBBB";   // Bakú
            default:   return "EBCI";
        }
    }

    static double slaHoras(String hub, String dest) {
        // 48h mismo continente, 72h diferente continente; pequeño ajuste por recojo
        double base = regionDe(hub).equals(regionDe(dest)) ? 48.0 : 72.0;
        double recojoHoras = regionDe(hub).equals(regionDe(dest)) ? 2.0 : 1.0;
        return Math.max(0, base - recojoHoras);
    }

    // ====== Construcción de ruta por hormiga — AHORA CON CANCELACIONES POR DÍA ======
    private static Ruta construirRuta(String hub, String destino,
                                      GrafoVuelos grafo,
                                      double[] tau, double[] heuristica,
                                      int pasosMax, double presupuestoHoras,
                                      Map<Integer,Integer> capacidadRestante,
                                      Map<String,Aeropuerto> aeropuertos,
                                      Map<Integer,Vuelo> vuelosPorId,
                                      int diaInicio, int horaInicio, int minutoInicio,
                                      Map<Integer, Set<Integer>> cancelByDay,
                                      Random rnd) {
        Set<String> visitados = new HashSet<>();
        visitados.add(hub);
        String actual = hub;
        double horas = 0.0;
        Ruta ruta = new Ruta();
        ruta.nodos.add(hub);

        for (int s = 0; s < pasosMax && horas <= presupuestoHoras; s++) {
            if (actual.equals(destino)) break;

            var aristas = grafo.aristasDesde(actual);
            if (aristas.isEmpty()) break;

            List<GrafoVuelos.Arista> candidatos = new ArrayList<>();
            List<Double> pesos = new ArrayList<>();

            for (var e : aristas) {
                String nextIata = e.b;
                Aeropuerto next = aeropuertos.get(nextIata);
                if (next == null) continue;

                Vuelo vuelo = vuelosPorId.get(e.vueloId);
                if (vuelo == null) continue;

                if (visitados.contains(nextIata)) continue; // evita ciclos simples
                Integer capRest = capacidadRestante.getOrDefault(e.vueloId, 0);
                if (capRest <= 0) continue;

                // Tiempo transcurrido absoluto desde el inicio del pedido
                int horasDelDia = (horaInicio + (int) Math.floor(horas)) % 24;
                int minutosDelDia = (minutoInicio + (int) Math.round((horas % 1.0) * 60)) % 60;

                // Calcular día real del mes en este salto
                int diasTranscurridos = (horaInicio * 60 + minutoInicio + (int) Math.round(horas * 60)) / (24 * 60);
                int diaActual = diaInicio + diasTranscurridos;
                if (diaActual > 31) break; // fuera de mes

                // Si el vuelo está cancelado ese día, no es candidato
                if (cancelByDay != null && !cancelByDay.isEmpty()) {
                    Set<Integer> cancelsHoy = cancelByDay.getOrDefault(diaActual, Collections.emptySet());
                    if (cancelsHoy.contains(e.vueloId)) {
                        continue;
                    }
                }

                // Ajustar espera: si ya pasó la hora de salida, se espera al siguiente día
                int tiempoActualEnMinutos = horasDelDia * 60 + minutosDelDia;
                int salida = vuelo.salidaMin;
                int minutosEspera;
                if (salida >= tiempoActualEnMinutos) {
                    minutosEspera = salida - tiempoActualEnMinutos;
                } else {
                    // espera hasta mañana
                    minutosEspera = (24 * 60 - tiempoActualEnMinutos) + salida;
                    // ojo: el vuelo saldrá el *díaActual+1*, validar cancelación también mañana
                    int diaManiana = diaActual + 1;
                    if (cancelByDay != null && !cancelByDay.isEmpty()) {
                        Set<Integer> cancelsManiana = cancelByDay.getOrDefault(diaManiana, Collections.emptySet());
                        if (cancelsManiana.contains(e.vueloId)) {
                            // si mañana también está cancelado, descartamos
                            continue;
                        }
                    }
                    if (diaManiana > 31) continue;
                }

                // Score ACO
                double tauVal = tau[e.vueloId];
                double heurVal = heuristica[e.vueloId];
                double eps = 1e-9;
                double alpha = 1.0, beta = 2.0;
                double score = Math.pow(Math.max(tauVal, eps), alpha) * Math.pow(Math.max(heurVal, eps), beta);
                candidatos.add(e);
                pesos.add(score);
            }

            if (candidatos.isEmpty()) break;

            // Ruleta proporcional
            double suma = pesos.stream().mapToDouble(d -> d).sum();
            double r = rnd.nextDouble() * (suma <= 0 ? 1.0 : suma);
            double acc = 0.0;
            int idx = candidatos.size() - 1; // fallback
            for (int i = 0; i < candidatos.size(); i++) {
                acc += (suma <= 0 ? (1.0 / candidatos.size()) : pesos.get(i));
                if (r <= acc) { idx = i; break; }
            }
            GrafoVuelos.Arista elegido = candidatos.get(idx);
            Vuelo v = vuelosPorId.get(elegido.vueloId);
            if (v == null) break;

            // Aplicar tiempos (espera + vuelo)
            int horasDelDia = (horaInicio + (int) Math.floor(horas)) % 24;
            int minutosDelDia = (minutoInicio + (int) Math.round((horas % 1.0) * 60)) % 60;
            int tiempoActualEnMinutos = horasDelDia * 60 + minutosDelDia;

            int salida = v.salidaMin;
            double esperaHoras;
            if (salida >= tiempoActualEnMinutos) {
                esperaHoras = (salida - tiempoActualEnMinutos) / 60.0;
            } else {
                esperaHoras = ((24 * 60 - tiempoActualEnMinutos) + salida) / 60.0;
            }

            horas += esperaHoras + elegido.horas;
            capacidadRestante.put(elegido.vueloId, capacidadRestante.getOrDefault(elegido.vueloId, 0) - 1);

            ruta.vuelosUsados.add(elegido.vueloId);
            ruta.itinerario.add(actual + "->" + elegido.b + " (" + String.format(Locale.US, "%.2f", elegido.horas) + "h)");
            actual = elegido.b;
            ruta.nodos.add(actual);

            if (actual.equals(destino)) break;
        }

        if (!actual.equals(destino)) return null;
        ruta.horasTotales = horas;
        return ruta;
    }

    // ====== Planificador ACO (versión original) ======
    public static List<Asignacion> planificarConAco(
            Map<String,Aeropuerto> aeropuertos,
            List<Vuelo> vuelos,
            List<Pedido> pedidos,
            ParametrosAco p,
            long semillaAleatoria
    ) {
        return planificarConAco(aeropuertos, vuelos, pedidos, p, semillaAleatoria,
                /*cancelByDay*/ Collections.emptyMap(), /*diaInicioPlan*/ 1);
    }

    // ====== Planificador ACO con cancelaciones (sobre carga) ======
    public static List<Asignacion> planificarConAco(
            Map<String,Aeropuerto> aeropuertos,
            List<Vuelo> vuelos,
            List<Pedido> pedidos,
            ParametrosAco p,
            long semillaAleatoria,
            Map<Integer, Set<Integer>> cancelByDay,
            int diaInicioPlan
    ) {
        // Cargar regiones por IATA
        REGION_BY_IATA.clear();
        for (Aeropuerto ap : aeropuertos.values()) {
            if (ap.continente != null && !ap.continente.isBlank()) {
                REGION_BY_IATA.put(ap.codigo, ap.continente);
            }
        }

        // Crear mapa de vuelos por ID para acceso rápido
        Map<Integer, Vuelo> vuelosPorId = new HashMap<>();
        for (Vuelo v : vuelos) vuelosPorId.put(v.id, v);

        GrafoVuelos grafo = new GrafoVuelos(vuelos);
        double[] tau = new double[vuelos.size()];
        double[] heur = new double[vuelos.size()];
        Arrays.fill(tau, 0.1);

        // Heurística basada en distancia, duración y capacidad disponible (inicial)
        for (Vuelo v : vuelos) {
            Aeropuerto a1 = aeropuertos.get(v.origen);
            Aeropuerto a2 = aeropuertos.get(v.destino);
            double heurVal = 1e-6;
            if (a1 != null && a2 != null) {
                double dist = UtilArchivos.distanciaKm(a1, a2);
                double durHoras = v.horasDuracion;
                if (dist <= 0 || Double.isInfinite(dist) || Double.isNaN(dist)) dist = 1e6;
                // Simple (puedes refinar): 1 / (dist * dur) y ligero boost por capacidad nominal
                heurVal = 1.0 / (dist * Math.max(0.1, durHoras));
                heurVal *= Math.max(1.0, v.capacidad / 100.0);
            }
            heur[v.id] = heurVal;
        }

        // Capacidad restante (reset por planificación)
        Map<Integer,Integer> capRest = new HashMap<>();
        for (Vuelo v : vuelos) capRest.put(v.id, v.capacidad);

        List<Asignacion> resultado = new ArrayList<>();
        Random rnd = new Random(semillaAleatoria);

        // Limpiar ocupación de aeropuertos para esta corrida
        for (Aeropuerto ap : aeropuertos.values()) {
            ap.ocupacionPorMinuto.clear();
        }

        for (Pedido ped : pedidos) {
            String hub = hubParaDestino(ped.destinoIata);
            double presupuesto = slaHoras(hub, ped.destinoIata);

            Ruta mejor = null;
            for (int it=0; it<p.iteraciones; it++) {
                for (int h=0; h<p.hormigas; h++) {
                    Ruta r = construirRuta(
                            hub, ped.destinoIata, grafo, tau, heur, p.pasosMax, presupuesto,
                            capRest, aeropuertos, vuelosPorId,
                            ped.dia, ped.hora, ped.minuto,
                            cancelByDay,
                            rnd
                    );
                    if (r != null && (mejor==null || r.horasTotales < mejor.horasTotales)) mejor = r;
                }
                // evaporación
                for (int i=0;i<tau.length;i++) tau[i] *= (1.0 - p.rho);
                // refuerzo
                if (mejor != null) {
                    double dep = p.Q / (1.0 + mejor.horasTotales);
                    for (int fid : mejor.vuelosUsados) tau[fid] += dep;
                }
            }

            int paquetesRestantes = ped.paquetes;
            // Permitir hasta 3 rutas alternativas por pedido
            int intentosRuta = 0;
            while (paquetesRestantes > 0 && mejor != null && intentosRuta < 3) {
                intentosRuta++;
                Asignacion asg = new Asignacion();
                asg.pedido = ped;
                asg.hubOrigen = hub;
                asg.ruta = mejor;
                asg.paquetesAsignados = 0;
                asg.paquetesPendientes = paquetesRestantes;

                // cuello de botella: vuelos + almacén destino (ocupa 120 minutos al llegar)
                int cuelloVuelo = Integer.MAX_VALUE;
                for (int fid : mejor.vuelosUsados) {
                    cuelloVuelo = Math.min(cuelloVuelo, capRest.getOrDefault(fid, 0));
                }
                Aeropuerto apDest = aeropuertos.get(ped.destinoIata);
                int cuelloAlmacen = Integer.MAX_VALUE;
                if (apDest != null) {
                    int minutoLlegada = ped.dia * 24 * 60 + ped.hora * 60 + ped.minuto + (int)(mejor.horasTotales * 60);
                    int ocupMax = 0;
                    for (int m = minutoLlegada; m < minutoLlegada + 120; m++) {
                        ocupMax = Math.max(ocupMax, apDest.ocupacionPorMinuto.getOrDefault(m,0));
                    }
                    cuelloAlmacen = Math.max(0, apDest.capacidad - ocupMax);
                }
                int asignable = Math.max(0, Math.min(paquetesRestantes, Math.min(cuelloVuelo, cuelloAlmacen)));

                if (asignable > 0) {
                    for (int fid : mejor.vuelosUsados) {
                        capRest.put(fid, capRest.get(fid) - asignable);
                    }
                    if (apDest != null) {
                        int minutoLlegada = ped.dia * 24 * 60 + ped.hora * 60 + ped.minuto + (int)(mejor.horasTotales * 60);
                        for (int m = minutoLlegada; m < minutoLlegada + 120; m++) {
                            apDest.ocupacionPorMinuto.put(m, apDest.ocupacionPorMinuto.getOrDefault(m, 0) + asignable);
                        }
                    }
                    asg.paquetesAsignados = asignable;
                    asg.paquetesPendientes = paquetesRestantes - asignable;
                    paquetesRestantes -= asignable;
                }

                resultado.add(asg);

                // Si quedan paquetes, busca otra mejor ruta (pocas iteraciones)
                if (paquetesRestantes > 0) {
                    Ruta mejorAlt = null;
                    double mejorHoras = Double.POSITIVE_INFINITY;
                    for (int it=0; it<5; it++) {
                        for (int h=0; h<15; h++) {
                            Ruta r = construirRuta(
                                    hub, ped.destinoIata, grafo, tau, heur, p.pasosMax, presupuesto,
                                    capRest, aeropuertos, vuelosPorId,
                                    ped.dia, ped.hora, ped.minuto,
                                    cancelByDay,
                                    rnd
                            );
                            if (r != null && r.horasTotales < mejorHoras) {
                                mejorHoras = r.horasTotales;
                                mejorAlt = r;
                            }
                        }
                    }
                    if (mejorHoras == Double.POSITIVE_INFINITY || mejorAlt == null) break;
                    mejor = mejorAlt;
                }
            }
        }
        return resultado;
    }

    // ====== Simulador semanal ======
    /**
     * Simula una semana (o N días) planificando día a día.
     * - Filtra pedidos del día D.
     * - Aplica cancelaciones del día D (Map<Integer,Set<vueloId>>).
     * - Resetea la capacidad de vuelos por día (operación diaria).
     * - Devuelve el plan consolidado.
     */
    public static List<Asignacion> simularSemana(
            Map<String, Aeropuerto> aeropuertos,
            List<Vuelo> vuelos,
            List<Pedido> pedidos,
            ParametrosAco parametros,
            Path archivoCancelaciones,
            int diaInicio,            // ej. 1
            int numeroDias,          // ej. 7
            long semilla
    ) throws IOException {

        // Cargar cancelaciones y mapear a ids por día
        List<Cancelacion> cancels = cargarCancelaciones(archivoCancelaciones);
        Map<Integer, Set<Integer>> cancelByDay = mapearCancelacionesAIdsPorDia(vuelos, cancels);

        List<Asignacion> consolidado = new ArrayList<>();

        // Ordenar pedidos por (dia, hora, minuto) por prolijidad
        pedidos.sort(Comparator
                .comparingInt((Pedido p) -> p.dia)
                .thenComparingInt(p -> p.hora)
                .thenComparingInt(p -> p.minuto));

        for (int d = 0; d < numeroDias; d++) {
            int diaSim = diaInicio + d;
            if (diaSim > 31) break;

            // Pedidos del día
            List<Pedido> pedidosDelDia = new ArrayList<>();
            for (Pedido p : pedidos) if (p.dia == diaSim) pedidosDelDia.add(p);

            if (pedidosDelDia.isEmpty()) continue;

            // IMPORTANTE: limpiar ocupación por minuto para la corrida del día
            for (Aeropuerto ap : aeropuertos.values()) ap.ocupacionPorMinuto.clear();

            // Planificar para ese día (aplicando únicamente cancelaciones del día 'diaSim')
            Map<Integer, Set<Integer>> cancelSoloHoy = new HashMap<>();
            if (cancelByDay.containsKey(diaSim)) {
                cancelSoloHoy.put(diaSim, cancelByDay.get(diaSim));
            }

            List<Asignacion> planDelDia = planificarConAco(
                    aeropuertos,
                    clonarVuelosConCapacidad(vuelos), // capacidad diaria
                    pedidosDelDia,
                    parametros,
                    semilla + diaSim,
                    cancelSoloHoy,
                    diaSim // para consistencia en tiempos base
            );

            consolidado.addAll(planDelDia);
        }

        return consolidado;
    }

    /** Clona la lista de vuelos con la misma capacidad (reset diario). */
    private static List<Vuelo> clonarVuelosConCapacidad(List<Vuelo> vuelos) {
        List<Vuelo> copia = new ArrayList<>(vuelos.size());
        for (Vuelo v : vuelos) {
            copia.add(new Vuelo(
                    v.id, v.origen, v.destino, v.salidaMin, v.llegadaMin,
                    v.capacidad, (int) Math.round(v.horasDuracion * 60), v.esContinental
            ));
        }
        return copia;
    }
}
