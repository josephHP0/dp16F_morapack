package com.morapack.planificador.nucleo;

import com.morapack.planificador.dominio.*;
import com.morapack.planificador.util.UtilArchivos;
import java.util.*;

public class PlanificadorAco {

    // Región por IATA calculada desde el archivo de aeropuertos
    private static final Map<String,String> REGION_BY_IATA = new HashMap<>();

    // Hubs por región
    public static final Map<String,String> HUBS = Map.of(
            "SPIM","AM",  // Lima
            "EBCI","EU",  // Bruselas
            "UBBB","AS"   // Bakú
    );

    static String regionDe(String iata) {
        if (iata == null) return "EU";
        String r = REGION_BY_IATA.get(iata);
        if (r != null) return r;
        return "EU"; // Valor por defecto
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
        // 48h mismo continente, 72h diferente continente
        // Ajuste dinámico del tiempo de recojo basado en la distancia
        double base = regionDe(hub).equals(regionDe(dest)) ? 48.0 : 72.0;
        
        // Reducir menos tiempo para rutas intercontinentales
        double recojoHoras = regionDe(hub).equals(regionDe(dest)) ? 2.0 : 1.0;
        
        return Math.max(0, base - recojoHoras);
    }

    // Construcción de ruta por una hormiga
    private static Ruta construirRuta(String hub, String destino,
                                      GrafoVuelos grafo,
                                      double[] tau, double[] heuristica,
                                      int pasosMax, double presupuestoHoras,
                                      Map<Integer,Integer> capacidadRestante,
                                      Map<String,Aeropuerto> aeropuertos,
                                      int diaInicio, int horaInicio, int minutoInicio,
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

                // Solo filtra capacidad de almacén si es el destino final
                if (nextIata.equals(destino) && next.capacidad > 0 && next.cargaEntrante >= next.capacidad) continue;

                Integer cap = capacidadRestante.get(e.vueloId);
                if (cap == null || cap <= 0) continue;

                if (visitados.contains(nextIata)) continue;

                // Get corresponding flight
                Vuelo vuelo = null;
                for (Vuelo v : grafo.getVuelos()) {
                    if (v.id == e.vueloId) {
                        vuelo = v;
                        break;
                    }
                }
                if (vuelo == null) continue;

                // Check if this flight's time is compatible with current total hours
                // Convert horas to day, hour, minute
                int diasTranscurridos = (int)(horas / 24.0);
                int horasDelDia = (int)(horas % 24.0);
                int minutosDelDia = (int)((horas * 60.0) % 60.0);

                // Calculate actual departure time considering initial time
                int diaActual = diaInicio + diasTranscurridos;
                if (diaActual > 31) continue; // Past end of month
                
                // Calculate if this flight's departure time works with our current time
                int tiempoActualEnMinutos = horasDelDia * 60 + minutosDelDia;
                if (vuelo.salidaMin < tiempoActualEnMinutos) {
                    // Need to wait for tomorrow's flight
                    if (diaActual + 1 > 31) continue; // Would be past end of month
                    diasTranscurridos++;
                    tiempoActualEnMinutos = vuelo.salidaMin;
                }

                // Score ACO (puedes ajustar alpha/beta aquí si lo deseas)
                double tauVal = tau[e.vueloId];
                double heurVal = heuristica[e.vueloId];
                double eps = 1e-9;
                double alpha = 1.0, beta = 2.0; // puedes parametrizar
                double score = Math.pow(Math.max(tauVal, eps), alpha) * Math.pow(Math.max(heurVal, eps), beta);
                candidatos.add(e);
                pesos.add(score);
            }

            if (candidatos.isEmpty()) break;

            // Ruleta proporcional
            double suma = pesos.stream().mapToDouble(d -> d).sum();
            double r = rnd.nextDouble() * (suma <= 0 ? 1.0 : suma);
            double acc = 0.0;
            int idx = candidatos.size() - 1; // por defecto el último
            for (int i = 0; i < candidatos.size(); i++) {
                acc += (suma <= 0 ? (1.0 / candidatos.size()) : pesos.get(i));
                if (r <= acc) {
                    idx = i;
                    break;
                }
            }
            var elegido = candidatos.get(idx);

            // Registrar tramo en itinerario
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

    // Planificación por ACO
    public static List<Asignacion> planificarConAco(
            Map<String,Aeropuerto> aeropuertos,
            List<Vuelo> vuelos,
            List<Pedido> pedidos,
            ParametrosAco p,
            long semillaAleatoria
    ) {
        // Cargar regiones por IATA desde los aeropuertos
        REGION_BY_IATA.clear();
        for (Aeropuerto ap : aeropuertos.values()) {
            if (ap.continente != null && !ap.continente.isBlank()) {
                REGION_BY_IATA.put(ap.codigo, ap.continente);
            }
        }

        GrafoVuelos grafo = new GrafoVuelos(vuelos);
        double[] tau = new double[vuelos.size()];
        double[] heur = new double[vuelos.size()];
        Arrays.fill(tau, 0.1);

        // Calcular heurística usando distancia real (Haversine) y duración en horas
        for (Vuelo v : vuelos) {
            Aeropuerto a1 = aeropuertos.get(v.origen);
            Aeropuerto a2 = aeropuertos.get(v.destino);
            double heurVal = 1e-6;
            if (a1 == null || a2 == null) {
                heur[v.id] = heurVal;
                continue;
            }
            double dist = UtilArchivos.distanciaKm(a1, a2);
            double durHoras = v.horasDuracion;
            if (dist <= 0 || Double.isInfinite(dist) || Double.isNaN(dist)) dist = 1e6;
            heurVal = 1.0 / (dist/1000.0 + durHoras + 1.0);
            heur[v.id] = heurVal;
        }

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
                    Ruta r = construirRuta(hub, ped.destinoIata, grafo, tau, heur, p.pasosMax, presupuesto, capRest, aeropuertos, 
                        ped.dia, ped.hora, ped.minuto, rnd);
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

            int paquetesRestantes = ped.paquetes;
            List<Ruta> rutasUsadas = new ArrayList<>();
            List<Integer> paquetesPorRuta = new ArrayList<>();
            
            while (paquetesRestantes > 0 && mejor != null) {
                Asignacion asg = new Asignacion();
                asg.pedido = ped;
                asg.hubOrigen = hub;
                asg.ruta = mejor;
                asg.paquetesAsignados = 0;
                asg.paquetesPendientes = paquetesRestantes;

                // cuello de botella: vuelos + almacén destino
                int cuelloVuelo = Integer.MAX_VALUE;
                for (int fid : mejor.vuelosUsados) {
                    cuelloVuelo = Math.min(cuelloVuelo, capRest.getOrDefault(fid, 0));
                }
                
                Aeropuerto apDest = aeropuertos.get(ped.destinoIata);
                int remAlmacen = 0;
                if (apDest != null) {
                    remAlmacen = Math.max(0, apDest.capacidad - apDest.cargaEntrante);
                }
                
                int asignable = Math.max(0, Math.min(paquetesRestantes, Math.min(cuelloVuelo, remAlmacen)));

                if (asignable > 0) {
                    for (int fid : mejor.vuelosUsados) {
                        capRest.put(fid, capRest.get(fid) - asignable);
                    }
                    if (apDest != null) {
                        apDest.cargaEntrante += asignable;
                    }
                    asg.paquetesAsignados = asignable;
                    asg.paquetesPendientes = paquetesRestantes - asignable;
                    paquetesRestantes -= asignable;
                    
                    rutasUsadas.add(mejor);
                    paquetesPorRuta.add(asignable);
                }
                
                resultado.add(asg);
                
                // Si quedan paquetes, intentar encontrar otra ruta
                if (paquetesRestantes > 0) {
                    mejor = null;
                    double mejorHoras = Double.POSITIVE_INFINITY;
                    
                    // Buscar una nueva ruta evitando vuelos saturados
                    // Usar solo 1/3 de las iteraciones y hormigas para rutas adicionales
                    for (int it=0; it<p.iteraciones/3; it++) {
                        for (int h=0; h<p.hormigas/3; h++) {
                            Ruta r = construirRuta(hub, ped.destinoIata, grafo, tau, heur, p.pasosMax, presupuesto, 
                                capRest, aeropuertos, ped.dia, ped.hora, ped.minuto, rnd);
                            if (r != null && r.horasTotales < mejorHoras) {
                                mejor = r;
                                mejorHoras = r.horasTotales;
                            }
                        }
                    }
                    
                    // Si después de algunos intentos no encontramos ruta, abandonar
                    if (mejorHoras == Double.POSITIVE_INFINITY) break;
                    
                    // Si no encontramos una ruta alternativa, salimos del bucle
                    if (mejor == null) break;
                }
            }
        }
        return resultado;
    }

}
