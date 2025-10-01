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

            // Cargar pedidos (HUB SKBO por defecto; cambia según tu caso)
            List<Pedido> pedidos = UtilArchivos.cargarPedidos(pedidosPath, "SKBO");

            // Cargar vuelos
            List<GrafoVuelos.Vuelo> vuelos = UtilArchivos.cargarVuelos(vuelosPath);
            GrafoVuelos grafo = new GrafoVuelos(vuelos);

            // Cargar cancelaciones
            Map<Integer, List<Cancelacion>> cancelsPorDia =
                LectorCancelaciones.cargarPorDia(cancelacionesPath);

            // Configuración ACO (verbose activado)
            ParametrosAco params = new ParametrosAco()
                    .ants(50).iter(200).alpha(1.0).beta(3.0).rho(0.15).q0(0.10).tau0(0.01)
                    .verbose(true);

            // Planificador + Simulador
            PlanificadorAco planificador = new PlanificadorAco();
            Simulador sim = new Simulador(grafo, planificador, cancelsPorDia);

            // Solución inicial
            sim.inicializar(pedidos, params);

            // Corre semana (01..07 ejemplo)
            sim.correrSemana(1, 7, params);

            // Dump final (si necesitas el listado)
            System.out.println("\n=== ASIGNACIONES FINALES ===");
            List<Asignacion> resultado = sim.getAsignaciones();
            for (Asignacion a : resultado) {
                System.out.println(a);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
