package com.morapack.backend.service;

import com.morapack.backend.model.Aeropuerto;
import com.morapack.backend.model.Pedido;
import com.morapack.backend.model.Vuelo;
import com.morapack.backend.util.UtilArchivos;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Servicio principal que orquesta la carga de datos y la ejecución
 * del algoritmo de planificación (ACO).
 * Usará Repositorios en un entorno de DB real.
 */
@Service
public class PlanificadorService {

    // Aquí se inyectarían Repositorios y otras dependencias.
    // private final PedidoRepository pedidoRepository;

    /**
     * Inicia una corrida de planificación completa.
     * @param parametros Configuraciones del algoritmo ACO.
     * @param diaInicio Día de inicio de la simulación.
     * @param diasTotal Días totales a simular.
     * @return El plan de asignación consolidado.
     */
    public List<Asignacion> iniciarCorridaCompleta(ParametrosAco parametros, int diaInicio, int diasTotal) throws Exception {

        // --- Carga de datos (Temporalmente desde archivos, se migrará a Repositorios) ---
        // Asumiendo rutas por defecto o cargadas desde la configuración de Spring
        Path aeropuertosPath = Paths.get("data/aeropuertos.txt");
        Path vuelosPath      = Paths.get("data/vuelos.txt");
        Path pedidosPath     = Paths.get("data/pedidos_1.txt");
        Path cancelacionesPath = Paths.get("data/cancelaciones.txt");

        Map<String,Aeropuerto> aeropuertos = UtilArchivos.cargarAeropuertos(aeropuertosPath);
        if (aeropuertos.isEmpty()) throw new IllegalArgumentException("No se cargaron aeropuertos.");
        List<Vuelo> vuelos = UtilArchivos.cargarVuelos(vuelosPath, aeropuertos);
        if (vuelos.isEmpty()) throw new IllegalArgumentException("No se cargaron vuelos válidos.");

        List<Pedido> pedidos = (pedidosPath!=null && Files.exists(pedidosPath))
                ? UtilArchivos.cargarPedidos(pedidosPath, aeropuertos.keySet())
                : UtilArchivos.generarPedidosSinteticos(aeropuertos.keySet(), PlanificadorAco.HUBS.keySet(), 40, 7L);

        // --- Ejecutar Simulación ---
        long semilla = 7L; // Semilla fija para reproducibilidad

        List<Asignacion> plan = PlanificadorAco.ejecutarSimulacionConSLA(
                aeropuertos,
                vuelos,
                pedidos,
                parametros,
                cancelacionesPath,
                diaInicio,
                diasTotal,
                semilla
        );

        // --- Post-ejecución ---
        AnalisisAlgoritmo.analizarProblemasEspecificos(plan, pedidos);

        return plan;
    }

    // Aquí irían otros métodos como guardarPlan(plan), obtenerMetricas(corridaId), etc.
}