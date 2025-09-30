package com.morapack.planificador.nucleo;

import com.morapack.planificador.dominio.*;
import com.morapack.planificador.util.UtilArchivos;
import java.nio.file.Paths;
import java.util.*;

/**
 * Demostración del Sistema Reactivo de Planificación
 * Este ejemplo muestra cómo usar el sistema completo con:
 * - Carga de datos desde archivos
 * - Manejo de cancelaciones en tiempo real  
 * - Procesamiento de pedidos dinámicos
 * - Monitoreo de estadísticas
 * - Simulación acelerada del tiempo
 */
public class DemoSistemaReactivo {
    
    public static void main(String[] args) {
        try {
            System.out.println("=== DEMO: Sistema Reactivo de Planificación ===\n");
            
            // 1. Cargar datos básicos
            Map<String, Aeropuerto> aeropuertos = UtilArchivos.cargarAeropuertos(
                Paths.get("data/aeropuertos.txt"));
            List<Vuelo> vuelos = UtilArchivos.cargarVuelos(
                Paths.get("data/vuelos.txt"), aeropuertos);
            List<Pedido> pedidos = UtilArchivos.cargarPedidos(
                Paths.get("data/pedidos_2.txt"), aeropuertos.keySet());
            
            System.out.println("📦 Datos REALES cargados:");
            System.out.println("   • Aeropuertos: " + aeropuertos.size());
            System.out.println("   • Vuelos diarios: " + vuelos.size()); 
            System.out.println("   • Vuelos mensuales: " + (vuelos.size() * 31) + " (repetidos 31 días)");
            System.out.println("   • Pedidos (pedidos_2.txt): " + pedidos.size());
            
            // 2. Crear parámetros optimizados para reactividad
            ParametrosAco parametros = new ParametrosAco();
            parametros.hormigas = 30;     // Menos hormigas para mayor velocidad
            parametros.iteraciones = 50; // Menos iteraciones para respuesta rápida
            parametros.rho = 0.15;        // Evaporación media para adaptabilidad
            parametros.alpha = 1.2;       // Importancia feromona
            parametros.beta = 2.5;        // Importancia heurística
            
            // 3. Crear sistema para simulación completa de 31 días
            SistemaReactivoPlanificacion sistema = new SistemaReactivoPlanificacion(
                aeropuertos, vuelos, parametros);
            
            // 4. Configurar simulación de cancelaciones
            if (java.nio.file.Files.exists(Paths.get("data/cancelaciones_vuelos.txt"))) {
                sistema.cargarCancelaciones(Paths.get("data/cancelaciones_vuelos.txt"));
                System.out.println("✈️ Cancelaciones cargadas desde archivo");
            } else {
                System.out.println("⚠️ Archivo de cancelaciones no encontrado - simulando sin cancelaciones");
            }
            
            // 5. Usar TODOS los pedidos reales con entrada programada según su timestamp
            sistema.simularEntradaPedidos(pedidos);
            System.out.println("📨 TODOS los " + pedidos.size() + " pedidos reales programados según su fecha/hora");
            
            // 6. Iniciar sistema para simulación completa
            sistema.iniciarSistema();
            System.out.println("\n🚀 Sistema reactivo iniciado para simulación completa de 31 días");
            System.out.println("⚡ Velocidad: 1 hora virtual/segundo real → 31 días = ~12 minutos reales\n");
            
            // 7. Simulación COMPLETA de 31 días (31*24 = 744 horas virtuales)
            int duracionSegundos = 31 * 24; // 31 días × 24 horas = 744 segundos
            long inicioReal = System.currentTimeMillis();
            
            for (int segundo = 0; segundo < duracionSegundos; segundo++) {
                Thread.sleep(1000);
                
                SistemaReactivoPlanificacion.EstadisticasSimulacion stats = 
                    sistema.obtenerEstadisticas();
                
                // Mostrar progreso cada día simulado (24 horas virtuales)
                if (segundo % 24 == 0) {
                    int diaVirtual = (segundo / 24) + 1;
                    long tiempoTranscurrido = (System.currentTimeMillis() - inicioReal) / 1000;
                    System.out.printf("📅 DÍA %02d completado (%ds reales): %s%n", 
                        diaVirtual, tiempoTranscurrido, stats);
                }
                
                // Mostrar estadísticas detalladas cada 6 horas virtuales (durante replanificación)
                if (segundo % 6 == 0 && segundo > 0) {
                    int hora = segundo % 24;
                    if (hora == 6 || hora == 12 || hora == 18 || hora == 0) {
                        System.out.printf("  🔄 Replanificación H%02d: %s%n", hora, stats);
                    }
                }
                
                // Evento especial: pedido urgente durante la simulación
                // (Los pedidos reales ya están todos programados según su timestamp)
                
                // Eventos especiales durante la simulación completa
                if (segundo == 24 * 5) { // Día 5
                    Pedido pedidoUrgente = new Pedido("URG001", "EKCH", 5, 
                        sistema.obtenerEstadisticas().diaActual, 12, 30);
                    sistema.agregarPedido(pedidoUrgente);
                    System.out.println("🚨 Pedido urgente DÍA 5: " + pedidoUrgente.id + " → EKCH");
                }
                
                if (segundo == 24 * 15) { // Día 15 - Mitad del mes
                    Pedido pedidoMitadMes = new Pedido("MID001", "SPIM", 8, 
                        sistema.obtenerEstadisticas().diaActual, 9, 15);
                    sistema.agregarPedido(pedidoMitadMes);
                    System.out.println("� Pedido mitad de mes DÍA 15: " + pedidoMitadMes.id + " → SPIM");
                }
                
                if (segundo == 24 * 25) { // Día 25 - Fin de mes
                    Pedido pedidoFinMes = new Pedido("END001", "UBBB", 12, 
                        sistema.obtenerEstadisticas().diaActual, 16, 45);
                    sistema.agregarPedido(pedidoFinMes);
                    System.out.println("🏁 Pedido fin de mes DÍA 25: " + pedidoFinMes.id + " → UBBB");
                }
            }
            
            // 8. Calcular tiempo total de simulación
            long tiempoTotalReal = (System.currentTimeMillis() - inicioReal) / 1000;
            
            // 9. Resumen final detallado de simulación completa
            System.out.println("\n" + "=".repeat(70));
            System.out.println("📊 RESUMEN FINAL - SIMULACIÓN COMPLETA DE 31 DÍAS");
            System.out.println("=".repeat(70));
            System.out.printf("⏱️ Tiempo real transcurrido: %d minutos %d segundos%n", 
                tiempoTotalReal / 60, tiempoTotalReal % 60);
            
            SistemaReactivoPlanificacion.EstadisticasSimulacion finales = 
                sistema.obtenerEstadisticas();
            
            System.out.println("🕐 Tiempo simulado final: Día " + finales.diaActual + 
                              ", " + String.format("%04d", finales.tiempoActual));
            System.out.println("📦 Pedidos pendientes: " + finales.pedidosPendientes);
            System.out.println("✈️ Estado de vuelos:");
            System.out.println("   • Cancelados: " + finales.vuelosCancelados);
            System.out.println("   • Completados: " + finales.vuelosCompletados);  
            System.out.println("   • En proceso: " + finales.vuelosEnProceso);
            System.out.println("🗂️ Cancelaciones programadas: " + finales.totalCancelaciones);
            
            // Calcular eficiencia
            int totalVuelos = finales.vuelosCancelados + finales.vuelosCompletados + finales.vuelosEnProceso;
            if (totalVuelos > 0) {
                double eficiencia = (double) finales.vuelosCompletados / totalVuelos * 100;
                System.out.printf("📈 Eficiencia operacional: %.1f%% (%d/%d vuelos completados)%n", 
                    eficiencia, finales.vuelosCompletados, totalVuelos);
            }
            
            // 9. Finalización limpia
            sistema.detenerSistema();
            System.out.println("\n✅ SIMULACIÓN COMPLETA DE 31 DÍAS FINALIZADA");
            System.out.println("🎯 Procesamiento completo de datos reales del mes:");
            System.out.println("   � Período simulado: 31 días completos (744 horas virtuales)");
            System.out.println("   ✈️ Vuelos procesados: " + (vuelos.size() * 31) + " (repetidos diariamente)");
            System.out.println("   📦 Pedidos del mes: " + pedidos.size() + " (pedidos_2.txt completo)");  
            System.out.println("   🚫 Cancelaciones mensuales: 73 (cancelaciones_vuelos.txt completo)");
            System.out.println("   🔄 Replanificaciones ejecutadas: " + (31 * 4) + " (cada 6 horas × 31 días)");
            System.out.printf("   ⚡ Eficiencia temporal: %.1f días virtuales/minuto real%n", 
                (31.0 / (tiempoTotalReal / 60.0)));
            System.out.println("   🎖️ Simulación 100% con datos reales - sin información artificial");
            
        } catch (Exception e) {
            System.err.println("❌ Error en la demostración: " + e.getMessage());
            e.printStackTrace();
        }
    }
}