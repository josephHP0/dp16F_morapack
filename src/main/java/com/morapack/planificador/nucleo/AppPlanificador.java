package com.morapack.planificador.nucleo;

import com.morapack.planificador.dominio.*;
import com.morapack.planificador.util.UtilArchivos;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class AppPlanificador {
    public static void main(String[] args) throws Exception {
        Map<String,String> arg = Arrays.stream(args)
                .map(s -> s.split("=", 2))
                .filter(a -> a.length==2 && a[0].startsWith("--"))
                .collect(Collectors.toMap(a->a[0].substring(2), a->a[1]));

        Path aeropuertosPath = Paths.get(arg.getOrDefault("aeropuertos", "data/aeropuertos.txt"));
        Path vuelosPath      = Paths.get(arg.getOrDefault("vuelos", "data/vuelos.txt"));
        Path pedidosPath     = Paths.get(arg.getOrDefault("pedidos", "data/pedidos.txt"));
        Path salidaPath      = Paths.get(arg.getOrDefault("salida", "plan_asignacion.csv"));

        Map<String,Aeropuerto> aeropuertos = UtilArchivos.cargarAeropuertos(aeropuertosPath);
        if (aeropuertos.isEmpty()) throw new IllegalArgumentException("No se cargaron aeropuertos.");
        List<Vuelo> vuelos = UtilArchivos.cargarVuelos(vuelosPath, aeropuertos);
        if (vuelos.isEmpty()) throw new IllegalArgumentException("No se cargaron vuelos válidos.");

        List<Pedido> pedidos = (pedidosPath!=null && Files.exists(pedidosPath))
                ? UtilArchivos.cargarPedidos(pedidosPath, aeropuertos.keySet())
                : UtilArchivos.generarPedidosSinteticos(aeropuertos.keySet(), PlanificadorAco.HUBS.keySet(), 40, 7L);

        ParametrosAco p = new ParametrosAco();
        // if (arg.containsKey("alpha")) p.alpha = Double.parseDouble(arg.get("alpha"));
        // if (arg.containsKey("beta")) p.beta = Double.parseDouble(arg.get("beta"));
        // if (arg.containsKey("rho")) p.rho = Double.parseDouble(arg.get("rho"));
        // if (arg.containsKey("Q")) p.Q = Double.parseDouble(arg.get("Q"));
        // if (arg.containsKey("hormigas")) p.hormigas = Integer.parseInt(arg.get("hormigas"));
        // if (arg.containsKey("iteraciones")) p.iteraciones = Integer.parseInt(arg.get("iteraciones"));
        // if (arg.containsKey("pasosMax")) p.pasosMax = Integer.parseInt(arg.get("pasosMax"));
        p.alpha = 1.0;
        p.beta = 2.0;
        p.rho = 0.5;
        p.Q = 100.0;
        p.hormigas = 20;
        p.iteraciones = 50;
        p.pasosMax = 30;


        List<Asignacion> plan = PlanificadorAco.planificarConAco(aeropuertos, vuelos, pedidos, p, 7L);
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