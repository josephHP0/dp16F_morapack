package com.morapack.planificador.nucleo;

import com.morapack.planificador.dominio.*;
import com.morapack.planificador.util.UtilArchivos;

import java.nio.file.*;
import java.util.*;


import com.morapack.planificador.util.LectorCancelaciones;
import com.morapack.planificador.dominio.Cancelacion;

public class AppPlanificador {

    public static void main(String[] args) {
        try {
            // Paths (ajusta si usas otros nombres)
            Path pedidosPath       = Paths.get("data/pedidos_semanal.txt");
            Path vuelosPath        = Paths.get("data/vuelos.txt");
            Path cancelacionesPath = Paths.get("data/cancelaciones.txt");

            System.out.println("🔄 Cargando datos...");
            
            // Cargar datos
            List<Pedido> pedidosTodos = UtilArchivos.cargarPedidos(pedidosPath, "SKBO");
            List<GrafoVuelos.Vuelo> vuelos = UtilArchivos.cargarVuelos(vuelosPath);
            Map<Integer, List<Cancelacion>> cancelsPorDia =
                LectorCancelaciones.cargarPorDia(cancelacionesPath);

            System.out.printf("📊 Datos cargados: %d pedidos, %d vuelos, %d días con cancelaciones%n", 
                             pedidosTodos.size(), vuelos.size(), cancelsPorDia.size());

            // OPTIMIZACIÓN: Usar muestra de pedidos para pruebas rápidas
            List<Pedido> pedidos = pedidosTodos.size() > 50 ? 
                pedidosTodos.subList(0, 50) : pedidosTodos;
            System.out.println("⚡ Usando muestra de " + pedidos.size() + " pedidos para optimizar velocidad");

            GrafoVuelos grafo = new GrafoVuelos(vuelos);

            // CONFIGURACIÓN OPTIMIZADA PARA VELOCIDAD
            ParametrosAco params = new ParametrosAco()
                    .ants(8).iter(30)           // Reducido: 8×30 = 240 vs 50×200 = 10,000
                    .alpha(1.0).beta(2.5).rho(0.2).q0(0.15).tau0(0.01)
                    .verbose(false);            // SIN logging detallado

            System.out.println("🚀 Configuración ACO optimizada: 8 hormigas, 30 iteraciones");

            // Planificador + Simulador
            PlanificadorAco planificador = new PlanificadorAco();
            Simulador sim = new Simulador(grafo, planificador, cancelsPorDia);

            // Cronometrar ejecución
            long inicio = System.currentTimeMillis();

            // Solución inicial
            System.out.println("📋 Generando solución inicial...");
            sim.inicializar(pedidos, params);

            // Simulación reducida: solo 3 días para prueba
            System.out.println("⏰ Ejecutando simulación (3 días)...");
            sim.correrSemana(1, 3, params);

            long fin = System.currentTimeMillis();
            double tiempoSegundos = (fin - inicio) / 1000.0;

            // Resultados optimizados
            List<Asignacion> resultado = sim.getAsignaciones();
            
            System.out.printf("%n✅ SIMULACIÓN COMPLETADA EN %.2f SEGUNDOS%n", tiempoSegundos);
            System.out.println("📦 Asignaciones generadas: " + resultado.size());
            
            // Mostrar solo resumen, no todos los detalles
            long asignacionesExitosas = resultado.stream()
                .filter(a -> a.getRuta() != null && a.getRuta().vuelosUsados != null && !a.getRuta().vuelosUsados.isEmpty())
                .count();
            
            System.out.printf("✈️  Asignaciones con ruta: %d/%d (%.1f%%)%n", 
                             asignacionesExitosas, resultado.size(),
                             resultado.size() > 0 ? (asignacionesExitosas * 100.0) / resultado.size() : 0.0);
            
            // Mostrar solo las primeras 10 asignaciones como ejemplo
            System.out.println("\n=== MUESTRA DE ASIGNACIONES (primeras 10) ===");
            for (int i = 0; i < Math.min(10, resultado.size()); i++) {
                System.out.println((i+1) + ". " + resultado.get(i));
            }
            if (resultado.size() > 10) {
                System.out.println("... y " + (resultado.size() - 10) + " asignaciones más.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
