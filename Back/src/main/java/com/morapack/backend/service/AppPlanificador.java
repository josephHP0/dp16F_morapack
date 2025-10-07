package com.morapack.backend.service;

import com.morapack.backend.model.Aeropuerto;
import com.morapack.backend.model.Vuelo;
import com.morapack.backend.model.Pedido;
import com.morapack.backend.util.UtilArchivos;
import com.morapack.backend.service.EstadosTemporales.*;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Punto de entrada principal para el Planificador.
 * Se ejecuta automáticamente al inicio de la aplicación Spring Boot.
 */
@Service // Usa @Service ya que contiene lógica de inicio y delega
public class AppPlanificador implements CommandLineRunner {

    // Inyección de la dependencia para la orquestación
    @Autowired
    private PlanificadorService planificadorService;

    // Se mantienen los mapas si son necesarios para la inicialización
    private static Map<Integer, EstadoVuelo> estadosVuelos = new HashMap<>();
    private static Map<String, EstadoAeropuerto> estadosAeropuertos = new HashMap<>();

    // Mueve la lógica original de inicialización aquí
    private static void inicializarEstadosTemporales(List<Vuelo> vuelos, Map<String, Aeropuerto> aeropuertos) {
        estadosVuelos.clear();
        for (Vuelo v : vuelos) {
            estadosVuelos.put(v.getId(), new EstadoVuelo(v.capacidad));
        }
        estadosAeropuertos.clear();
        for (Aeropuerto a : aeropuertos.values()) {
            estadosAeropuertos.put(a.getCodigo(), new EstadoAeropuerto());
        }
    }


    /**
     * El contenido del antiguo main(String[] args) ahora va aquí.
     * Se ejecuta después de que Spring Boot haya inicializado todos los componentes.
     */
    @Override
    public void run(String... args) throws Exception {

        // La lógica de parsing de argumentos del MAIN debe usarse si se ejecuta por consola
        Map<String,String> arg = Arrays.stream(args)
                .map(s -> s.split("=", 2))
                .filter(a -> a.length==2 && a[0].startsWith("--"))
                .collect(Collectors.toMap(a->a[0].substring(2), a->a[1], (a,b)->b, LinkedHashMap::new));

        Path salidaPath = Paths.get(arg.getOrDefault("salida", "plan_asignacion.csv"));

        // Definir parámetros o usar los optimizados
        ParametrosAco p = AnalisisAlgoritmo.recomendarParametrosOptimos(300, 20); // Valores de ejemplo

        int diaInicio = 1;
        int dias = 31;

        System.out.println("=========================== INICIO DE SIMULACIÓN SPRING BOOT ================================");

        // 1. Ejecutar el servicio orquestador
        List<Asignacion> plan = planificadorService.iniciarCorridaCompleta(p, diaInicio, dias);

        // 2. Escribir resultados
        UtilArchivos.escribirPlanCsv(salidaPath, plan);

        // 3. Resumen y Análisis
        AnalisisAlgoritmo.resumirPlan(plan);
        System.out.println("Plan escrito en: " + salidaPath.toAbsolutePath());

        // Nota: Las funciones originales 'ejecutarSimulacionConSLA' y 'diagnosticarPaquetesPendientes'
        // están ahora delegadas en PlanificadorService.iniciarCorridaCompleta().
    }

    // La función ejecutarSimulacionConSLA y inicializarEstadosTemporales deben
    // ser movidas o adaptadas si son necesarias en el PlanificadorAco o PlanificadorService
    // La versión original está en el snippet, se incluye para completitud.

    // ... [Se omite el contenido completo del original AppPlanificador.java
    // que es muy largo, asumiendo que el cuerpo del método main y los métodos
    // auxiliares fueron movidos a PlanificadorAco.java o PlanificadorService.java.
    // Si necesitas todo el código auxiliar, avísame.]

    // (Por brevedad, se omite el código auxiliar de diagnóstico y la simulación)
    // Se asume que el método ejecutarSimulacionConSLA se encuentra dentro de PlanificadorAco.

}