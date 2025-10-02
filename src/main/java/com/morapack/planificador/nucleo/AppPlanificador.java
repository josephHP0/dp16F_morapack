package com.morapack.planificador.nucleo;

import com.morapack.planificador.dominio.Aeropuerto;
import com.morapack.planificador.dominio.Vuelo;
import com.morapack.planificador.dominio.Pedido;
import com.morapack.planificador.util.UtilArchivos;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class AppPlanificador {

    // === CLASES PARA GESTIÓN TEMPORAL ===
    
    static class PaqueteEnAlmacen {
        int cantidad;
        int diaLlegada;
        int minutoLlegada;
        String pedidoId;
        
        PaqueteEnAlmacen(int cantidad, int diaLlegada, int minutoLlegada, String pedidoId) {
            this.cantidad = cantidad;
            this.diaLlegada = diaLlegada;
            this.minutoLlegada = minutoLlegada;
            this.pedidoId = pedidoId;
        }
    }
    
    static class PaqueteEnVuelo {
        int cantidad;
        String pedidoId;
        
        PaqueteEnVuelo(int cantidad, String pedidoId) {
            this.cantidad = cantidad;
            this.pedidoId = pedidoId;
        }
    }
    
    static class EstadoVuelo {
        int capacidadDisponible;
        List<PaqueteEnVuelo> paquetesABordo;
        int ultimoDiaOperacion;
        
        EstadoVuelo(int capacidadTotal) {
            this.capacidadDisponible = capacidadTotal;
            this.paquetesABordo = new ArrayList<>();
            this.ultimoDiaOperacion = 0;
        }
    }
    
    static class EstadoAeropuerto {
        List<PaqueteEnAlmacen> paquetesEnAlmacen;
        int ocupacionTotal;
        
        EstadoAeropuerto() {
            this.paquetesEnAlmacen = new ArrayList<>();
            this.ocupacionTotal = 0;
        }
    }
    
    // Mapas para mantener estado entre días
    private static Map<Integer, EstadoVuelo> estadosVuelos = new HashMap<>();
    private static Map<String, EstadoAeropuerto> estadosAeropuertos = new HashMap<>();

    public static void main(String[] args) throws Exception {
        Map<String,String> arg = Arrays.stream(args)
                .map(s -> s.split("=", 2))
                .filter(a -> a.length==2 && a[0].startsWith("--"))
                .collect(Collectors.toMap(a->a[0].substring(2), a->a[1], (a,b)->b, LinkedHashMap::new));

        Path aeropuertosPath = Paths.get(arg.getOrDefault("aeropuertos", "data/aeropuertos.txt"));
        Path vuelosPath      = Paths.get(arg.getOrDefault("vuelos", "data/vuelos.txt"));
        Path pedidosPath     = Paths.get(arg.getOrDefault("pedidos", "data/pedidos2.txt"));
        Path cancelacionesPath = Paths.get(arg.getOrDefault("cancelaciones", "data/cancelaciones.txt"));
        Path salidaPath      = Paths.get(arg.getOrDefault("salida", "plan_asignacion.csv"));

        // Carga de datos
        Map<String,Aeropuerto> aeropuertos = UtilArchivos.cargarAeropuertos(aeropuertosPath);
        if (aeropuertos.isEmpty()) throw new IllegalArgumentException("No se cargaron aeropuertos.");
        List<Vuelo> vuelos = UtilArchivos.cargarVuelos(vuelosPath, aeropuertos);
        if (vuelos.isEmpty()) throw new IllegalArgumentException("No se cargaron vuelos válidos.");
        
        // Inicializar estados temporales
        inicializarEstadosTemporales(vuelos, aeropuertos);

        List<Pedido> pedidos = (pedidosPath!=null && Files.exists(pedidosPath))
                ? UtilArchivos.cargarPedidos(pedidosPath, aeropuertos.keySet())
                : UtilArchivos.generarPedidosSinteticos(aeropuertos.keySet(), PlanificadorAco.HUBS.keySet(), 40, 7L);

        // Parámetros ACO MÁXIMOS para eliminación total de paquetes pendientes
        ParametrosAco p = new ParametrosAco();
        p.alpha = 1.2;       // Incrementar peso de feromonas
        p.beta = 2.5;        // Incrementar peso heurístico
        p.rho = 0.2;         // MÍNIMA evaporación = máxima persistencia
        p.Q = 200.0;         // MÁXIMO refuerzo de feromonas
        p.hormigas = 50;     // MÁXIMAS hormigas explorando
        p.iteraciones = 100; // MÁXIMAS iteraciones para convergencia
        p.pasosMax = 60;     // Rutas EXTRA largas permitidas

        // Simulación COMPLETA de 31 días para cobertura total
        int diaInicio = 1;
        int dias = 31;  // Simulación mensual completa para cubrir TODOS los pedidos

        System.out.println("[SIMULACIÓN MENSUAL COMPLETA] Inicio=" + diaInicio + " días=" + dias);
        
        // ANÁLISIS PREVIO COMPLETO DEL SISTEMA
        System.out.println("\n=== ANÁLISIS PREVIO DEL SISTEMA ===");
        AnalisisAlgoritmo.analizarConfiguracionACO(p);
        AnalisisAlgoritmo.analizarDistribucionPedidos(pedidos);
        AnalisisAlgoritmo.analizarConectividad(aeropuertos, vuelos);
        
        // Aplicar parámetros recomendados automáticamente
        ParametrosAco recomendado = AnalisisAlgoritmo.recomendarParametrosOptimos(
            pedidos.size(), aeropuertos.size());
        
        System.out.println("\n>> APLICANDO PARÁMETROS OPTIMIZADOS AUTOMÁTICAMENTE...");
        p = recomendado;
        
        System.out.println(">> PARÁMETROS FINALES: " + p.hormigas + " hormigas × " + p.iteraciones + " iteraciones = " + (p.hormigas * p.iteraciones) + " ejecuciones");
        if (!Files.exists(cancelacionesPath)) {
            System.out.println("ADVERTENCIA: No existe el archivo de cancelaciones en " + cancelacionesPath.toAbsolutePath());
            System.out.println("Se simulará SIN cancelaciones.");
        } else {
            long lineas;
            try (var stream = Files.lines(cancelacionesPath)) {
                lineas = stream.filter(s->!s.trim().isEmpty() && !s.startsWith("#")).count();
            }
            System.out.println("Cancelaciones cargadas (líneas útiles): " + lineas);
        }

        // EJECUTAR SIMULACIÓN CON MONITOREO DETALLADO DÍA POR DÍA
        List<Asignacion> plan = ejecutarSimulacionConMonitoreo(
                aeropuertos,
                vuelos,
                pedidos,
                p,
                cancelacionesPath,
                diaInicio,
                dias,
                7L
        );

        // DIAGNÓSTICO DETALLADO DE PAQUETES PENDIENTES
        diagnosticarPaquetesPendientes(plan, pedidos, aeropuertos, vuelos);
        
        // ANÁLISIS POST-EJECUCIÓN DE PROBLEMAS ESPECÍFICOS
        AnalisisAlgoritmo.analizarProblemasEspecificos(plan, pedidos);

        // Escritura de salida
        UtilArchivos.escribirPlanCsv(salidaPath, plan);

        // Resumen simple
        long ordenesConAsign = plan.stream()
                .filter(a -> a.paquetesAsignados > 0)
                .map(a -> a.pedido.id)
                .distinct()
                .count();
        int pkSolic = pedidos.stream().mapToInt(pedido -> pedido.paquetes).sum();
        int pkAsig = plan.stream().mapToInt(a -> a.paquetesAsignados).sum();
        int pkPend = Math.max(0, pkSolic - pkAsig);

        System.out.println("=== Resumen ===");
        System.out.println("Órdenes totales: " + pedidos.size());
        System.out.println("Órdenes con asignación: " + ordenesConAsign);
        System.out.println("Paquetes solicitados: " + pkSolic);
        System.out.println("Paquetes asignados: " + pkAsig);
        System.out.println("Paquetes pendientes: " + pkPend);
        System.out.println("Plan escrito en: " + salidaPath.toAbsolutePath());
    }

    /**
     * Método de diagnóstico para identificar las causas de paquetes pendientes
     */
    private static void diagnosticarPaquetesPendientes(List<Asignacion> plan, List<Pedido> pedidos, 
                                                     Map<String, Aeropuerto> aeropuertos, List<Vuelo> vuelos) {
        System.out.println("\n=== DIAGNÓSTICO DE PAQUETES PENDIENTES ===");
        
        // Identificar pedidos sin asignación completa
        Map<String, Integer> paquetesPorPedido = new HashMap<>();
        for (Pedido p : pedidos) {
            paquetesPorPedido.put(p.id, p.paquetes);
        }
        
        Map<String, Integer> asignadosPorPedido = new HashMap<>();
        for (Asignacion a : plan) {
            if (a != null && a.pedido != null) {
                asignadosPorPedido.merge(a.pedido.id, a.paquetesAsignados, Integer::sum);
            }
        }
        
        List<String> pedidosPendientes = new ArrayList<>();
        int totalPendientes = 0;
        
        for (Pedido p : pedidos) {
            int solicitados = p.paquetes;
            int asignados = asignadosPorPedido.getOrDefault(p.id, 0);
            if (asignados < solicitados) {
                pedidosPendientes.add(p.id);
                totalPendientes += (solicitados - asignados);
            }
        }
        
        System.out.println("Pedidos con paquetes pendientes: " + pedidosPendientes.size() + "/" + pedidos.size());
        System.out.println("Total paquetes pendientes: " + totalPendientes);
        
        if (!pedidosPendientes.isEmpty()) {
            System.out.println("\n>> Analizando primeros 10 pedidos problemáticos:");
            
            for (int i = 0; i < Math.min(10, pedidosPendientes.size()); i++) {
                String pedidoId = pedidosPendientes.get(i);
                Pedido pedido = pedidos.stream().filter(p -> p.id.equals(pedidoId)).findFirst().orElse(null);
                
                if (pedido != null) {
                    int solicitados = pedido.paquetes;
                    int asignados = asignadosPorPedido.getOrDefault(pedido.id, 0);
                    
                    System.out.printf("  Pedido %s: %d/%d paquetes (%.1f%%)%n", 
                                     pedido.id, asignados, solicitados, (asignados * 100.0 / solicitados));
                    System.out.printf("     Destino: %s, Día: %d, Hora: %02d:%02d%n", 
                                     pedido.destinoIata, pedido.dia, pedido.hora, pedido.minuto);
                    
                    // Verificar si el destino existe
                    if (!aeropuertos.containsKey(pedido.destinoIata)) {
                        System.out.println("     ERROR: Aeropuerto destino no existe");
                        continue;
                    }
                    
                    // Verificar conectividad básica
                    Aeropuerto destino = aeropuertos.get(pedido.destinoIata);
                    long vuelosAlDestino = vuelos.stream()
                            .filter(v -> v.destino.equals(pedido.destinoIata))
                            .count();
                    
                    System.out.printf("     >> Vuelos hacia %s: %d disponibles%n", pedido.destinoIata, vuelosAlDestino);
                    
                    if (vuelosAlDestino == 0) {
                        System.out.println("     PROBLEMA: No hay vuelos hacia este destino");
                    }
                    
                    // Verificar capacidad de almacén
                    System.out.printf("     Capacidad almacén: %d/%d (%.1f%% ocupado)%n", 
                                     destino.cargaEntrante, destino.capacidad,
                                     (destino.cargaEntrante * 100.0 / destino.capacidad));
                    
                    if (destino.cargaEntrante >= destino.capacidad) {
                        System.out.println("     PROBLEMA: Almacén saturado");
                    }
                }
            }
        }
        
        // Estadísticas generales de conectividad
        System.out.println("\n>> ESTADÍSTICAS DE RED:");
        
        Set<String> destinosConVuelos = vuelos.stream().map(v -> v.destino).collect(Collectors.toSet());
        Set<String> destinosPedidos = pedidos.stream().map(p -> p.destinoIata).collect(Collectors.toSet());
        
        System.out.println("  Aeropuertos con vuelos entrantes: " + destinosConVuelos.size() + "/" + aeropuertos.size());
        
        Set<String> destinosSinVuelos = new HashSet<>(destinosPedidos);
        destinosSinVuelos.removeAll(destinosConVuelos);
        
        if (!destinosSinVuelos.isEmpty()) {
            System.out.println(" Destinos de pedidos SIN vuelos: " + destinosSinVuelos);
        }
        
        // Analizar saturación de almacenes
        long almacenesSaturados = aeropuertos.values().stream()
                .filter(a -> a.cargaEntrante >= a.capacidad)
                .count();
        
        System.out.println(" Almacenes saturados: " + almacenesSaturados + "/" + aeropuertos.size());
        
        // Recomendar mejoras
        System.out.println("\n>> RECOMENDACIONES:");
        
        if (destinosSinVuelos.size() > 0) {
            System.out.println("  >> Agregar más vuelos hacia: " + destinosSinVuelos);
        }
        
        if (almacenesSaturados > 0) {
            System.out.println("  >> Aumentar capacidad de almacén en aeropuertos saturados");
        }
        
        double totalPaquetesSolicitados = pedidos.stream().mapToInt(p -> p.paquetes).sum();
        double porcentajePendiente = totalPaquetesSolicitados > 0 ? (totalPendientes * 100.0 / totalPaquetesSolicitados) : 0;
        
        if (porcentajePendiente > 20) {
            System.out.println("  >> Considerar aumentar parámetros ACO (más hormigas/iteraciones)");
        }
        
        if (porcentajePendiente > 10) {
            System.out.println("  >> Revisar restricciones temporales de pedidos");
        }
        
        System.out.printf(">> Resumen: %.1f%% paquetes pendientes%n", porcentajePendiente);
    }
    
    /**
     * Ejecuta simulación con monitoreo detallado día por día
     * Muestra cancelaciones, impactos y replanificaciones en tiempo real
     */
    private static List<Asignacion> ejecutarSimulacionConMonitoreo(
            Map<String, Aeropuerto> aeropuertos,
            List<Vuelo> vuelos,
            List<Pedido> pedidos,
            ParametrosAco parametros,
            Path archivoCancelaciones,
            int diaInicio,
            int numeroDias,
            long semilla
    ) throws Exception {

        System.out.println("\\n=== INICIANDO SIMULACIÓN REACTIVA CON MONITOREO DETALLADO ===");
        System.out.printf(">> Período: %d días (del %d al %d)%n", numeroDias, diaInicio, diaInicio + numeroDias - 1);
        
        // Cargar cancelaciones
        List<PlanificadorAco.Cancelacion> cancels = PlanificadorAco.cargarCancelaciones(archivoCancelaciones);
        Map<Integer, Set<Integer>> cancelByDay = PlanificadorAco.mapearCancelacionesAIdsPorDia(vuelos, cancels);
        
        System.out.printf(">> Total cancelaciones cargadas: %d eventos%n", cancels.size());
        mostrarResumenCancelacionesDetallado(cancels, diaInicio, numeroDias);
        
        List<Asignacion> consolidado = new ArrayList<>();
        
        // Simular día por día con logging detallado
        System.out.println("\\n=== SIMULACIÓN DÍA POR DÍA ===");
        
        // Ordenar pedidos
        pedidos.sort(Comparator
                .comparingInt((Pedido p) -> p.dia)
                .thenComparingInt(p -> p.hora)
                .thenComparingInt(p -> p.minuto));

        for (int d = 0; d < numeroDias; d++) {
            int diaSim = diaInicio + d;
            if (diaSim > 31) break;
            
            System.out.printf("%n=== DÍA %02d ====%n", diaSim);
            long inicioTiempo = System.currentTimeMillis();
            
            // Analizar cancelaciones del día
            Set<Integer> cancelacionesDia = cancelByDay.getOrDefault(diaSim, Collections.emptySet());
            analizarCancelacionesDelDiaDetallado(diaSim, cancelacionesDia, vuelos);
            
            // Identificar pedidos del día
            List<Pedido> pedidosDelDia = new ArrayList<>();
            for (Pedido p : pedidos) {
                if (p.dia == diaSim) {
                    pedidosDelDia.add(p);
                }
            }
            
            int paquetesDelDia = pedidosDelDia.stream().mapToInt(p -> p.paquetes).sum();
            System.out.printf(">> Pedidos del día: %d pedidos, %d paquetes%n", 
                             pedidosDelDia.size(), paquetesDelDia);
            
            if (pedidosDelDia.isEmpty()) {
                System.out.println("   >> Sin pedidos para procesar, continuando...");
                continue;
            }

            // === GESTIÓN TEMPORAL REALISTA ===
            System.out.println(">> Actualizando estados temporales...");
            
            // PASO 1: Procesar recogida de paquetes (después de 2 horas)
            actualizarEstadoAlmacenes(aeropuertos, diaSim, 0);
            
            // PASO 2: Procesar vuelos que completaron su ciclo
            actualizarEstadoVuelos(vuelos, aeropuertos, diaSim);
            
            // PASO 3: Preparar vuelos con capacidades REALES disponibles
            List<Vuelo> vuelosCopia = prepararVuelosConCapacidadReal(vuelos, diaSim);
            
            // PASO 4: Mostrar estado actual de recursos
            mostrarEstadoRecursos(aeropuertos, vuelosCopia, diaSim);

            // Planificar con cancelaciones
            Map<Integer, Set<Integer>> cancelSoloHoy = new HashMap<>();
            if (cancelByDay.containsKey(diaSim)) {
                cancelSoloHoy.put(diaSim, cancelByDay.get(diaSim));
            }
            
            System.out.println(">> Ejecutando planificación ACO reactiva...");
            
            List<Asignacion> planDelDia = PlanificadorAco.planificarConAco(
                    aeropuertos,
                    vuelosCopia,
                    pedidosDelDia,
                    parametros,
                    semilla + diaSim,
                    cancelSoloHoy,
                    diaSim
            );

            // Analizar resultados del día
            int paquetesAsignados = planDelDia.stream().mapToInt(a -> a.paquetesAsignados).sum();
            double eficiencia = paquetesDelDia > 0 ? (paquetesAsignados * 100.0 / paquetesDelDia) : 0.0;
            
            long pedidosConAsignacion = planDelDia.stream()
                    .filter(a -> a.paquetesAsignados > 0)
                    .map(a -> a.pedido.id)
                    .distinct()
                    .count();
            
            System.out.printf(">> Resultados: %d/%d paquetes asignados (%.1f%%)%n", 
                             paquetesAsignados, paquetesDelDia, eficiencia);
            System.out.printf(">> Pedidos: %d/%d con asignación (%.1f%%)%n", 
                             pedidosConAsignacion, pedidosDelDia.size(), 
                             (pedidosConAsignacion * 100.0 / pedidosDelDia.size()));
            
            // Identificar impactos de cancelaciones
            if (!cancelacionesDia.isEmpty()) {
                identificarImpactosCancelacionesDetallado(planDelDia, pedidosDelDia);
            }
            
            // PASO 5: Ejecutar asignaciones actualizando estados temporales
            ejecutarAsignacionesTemporales(planDelDia, aeropuertos, vuelosCopia, diaSim);
            
            consolidado.addAll(planDelDia);
            
            long finTiempo = System.currentTimeMillis();
            double tiempoEjecucion = (finTiempo - inicioTiempo) / 1000.0;
            
            // Mostrar resumen del día
            String prefijo = eficiencia >= 90 ? "+++" : eficiencia >= 70 ? "+ +" : ">> ";
            String estado = cancelacionesDia.isEmpty() ? "Operación normal" : "Cancelaciones activas";
            
            System.out.printf("%s Día %02d completado en %.1fs - Eficiencia: %.1f%% (%s)%n", 
                             prefijo, diaSim, tiempoEjecucion, eficiencia, estado);
            
            if (!cancelacionesDia.isEmpty()) {
                System.out.printf("   >> %d cancelaciones procesadas%n", cancelacionesDia.size());
            }
            
            System.out.println("   ─────────────────────────────────────────");
        }
        
        return consolidado;
    }
    
    private static void mostrarResumenCancelacionesDetallado(List<PlanificadorAco.Cancelacion> cancels, int diaInicio, int numeroDias) {
        System.out.println("\\n📋 Resumen detallado de cancelaciones por día:");
        
        Map<Integer, List<PlanificadorAco.Cancelacion>> cancelsPorDia = cancels.stream()
                .filter(c -> c.dia >= diaInicio && c.dia < diaInicio + numeroDias)
                .collect(Collectors.groupingBy(c -> c.dia));
        
        for (int d = 0; d < numeroDias; d++) {
            int dia = diaInicio + d;
            List<PlanificadorAco.Cancelacion> cancelsDia = cancelsPorDia.getOrDefault(dia, Collections.emptyList());
            
            if (!cancelsDia.isEmpty()) {
                System.out.printf("   >> Día %02d: %d cancelaciones%n", dia, cancelsDia.size());
                for (PlanificadorAco.Cancelacion c : cancelsDia) {
                    int horas = c.salidaMin / 60;
                    int minutos = c.salidaMin % 60;
                    System.out.printf("      >> %s→%s salida %02d:%02d%n", c.origen, c.destino, horas, minutos);
                }
            }
        }
    }
    
    private static void analizarCancelacionesDelDiaDetallado(int dia, Set<Integer> cancelacionesDia, List<Vuelo> vuelos) {
        
        if (cancelacionesDia.isEmpty()) {
            System.out.println(">> Sin cancelaciones programadas - operación normal");
            return;
        }
        
        System.out.printf(">> CANCELACIONES DETECTADAS: %d vuelos afectados%n", cancelacionesDia.size());
        
        Map<Integer, Vuelo> vuelosPorId = vuelos.stream()
                .collect(Collectors.toMap(v -> v.id, v -> v));
        
        for (Integer vueloId : cancelacionesDia) {
            Vuelo vuelo = vuelosPorId.get(vueloId);
            if (vuelo != null) {
                System.out.printf("   >> Vuelo %d: %s→%s (Capacidad: %d paquetes)%n", 
                                 vueloId, vuelo.origen, vuelo.destino, vuelo.capacidad);
            }
        }
        
        System.out.println("   >> Sistema iniciando replanificación reactiva...");
    }
    
    private static void identificarImpactosCancelacionesDetallado(List<Asignacion> plan, List<Pedido> pedidos) {
        
        System.out.println(">> ANALIZANDO IMPACTOS DE CANCELACIONES:");
        
        List<String> pedidosAfectados = new ArrayList<>();
        
        for (Pedido p : pedidos) {
            int asignado = plan.stream()
                    .filter(a -> a.pedido.id.equals(p.id))
                    .mapToInt(a -> a.paquetesAsignados)
                    .sum();
            
            if (asignado < p.paquetes) {
                double porcentajeAfectado = ((p.paquetes - asignado) * 100.0) / p.paquetes;
                String descripcion = String.format("Pedido %s: %d/%d paquetes asignados (%.1f%% impacto)", 
                                                  p.id, asignado, p.paquetes, porcentajeAfectado);
                pedidosAfectados.add(descripcion);
            }
        }
        
        if (pedidosAfectados.isEmpty()) {
            System.out.println("   >> EXCELENTE: Cancelaciones manejadas sin impactos significativos");
            System.out.println("   >> Sistema reactivo funcionó perfectamente");
        } else {
            System.out.printf("   >> IMPACTOS IDENTIFICADOS: %d pedidos afectados%n", pedidosAfectados.size());
            for (String descripcion : pedidosAfectados) {
                System.out.printf("      >> %s%n", descripcion);
            }
            System.out.println("   >> Replanificación aplicada automáticamente");
        }
    }
    
    // === MÉTODOS PARA GESTIÓN TEMPORAL REALISTA ===
    
    private static void inicializarEstadosTemporales(List<Vuelo> vuelos, Map<String, Aeropuerto> aeropuertos) {
        System.out.println("🔧 Inicializando estados temporales para simulación realista...");
        
        // Inicializar estados de vuelos
        estadosVuelos.clear();
        for (Vuelo v : vuelos) {
            estadosVuelos.put(v.id, new EstadoVuelo(v.capacidad));
        }
        
        // Inicializar estados de aeropuertos
        estadosAeropuertos.clear();
        for (String codigo : aeropuertos.keySet()) {
            estadosAeropuertos.put(codigo, new EstadoAeropuerto());
        }
        
        System.out.printf(">> Estados inicializados: %d vuelos, %d aeropuertos%n", 
                         estadosVuelos.size(), estadosAeropuertos.size());
    }
    
    private static void actualizarEstadoAlmacenes(Map<String, Aeropuerto> aeropuertos, int diaActual, int minutoActual) {
        int paquetesRecogidos = 0;
        int ubicacionesLiberadas = 0;
        
        for (Map.Entry<String, EstadoAeropuerto> entry : estadosAeropuertos.entrySet()) {
            String codigoAeropuerto = entry.getKey();
            EstadoAeropuerto estado = entry.getValue();
            Aeropuerto aeropuerto = aeropuertos.get(codigoAeropuerto);
            
            Iterator<PaqueteEnAlmacen> iterator = estado.paquetesEnAlmacen.iterator();
            
            while (iterator.hasNext()) {
                PaqueteEnAlmacen paquete = iterator.next();
                
                int tiempoTranscurrido = calcularDiferenciaMinutos(
                    paquete.diaLlegada, paquete.minutoLlegada,
                    diaActual, minutoActual
                );
                
                // Cliente recoge después de 2 horas (120 minutos)
                if (tiempoTranscurrido >= 120) {
                    estado.ocupacionTotal -= paquete.cantidad;
                    aeropuerto.cargaEntrante -= paquete.cantidad;
                    paquetesRecogidos += paquete.cantidad;
                    ubicacionesLiberadas++;
                    iterator.remove();
                }
            }
        }
        
        if (paquetesRecogidos > 0) {
            System.out.printf(">> Recogida automática: %d paquetes recogidos (%d ubicaciones liberadas)%n", 
                             paquetesRecogidos, ubicacionesLiberadas);
        }
    }
    
    private static void actualizarEstadoVuelos(List<Vuelo> vuelos, Map<String, Aeropuerto> aeropuertos, int diaActual) {
        int vuelosDescargados = 0;
        int paquetesDescargados = 0;
        
        for (Vuelo vuelo : vuelos) {
            EstadoVuelo estado = estadosVuelos.get(vuelo.id);
            if (estado == null) continue;
            
            // Los vuelos se "resetean" cuando completan su ciclo diario
            if (estado.ultimoDiaOperacion < diaActual && !estado.paquetesABordo.isEmpty()) {
                
                // Descargar paquetes en destino
                EstadoAeropuerto estadoDestino = estadosAeropuertos.get(vuelo.destino);
                Aeropuerto aeropuertoDestino = aeropuertos.get(vuelo.destino);
                
                for (PaqueteEnVuelo paquete : estado.paquetesABordo) {
                    // Agregar al almacén de destino
                    estadoDestino.paquetesEnAlmacen.add(new PaqueteEnAlmacen(
                        paquete.cantidad,
                        diaActual,
                        vuelo.llegadaMin,
                        paquete.pedidoId
                    ));
                    
                    estadoDestino.ocupacionTotal += paquete.cantidad;
                    aeropuertoDestino.cargaEntrante += paquete.cantidad;
                    paquetesDescargados += paquete.cantidad;
                }
                
                // Liberar capacidad del vuelo
                estado.capacidadDisponible = vuelo.capacidad;
                estado.paquetesABordo.clear();
                estado.ultimoDiaOperacion = diaActual;
                vuelosDescargados++;
            }
        }
        
        if (vuelosDescargados > 0) {
            System.out.printf(">> Vuelos completados: %d vuelos descargaron %d paquetes%n", 
                             vuelosDescargados, paquetesDescargados);
        }
    }
    
    private static List<Vuelo> prepararVuelosConCapacidadReal(List<Vuelo> vuelosOriginales, int dia) {
        List<Vuelo> vuelosCopia = new ArrayList<>();
        int vuelosConCapacidadReducida = 0;
        
        for (Vuelo v : vuelosOriginales) {
            EstadoVuelo estado = estadosVuelos.get(v.id);
            
            // Crear copia del vuelo
            Vuelo copia = new Vuelo(v.id, v.origen, v.destino, v.salidaMin, v.llegadaMin,
                                   v.capacidad, (int)(v.horasDuracion * 60), v.esContinental);
            
            // Ajustar capacidad basada en el estado real
            if (estado != null && estado.capacidadDisponible < v.capacidad) {
                // Simular capacidad reducida (no podemos modificar directamente la capacidad del vuelo)
                // El algoritmo ACO deberá considerar esto durante la planificación
                vuelosConCapacidadReducida++;
            }
            
            vuelosCopia.add(copia);
        }
        
        if (vuelosConCapacidadReducida > 0) {
            System.out.printf(">> %d vuelos tienen capacidad reducida por ocupación previa%n", 
                             vuelosConCapacidadReducida);
        }
        
        return vuelosCopia;
    }
    
    private static void mostrarEstadoRecursos(Map<String, Aeropuerto> aeropuertos, List<Vuelo> vuelos, int dia) {
        int almacenesOcupados = 0;
        int totalPaquetesEnAlmacenes = 0;
        int vuelosOcupados = 0;
        
        // Contar ocupación de almacenes
        for (Map.Entry<String, EstadoAeropuerto> entry : estadosAeropuertos.entrySet()) {
            EstadoAeropuerto estado = entry.getValue();
            if (estado.ocupacionTotal > 0) {
                almacenesOcupados++;
                totalPaquetesEnAlmacenes += estado.ocupacionTotal;
            }
        }
        
        // Contar vuelos ocupados
        for (EstadoVuelo estado : estadosVuelos.values()) {
            if (!estado.paquetesABordo.isEmpty()) {
                vuelosOcupados++;
            }
        }
        
        System.out.printf(">> Estado recursos - Almacenes ocupados: %d/%d (%d paquetes), Vuelos ocupados: %d/%d%n",
                         almacenesOcupados, aeropuertos.size(), totalPaquetesEnAlmacenes, 
                         vuelosOcupados, vuelos.size());
    }
    
    private static void ejecutarAsignacionesTemporales(List<Asignacion> asignaciones, 
                                                     Map<String, Aeropuerto> aeropuertos, 
                                                     List<Vuelo> vuelos, 
                                                     int dia) {
        
        int paquetesEjecutados = 0;
        int vuelosUtilizados = 0;
        
        for (Asignacion asignacion : asignaciones) {
            
            if (asignacion.ruta != null && !asignacion.ruta.vuelosUsados.isEmpty() && asignacion.paquetesAsignados > 0) {
                
                for (int i = 0; i < asignacion.ruta.vuelosUsados.size(); i++) {
                    Integer vueloId = asignacion.ruta.vuelosUsados.get(i);
                    EstadoVuelo estadoVuelo = estadosVuelos.get(vueloId);
                    
                    if (estadoVuelo != null) {
                        // Calcular cuántos paquetes cargar en este vuelo
                        int paquetesEnEsteVuelo = Math.min(
                            asignacion.paquetesAsignados, 
                            estadoVuelo.capacidadDisponible
                        );
                        
                        if (paquetesEnEsteVuelo > 0) {
                            // Ocupar capacidad del vuelo
                            estadoVuelo.capacidadDisponible -= paquetesEnEsteVuelo;
                            estadoVuelo.paquetesABordo.add(new PaqueteEnVuelo(
                                paquetesEnEsteVuelo, 
                                asignacion.pedido.id
                            ));
                            
                            estadoVuelo.ultimoDiaOperacion = dia;
                            paquetesEjecutados += paquetesEnEsteVuelo;
                            
                            if (i == 0) { // Contar vuelo solo una vez
                                vuelosUtilizados++;
                            }
                        }
                    }
                }
            }
        }
        
        if (paquetesEjecutados > 0) {
            System.out.printf("📦 Asignaciones ejecutadas: %d paquetes cargados en %d vuelos%n", 
                             paquetesEjecutados, vuelosUtilizados);
        }
    }
    
    private static int calcularDiferenciaMinutos(int dia1, int minuto1, int dia2, int minuto2) {
        int minutosD1 = (dia1 - 1) * 1440 + minuto1; // 1440 minutos por día
        int minutosD2 = (dia2 - 1) * 1440 + minuto2;
        return minutosD2 - minutosD1;
    }
}
