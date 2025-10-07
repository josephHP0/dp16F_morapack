package com.morapack.planificador.nucleo;

import com.morapack.planificador.dominio.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Análisis detallado de la lógica del algoritmo ACO y módulo de replanificación
 * Identifica problemas de configuración, conectividad y distribución que causan paquetes pendientes
 */
public class AnalisisAlgoritmo {
    
    public static void analizarConfiguracionACO(ParametrosAco p) {
        System.out.println("=== >> ANÁLISIS DE CONFIGURACIÓN ACO ===");
        
        // Análisis de balance exploración vs explotación
        double balanceExploracion = p.alpha / (p.alpha + p.beta);
        System.out.printf("Balance exploración/explotación: %.2f (α=%.1f, β=%.1f)%n", 
                         balanceExploracion, p.alpha, p.beta);
        
        if (balanceExploracion < 0.3) {
            System.out.println(">> PROBLEMA: Demasiada explotación, poca exploración");
            System.out.println("   → Las hormigas siguen siempre las mismas rutas conocidas");
            System.out.println("   → Solución: Aumentar α o reducir β");
        } else if (balanceExploracion > 0.7) {
            System.out.println(">> PROBLEMA: Demasiada exploración, poca explotación");
            System.out.println("   → Las hormigas no aprenden de buenas rutas encontradas");
            System.out.println("   → Solución: Reducir α o aumentar β");
        } else {
            System.out.println(">> Balance exploración/explotación: ÓPTIMO");
        }
        
        // Análisis de evaporación
        System.out.printf("Tasa de evaporación: %.2f%n", p.rho);
        if (p.rho < 0.1) {
            System.out.println(">> PROBLEMA CRÍTICO: Evaporación muy baja - convergencia prematura");
            System.out.println("   → Las feromonas se acumulan indefinidamente");
            System.out.println("   → El algoritmo se queda en mínimos locales");
            System.out.println("   → Solución: Aumentar ρ a 0.3-0.5");
        } else if (p.rho > 0.8) {
            System.out.println(">> PROBLEMA: Evaporación muy alta - pierde memoria");
            System.out.println("   → Las feromonas desaparecen antes de acumularse");
            System.out.println("   → Solución: Reducir ρ a 0.3-0.5");
        } else {
            System.out.println(">> Tasa de evaporación: BUENA");
        }
        
        // Análisis de intensidad
        System.out.printf("Intensidad de refuerzo: %.1f%n", p.Q);
        if (p.Q > 150) {
            System.out.println(">> PROBLEMA: Refuerzo muy alto - convergencia prematura");
            System.out.println("   → Las primeras rutas encontradas dominan completamente");
            System.out.println("   → Solución: Reducir Q a 50-100");
        } else if (p.Q < 10) {
            System.out.println(">> PROBLEMA: Refuerzo muy bajo - no hay aprendizaje");
            System.out.println("   → Solución: Aumentar Q a 50-100");
        } else {
            System.out.println(">> Intensidad de refuerzo: ADECUADA");
        }
        
        // Análisis de población
        int ejecucionesTotales = p.hormigas * p.iteraciones;
        System.out.printf("Esfuerzo computacional: %d hormigas × %d iter = %d ejecuciones%n",
                         p.hormigas, p.iteraciones, ejecucionesTotales);
        
        if (ejecucionesTotales > 3000) {
            System.out.println(">> AVISO: Configuración muy costosa computacionalmente");
            System.out.println("   → Tiempo de ejecución puede ser excesivo");
        } else if (ejecucionesTotales < 500) {
            System.out.println(">> PROBLEMA: Configuración insuficiente");
            System.out.println("   → Pocas exploraciones para problemas complejos");
        } else {
            System.out.println(">> Esfuerzo computacional: EQUILIBRADO");
        }
        
        // Análisis de longitud de ruta
        System.out.printf("Pasos máximos permitidos: %d%n", p.pasosMax);
        if (p.pasosMax > 50) {
            System.out.println(">> PROBLEMA: Pasos máximos muy altos - rutas ineficientes");
            System.out.println("   → Permite rutas con muchas escalas innecesarias");
            System.out.println("   → Solución: Reducir a 20-30 pasos");
        } else if (p.pasosMax < 10) {
            System.out.println(">> PROBLEMA: Pasos máximos muy bajos - rutas imposibles");
            System.out.println("   → Algunas rutas válidas no se pueden construir");
            System.out.println("   → Solución: Aumentar a 15-25 pasos");
        } else {
            System.out.println(">> Pasos máximos: RAZONABLES");
        }
    }
    
    public static void analizarDistribucionPedidos(List<Pedido> pedidos) {
        System.out.println("\n=== >> ANÁLISIS DE DISTRIBUCIÓN DE PEDIDOS ===");
        
        // Distribución por días
        Map<Integer, Integer> pedidosPorDia = new HashMap<>();
        Map<Integer, Integer> paquetesPorDia = new HashMap<>();
        
        for (Pedido p : pedidos) {
            pedidosPorDia.merge(p.dia, 1, Integer::sum);
            paquetesPorDia.merge(p.dia, p.paquetes, Integer::sum);
        }
        
        System.out.println("Distribución de pedidos por día:");
        int totalDias = pedidosPorDia.size();
        for (Map.Entry<Integer, Integer> entry : pedidosPorDia.entrySet()) {
            int dia = entry.getKey();
            int numPedidos = entry.getValue();
            int numPaquetes = paquetesPorDia.get(dia);
            double porcentajePedidos = (numPedidos * 100.0) / pedidos.size();
            System.out.printf("  Día %02d: %d pedidos (%.1f%%), %d paquetes%n", 
                             dia, numPedidos, porcentajePedidos, numPaquetes);
        }
        
        // Identificar días con alta carga
        int maxPedidos = Collections.max(pedidosPorDia.values());
        int minPedidos = Collections.min(pedidosPorDia.values());
        double promedioPedidos = pedidosPorDia.values().stream().mapToInt(i -> i).average().orElse(0);
        
        System.out.printf("Estadísticas: Min=%d, Max=%d, Promedio=%.1f, Días activos=%d%n", 
                         minPedidos, maxPedidos, promedioPedidos, totalDias);
        
        if (maxPedidos > promedioPedidos * 2) {
            System.out.println(">> PROBLEMA: Distribución muy desigual de pedidos");
            System.out.println("   → Los días con alta carga saturan recursos y causan asignaciones incompletas");
            System.out.println("   → Considerar balancear la carga o aumentar capacidades");
        }
        
        // Análisis de rango temporal
        int diaMin = Collections.min(pedidosPorDia.keySet());
        int diaMax = Collections.max(pedidosPorDia.keySet());
        System.out.printf("Rango temporal: día %d al %d (%d días de span)%n", diaMin, diaMax, (diaMax - diaMin + 1));
        
        if ((diaMax - diaMin + 1) > 31) {
            System.out.println(">> PROBLEMA: Pedidos fuera del rango mensual estándar");
        }
        
        // Distribución por destinos
        Map<String, Integer> pedidosPorDestino = new HashMap<>();
        Map<String, Integer> paquetesPorDestino = new HashMap<>();
        for (Pedido p : pedidos) {
            pedidosPorDestino.merge(p.destinoIata, 1, Integer::sum);
            paquetesPorDestino.merge(p.destinoIata, p.paquetes, Integer::sum);
        }
        
        System.out.println("\nTop 10 destinos más solicitados:");
        pedidosPorDestino.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(entry -> {
                    String destino = entry.getKey();
                    int numPedidos = entry.getValue();
                    int numPaquetes = paquetesPorDestino.get(destino);
                    double porcentaje = (numPedidos * 100.0) / pedidos.size();
                    System.out.printf("  %s: %d pedidos (%.1f%%), %d paquetes%n", 
                            destino, numPedidos, porcentaje, numPaquetes);
                });
    }
    
    public static void analizarConectividad(Map<String, Aeropuerto> aeropuertos, List<Vuelo> vuelos) {
        System.out.println("\n===  ANÁLISIS DE CONECTIVIDAD ===");
        
        // Matriz de conectividad
        Map<String, Set<String>> conexiones = new HashMap<>();
        Map<String, Set<String>> conexionesInversas = new HashMap<>();
        
        for (Vuelo v : vuelos) {
            conexiones.computeIfAbsent(v.origen, k -> new HashSet<>()).add(v.destino);
            conexionesInversas.computeIfAbsent(v.destino, k -> new HashSet<>()).add(v.origen);
        }
        
        // Aeropuertos por grado de conectividad
        Map<String, Integer> gradoSalida = new HashMap<>();
        Map<String, Integer> gradoEntrada = new HashMap<>();
        
        for (Vuelo v : vuelos) {
            gradoSalida.merge(v.origen, 1, Integer::sum);
            gradoEntrada.merge(v.destino, 1, Integer::sum);
        }
        
        System.out.println("Top 5 hubs (más conexiones salientes):");
        gradoSalida.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> System.out.printf("  %s: %d vuelos salientes%n", 
                        entry.getKey(), entry.getValue()));
        
        System.out.println("\nTop 5 destinos populares (más conexiones entrantes):");
        gradoEntrada.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> System.out.printf("  %s: %d vuelos entrantes%n", 
                        entry.getKey(), entry.getValue()));
        
        // Identificar aeropuertos problemáticos
        Set<String> aeropuertosSinSalida = new HashSet<>(aeropuertos.keySet());
        aeropuertosSinSalida.removeAll(gradoSalida.keySet());
        
        if (!aeropuertosSinSalida.isEmpty()) {
            System.out.println(" PROBLEMA CRÍTICO: Aeropuertos sin vuelos salientes: " + aeropuertosSinSalida);
            System.out.println("   → Estos aeropuertos no pueden ser hubs intermedios");
        }
        
        Set<String> aeropuertosSinEntrada = new HashSet<>(aeropuertos.keySet());
        aeropuertosSinEntrada.removeAll(gradoEntrada.keySet());
        
        if (!aeropuertosSinEntrada.isEmpty()) {
            System.out.println(" PROBLEMA CRÍTICO: Aeropuertos sin vuelos entrantes: " + aeropuertosSinEntrada);
            System.out.println("   → Estos destinos son inalcanzables");
        }
        
        // Análisis de alcanzabilidad desde hubs principales
        Set<String> hubsPrincipales = Set.of("SPIM", "EBCI", "UBBB");
        System.out.println("\nAnálisis de alcanzabilidad desde hubs:");
        
        for (String hub : hubsPrincipales) {
            if (aeropuertos.containsKey(hub)) {
                Set<String> alcanzables = calcularAlcanzabilidad(hub, conexiones);
                double porcentajeAlcanzable = (alcanzables.size() * 100.0) / aeropuertos.size();
                System.out.printf("  %s: alcanza %d/%d aeropuertos (%.1f%%)%n", 
                                 hub, alcanzables.size(), aeropuertos.size(), porcentajeAlcanzable);
                
                if (porcentajeAlcanzable < 80) {
                    System.out.printf("    >> Hub %s tiene conectividad limitada%n", hub);
                }
            }
        }
        
        // Análisis de componentes conectados
        int componentesConectados = contarComponentesConectados(conexiones);
        System.out.printf("Componentes conectados: %d%n", componentesConectados);
        
        if (componentesConectados > 1) {
            System.out.println(" PROBLEMA CRÍTICO: Red no totalmente conectada");
            System.out.println("   → Algunos destinos pueden ser inalcanzables desde ciertos orígenes");
        } else {
            System.out.println(">> Red totalmente conectada");
        }
    }
    
    private static Set<String> calcularAlcanzabilidad(String origen, Map<String, Set<String>> grafo) {
        Set<String> visitados = new HashSet<>();
        Queue<String> cola = new LinkedList<>();
        cola.add(origen);
        visitados.add(origen);
        
        while (!cola.isEmpty()) {
            String actual = cola.poll();
            Set<String> vecinos = grafo.getOrDefault(actual, Collections.emptySet());
            for (String vecino : vecinos) {
                if (!visitados.contains(vecino)) {
                    visitados.add(vecino);
                    cola.add(vecino);
                }
            }
        }
        
        return visitados;
    }
    
    private static int contarComponentesConectados(Map<String, Set<String>> grafo) {
        Set<String> visitados = new HashSet<>();
        int componentes = 0;
        
        for (String nodo : grafo.keySet()) {
            if (!visitados.contains(nodo)) {
                dfs(nodo, grafo, visitados);
                componentes++;
            }
        }
        
        return componentes;
    }
    
    private static void dfs(String nodo, Map<String, Set<String>> grafo, Set<String> visitados) {
        visitados.add(nodo);
        Set<String> vecinos = grafo.getOrDefault(nodo, Collections.emptySet());
        for (String vecino : vecinos) {
            if (!visitados.contains(vecino)) {
                dfs(vecino, grafo, visitados);
            }
        }
    }
    
    public static ParametrosAco recomendarParametrosOptimos(int numPedidos, int numAeropuertos) {
        System.out.println("\n=== >> RECOMENDACIONES DE PARÁMETROS ÓPTIMOS ===");
        
        ParametrosAco optimizado = new ParametrosAco();
        
        // Balance óptimo basado en complejidad del problema
        if (numPedidos < 50) {
            // Problema pequeño: Enfoque en velocidad
            optimizado.hormigas = 8;
            optimizado.iteraciones = 25;
            optimizado.alpha = 1.0;
            optimizado.beta = 2.0;
            optimizado.rho = 0.5;       // Evaporación balanceada
            optimizado.Q = 50.0;        // Refuerzo moderado
            optimizado.pasosMax = 20;   // Rutas cortas
            System.out.println(">> Configuración para problema PEQUEÑO (velocidad optimizada)");
        } else if (numPedidos < 200) {
            // Problema mediano: Balance velocidad-calidad
            optimizado.hormigas = 12;
            optimizado.iteraciones = 40;
            optimizado.alpha = 1.0;
            optimizado.beta = 2.5;
            optimizado.rho = 0.4;       // Menos evaporación para mejor memoria
            optimizado.Q = 75.0;
            optimizado.pasosMax = 25;
            System.out.println(">> Configuración para problema MEDIANO (balance optimizado)");
        } else {
            // Problema grande: Enfoque en calidad
            optimizado.hormigas = 15;
            optimizado.iteraciones = 60;
            optimizado.alpha = 1.2;
            optimizado.beta = 3.0;      // Mayor peso heurístico
            optimizado.rho = 0.3;       // Menor evaporación = mejor memoria
            optimizado.Q = 100.0;
            optimizado.pasosMax = 30;
            System.out.println(">> Configuración para problema GRANDE (calidad optimizada)");
        }
        
        /*System.out.printf("Parámetros recomendados:%n");
        System.out.printf("   Hormigas: %d%n", optimizado.hormigas);
        System.out.printf("  >> Iteraciones: %d%n", optimizado.iteraciones);
        System.out.printf("  >> Alpha (feromonas): %.1f%n", optimizado.alpha);
        System.out.printf("   Beta (heurística): %.1f%n", optimizado.beta);
        System.out.printf("   Rho (evaporación): %.1f%n", optimizado.rho);
        System.out.printf("   Q (refuerzo): %.1f%n", optimizado.Q);
        System.out.printf("   Pasos máx: %d%n", optimizado.pasosMax);*/
        
        int esfuerzoTotal = optimizado.hormigas * optimizado.iteraciones;
        //System.out.printf("   Esfuerzo total: %d ejecuciones%n", esfuerzoTotal);
        
        // Estimación de tiempo
        double tiempoEstimado = esfuerzoTotal * 0.01; // Aproximación: 0.01s por ejecución
        if (tiempoEstimado < 10) {
            //System.out.printf("    Tiempo estimado: %.1f segundos (RÁPIDO)%n", tiempoEstimado);
        } else if (tiempoEstimado < 60) {
           // System.out.printf("    Tiempo estimado: %.1f segundos (MODERADO)%n", tiempoEstimado);
        } else {
            //System.out.printf("    Tiempo estimado: %.1f segundos (LENTO)%n", tiempoEstimado);
        }
        
        return optimizado;
    }
    
    public static void analizarProblemasEspecificos(List<Asignacion> plan, List<Pedido> pedidos) {
        //System.out.println("\n=== ANÁLISIS DE PROBLEMAS ESPECÍFICOS ===");
        
        // Identificar patrones en pedidos no asignados
        List<Pedido> pedidosSinAsignacion = new ArrayList<>();
        Map<String, Integer> asignadosPorPedido = new HashMap<>();
        
        for (Asignacion a : plan) {
            if (a != null && a.pedido != null) {
                asignadosPorPedido.merge(a.pedido.id, a.paquetesAsignados, Integer::sum);
            }
        }
        
        for (Pedido p : pedidos) {
            int asignados = asignadosPorPedido.getOrDefault(p.id, 0);
            if (asignados == 0) {
                pedidosSinAsignacion.add(p);
            }
        }
        
        System.out.printf("Pedidos completamente sin asignar: %d/%d (%.1f%%)%n", 
                         pedidosSinAsignacion.size(), pedidos.size(), 
                         (pedidosSinAsignacion.size() * 100.0) / pedidos.size());
        
        if (!pedidosSinAsignacion.isEmpty()) {
            // Análisis por destino
            Map<String, Long> sinAsignacionPorDestino = pedidosSinAsignacion.stream()
                    .collect(Collectors.groupingBy(p -> p.destinoIata, Collectors.counting()));
            
            System.out.println("Destinos con más pedidos sin asignar:");
            sinAsignacionPorDestino.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(5)
                    .forEach(entry -> System.out.printf("  %s: %d pedidos sin asignar%n", 
                            entry.getKey(), entry.getValue()));
            
            // Análisis por día
            Map<Integer, Long> sinAsignacionPorDia = pedidosSinAsignacion.stream()
                    .collect(Collectors.groupingBy(p -> p.dia, Collectors.counting()));
            
            System.out.println("Días con más pedidos sin asignar:");
            sinAsignacionPorDia.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                    .limit(5)
                    .forEach(entry -> System.out.printf("  Día %02d: %d pedidos sin asignar%n", 
                            entry.getKey(), entry.getValue()));
        }
        
        // Análisis de eficiencia por ruta
        Map<String, List<Double>> eficienciaPorDestino = new HashMap<>();
        for (Asignacion a : plan) {
            if (a != null && a.pedido != null && a.paquetesAsignados > 0) {
                double eficiencia = (a.paquetesAsignados * 100.0) / a.pedido.paquetes;
                eficienciaPorDestino.computeIfAbsent(a.pedido.destinoIata, k -> new ArrayList<>()).add(eficiencia);
            }
        }
        
        System.out.println("\nEficiencia promedio por destino (top 5 problemáticos):");
        eficienciaPorDestino.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 2) // Al menos 2 pedidos
                .map(entry -> {
                    String destino = entry.getKey();
                    double promedioEficiencia = entry.getValue().stream().mapToDouble(d -> d).average().orElse(0);
                    return Map.entry(destino, promedioEficiencia);
                })
                .sorted(Map.Entry.<String, Double>comparingByValue())
                .limit(5)
                .forEach(entry -> System.out.printf("  %s: %.1f%% eficiencia promedio%n", 
                        entry.getKey(), entry.getValue()));
    }
}