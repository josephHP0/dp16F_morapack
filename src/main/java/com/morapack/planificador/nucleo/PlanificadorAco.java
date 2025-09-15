package com.morapack.planificador.nucleo;

import com.morapack.planificador.dominio.*;
import com.morapack.planificador.util.UtilArchivos;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class PlanificadorAco {

    // ==== NUEVO: Región por IATA calculada desde el archivo de aeropuertos ====
    private static final Map<String,String> REGION_BY_IATA = new HashMap<>();

    // ==== NUEVO: País -> Región (lista mínima para los datos del proyecto) ====
    private static final Set<String> AMERICAS = new HashSet<>(Arrays.asList(
        "Colombia","Ecuador","Venezuela","Brasil","Perú","Bolivia","Chile",
        "Argentina","Paraguay","Uruguay","Mexico","Estados Unidos","Canada"));

    private static final Set<String> EUROPE = new HashSet<>(Arrays.asList(
        "Albania","Alemania","Austria","Belgica","Bielorrusia","Bulgaria",
        "Checa","Croacia","Dinamarca","Holanda","España","Francia","Italia",
        "Portugal","Suecia","Noruega","Polonia"));

    private static final Set<String> ASIA = new HashSet<>(Arrays.asList(
        "India","Siria","Arabia Saudita","Emiratos A.U","Afganistan","Oman",
        "Yemen","Pakistan","Azerbaiyan","Jordania","China","Japón",
        "Corea del Sur","Singapur"));

    // ==== Hubs por región (heurística simple por prefijo IATA) ====
    private static final Map<String,String> HUBS = Map.of(
            "SPIM","Americas",  // Lima
            "EBCI","Europe",    // Bruselas
            "UBBB","Asia"       // Bakú
    );

    static String regionDe(String iata) {
        String r = REGION_BY_IATA.get(iata);
        if (r != null) return r;
        // Fallback heurístico por prefijo si no estuviera en el mapa
        if (iata.startsWith("S")) return "Americas";
        if (iata.startsWith("O") || iata.startsWith("V") || (iata.startsWith("U") && iata.startsWith("UB"))) return "Asia";
        if (iata.startsWith("E") || iata.startsWith("L") || iata.startsWith("U")) return "Europe";
        return "Europe";
    }

    static String hubParaDestino(String destino) {
        switch (regionDe(destino)) {
            case "Americas": return "SPIM";   // Lima
            case "Europe":   return "EBCI";   // Bruselas
            case "Asia":     return "UBBB";   // Bakú
            default:         return "EBCI";
        }
    }

    static double slaHoras(String hub, String dest) {
        double base = regionDe(hub).equals(regionDe(dest)) ? 48.0 : 72.0;
        return Math.max(0, base - 2.0); // ventana de recojo de 2h
    }
    // ==== Construcción de ruta por una hormiga (AÑADÍ ITINERARIO) ====
    private static Ruta construirRuta(String hub, String destino,
                                      GrafoVuelos grafo,
                                      double[] tau, double[] heuristica,
                                      int pasosMax, double presupuestoHoras,
                                      Map<Integer,Integer> capacidadRestante,
                                      Map<String,Aeropuerto> aeropuertos,
                                      Random rnd) {
        Set<String> visitados = new HashSet<>();
        visitados.add(hub);
        String actual = hub;
        double horas = 0.0;
        Ruta ruta = new Ruta();
        ruta.nodos.add(hub);

        for (int s=0; s<pasosMax && horas <= presupuestoHoras; s++) {
            if (actual.equals(destino)) break;

            var aristas = grafo.aristasDesde(actual);
            if (aristas.isEmpty()) break;

            // Filtrar candidatos por capacidad de vuelo y almacén destino (si al llegar es el final)
            List<GrafoVuelos.Arista> candidatos = new ArrayList<>();
            List<Double> pesos = new ArrayList<>();

            for (var e : aristas) {
                String nextIata = e.b;
                Aeropuerto next = aeropuertos.get(nextIata);

                // almacén lleno
                if (next != null && next.cargaEntrante >= next.capacidadAlmacen) continue;

                Integer cap = capacidadRestante.get(e.vueloId);
                if (cap == null || cap <= 0) continue; // vuelo sin capacidad

                // no repetir nodos (evitar ciclos)
                if (visitados.contains(nextIata)) continue;

                // Score ACO
                double score = Math.pow(tau[e.vueloId], 1.0) * Math.pow(heuristica[e.vueloId], 2.0);
                candidatos.add(e);
                pesos.add(score);
            }

            if (candidatos.isEmpty()) break;

            // Ruleta
            double suma = pesos.stream().mapToDouble(d->d).sum();
            double r = rnd.nextDouble() * (suma<=0?1.0:suma);
            double acc = 0.0; int idx = 0;
            for (int i=0;i<candidatos.size();i++) {
                acc += (suma<=0? (1.0/candidatos.size()) : pesos.get(i));
                if (r <= acc) { idx = i; break; }
            }
            var elegido = candidatos.get(idx);

            // === NUEVO: registrar tramo en itinerario ===
            ruta.vuelosUsados.add(elegido.vueloId);
            ruta.itinerario.add(elegido.a + "->" + elegido.b + String.format(java.util.Locale.US, " (%.1fh)", elegido.horas));
            horas += elegido.horas;
            actual = elegido.b;
            ruta.nodos.add(actual);
            visitados.add(actual);
            if (actual.equals(destino)) break;
        }

        // Valida contra presupuesto (SLA-2h)
        if (!actual.equals(destino) || horas > presupuestoHoras) return null;
        ruta.horasTotales = horas;
        return ruta;
    }

    // ==== Planificación por ACO ====
    public static List<Asignacion> planificarConAco(
            Map<String,Aeropuerto> aeropuertos,
            List<Vuelo> vuelos,
            List<Pedido> pedidos,
            ParametrosAco p,
            long semillaAleatoria
    ) {
        GrafoVuelos grafo = new GrafoVuelos(vuelos);
        double[] tau = new double[vuelos.size()];
        double[] heur = new double[vuelos.size()];
        Arrays.fill(tau, 0.1);
        for (Vuelo v : vuelos) heur[v.id] = 1.0 / Math.max(0.001, v.horasDuracion);

        Map<Integer,Integer> capRest = new HashMap<>();
        for (Vuelo v : vuelos) capRest.put(v.id, v.capacidad);

        Random rnd = new Random(semillaAleatoria);
        List<Asignacion> resultado = new ArrayList<>();

        for (Pedido ped : pedidos) {
            String hub = hubParaDestino(ped.destinoIata);
            double presupuesto = slaHoras(hub, ped.destinoIata);
            Ruta mejor = null;

            for (int it=0; it<p.iteraciones; it++) {
                Ruta mejorIter = null;
                for (int h=0; h<p.hormigas; h++) {
                    Ruta r = construirRuta(hub, ped.destinoIata, grafo, tau, heur, p.pasosMax, presupuesto, capRest, aeropuertos, rnd);
                    if (r != null && (mejorIter==null || r.horasTotales < mejorIter.horasTotales)) mejorIter = r;
                }
                // evaporación
                for (int i=0;i<tau.length;i++) tau[i] *= (1.0 - p.rho);
                // refuerzo
                if (mejorIter != null) {
                    double dep = p.Q / (1.0 + mejorIter.horasTotales);
                    for (int fid : mejorIter.vuelosUsados) tau[fid] += dep;
                    if (mejor == null || mejorIter.horasTotales < mejor.horasTotales) mejor = mejorIter;
                }
            }

            Asignacion asg = new Asignacion();
            asg.pedido = ped; asg.hubOrigen = hub; asg.ruta = mejor;
            asg.paquetesAsignados = 0; asg.paquetesPendientes = ped.paquetes;

            if (mejor == null) { resultado.add(asg); continue; }

            // cuello de botella: vuelos + almacén destino
            int cuelloVuelo = Integer.MAX_VALUE;
            for (int fid : mejor.vuelosUsados) cuelloVuelo = Math.min(cuelloVuelo, capRest.getOrDefault(fid, 0));
            Aeropuerto apDest = aeropuertos.get(ped.destinoIata);
            int remAlmacen = apDest==null?0: Math.max(0, apDest.capacidadAlmacen - apDest.cargaEntrante);
            int asignable = Math.max(0, Math.min(ped.paquetes, Math.min(cuelloVuelo, remAlmacen)));

            if (asignable > 0) {
                for (int fid : mejor.vuelosUsados) capRest.put(fid, capRest.get(fid) - asignable);
                if (apDest != null) apDest.cargaEntrante += asignable;
                asg.paquetesAsignados = asignable;
                asg.paquetesPendientes = ped.paquetes - asignable;
            }
            resultado.add(asg);
        }
        return resultado;
    }

    // ==== MAIN ====
    public static void main(String[] args) throws Exception {
        // CLI estilo: --aeropuertos data/aeropuertos.txt --vuelos data/vuelos.txt --pedidos data/pedidos.txt --salida plan_asignacion.csv
        Map<String,String> arg = Arrays.stream(args)
                .map(s -> s.split("=", 2))
                .filter(a -> a.length==2 && a[0].startsWith("--"))
                .collect(Collectors.toMap(a->a[0].substring(2), a->a[1]));

        Path aeropuertosPath = Paths.get(arg.getOrDefault("aeropuertos", "data/aeropuertos.txt"));
        Path vuelosPath      = Paths.get(arg.getOrDefault("vuelos", "data/vuelos.txt"));
        Path pedidosPath     = arg.containsKey("pedidos") ? Paths.get(arg.get("pedidos")) : null;
        Path salidaPath      = Paths.get(arg.getOrDefault("salida", "plan_asignacion.csv"));

        Map<String,Aeropuerto> aeropuertos = UtilArchivos.cargarAeropuertos(aeropuertosPath);
        if (aeropuertos.isEmpty()) throw new IllegalArgumentException("No se cargaron aeropuertos.");
        List<Vuelo> vuelos = UtilArchivos.cargarVuelos(vuelosPath, aeropuertos.keySet());
        if (vuelos.isEmpty()) throw new IllegalArgumentException("No se cargaron vuelos válidos.");

        List<Pedido> pedidos = (pedidosPath!=null && Files.exists(pedidosPath))
                ? UtilArchivos.cargarPedidos(pedidosPath, aeropuertos.keySet())
                : UtilArchivos.generarPedidosSinteticos(aeropuertos.keySet(), HUBS.keySet(), 40, 7L);

        // Parámetros ACO (opcionalmente puedes exponerlos por CLI también)
        ParametrosAco p = new ParametrosAco();
        if (arg.containsKey("alpha")) p.alpha = Double.parseDouble(arg.get("alpha"));
        if (arg.containsKey("beta")) p.beta = Double.parseDouble(arg.get("beta"));
        if (arg.containsKey("rho")) p.rho = Double.parseDouble(arg.get("rho"));
        if (arg.containsKey("Q")) p.Q = Double.parseDouble(arg.get("Q"));
        if (arg.containsKey("hormigas")) p.hormigas = Integer.parseInt(arg.get("hormigas"));
        if (arg.containsKey("iteraciones")) p.iteraciones = Integer.parseInt(arg.get("iteraciones"));
        if (arg.containsKey("pasosMax")) p.pasosMax = Integer.parseInt(arg.get("pasosMax"));

        List<Asignacion> plan = planificarConAco(aeropuertos, vuelos, pedidos, p, 7L);
        UtilArchivos.escribirPlanCsv(salidaPath, plan);

        long conAsign = plan.stream().filter(a -> a.paquetesAsignados>0).map(a -> a.pedido.id).distinct().count();
        int pkSolic = pedidos.stream().mapToInt(ped -> ped.paquetes).sum();
        int pkAsig = plan.stream().mapToInt(a -> a.paquetesAsignados).sum();
        int pkPend = pkSolic - pkAsig;

        System.out.println("=== Resumen de planificación (ACO) ===");
        System.out.println("Órdenes totales: " + pedidos.size());
        System.out.println("Órdenes con asignación: " + conAsign);
        System.out.println("Paquetes solicitados: " + pkSolic);
        System.out.println("Paquetes asignados: " + pkAsig);
        System.out.println("Paquetes pendientes: " + pkPend);
        System.out.println("Plan escrito en: " + salidaPath.toAbsolutePath());
    }
}
