package com.morapack.planificador.app;

import com.morapack.planificador.dominio.Aeropuerto;
import com.morapack.planificador.dominio.Pedido;
import com.morapack.planificador.dominio.Vuelo;
import com.morapack.planificador.util.UtilArchivos;
import com.morapack.planificador.simulation.CancellationRecord;

import java.nio.file.Path;
import java.time.YearMonth;
import java.util.*;

public class TestLectura {
    public static void main(String[] args) {
        try {
            System.out.println("=== PROBANDO LECTURA DE ARCHIVOS ===");
            
            // Rutas de archivos
            Path aeropTxt = Path.of("data/aeropuertos.txt");
            Path vuelosTxt = Path.of("data/vuelos.txt");
            Path pedidosTxt = Path.of("data/pedidos2.txt");
            Path cancelTxt = Path.of("data/cancelaciones.txt");
            
            System.out.println("Rutas de archivos:");
            System.out.println("- Aeropuertos: " + aeropTxt + " (existe: " + java.nio.file.Files.exists(aeropTxt) + ")");
            System.out.println("- Vuelos: " + vuelosTxt + " (existe: " + java.nio.file.Files.exists(vuelosTxt) + ")");
            System.out.println("- Pedidos: " + pedidosTxt + " (existe: " + java.nio.file.Files.exists(pedidosTxt) + ")");
            System.out.println("- Cancelaciones: " + cancelTxt + " (existe: " + java.nio.file.Files.exists(cancelTxt) + ")");
            System.out.println();
            
            // 1. Cargar aeropuertos
            System.out.println("1. Cargando aeropuertos...");
            Map<String, Aeropuerto> aeropuertos = UtilArchivos.leerAeropuertos(aeropTxt);
            System.out.println("   Aeropuertos cargados: " + aeropuertos.size());
            aeropuertos.forEach((codigo, aero) -> {
                String infiniteFlag = aero.isInfiniteSource() ? " [HUB]" : "";
                System.out.println("   - " + codigo + ": " + aero.getNombre() + 
                                 " (lat: " + aero.lat + ", lon: " + aero.lon + 
                                 ", capacidad: " + aero.getCapacidadAlmacen() + ")" + infiniteFlag);
            });
            System.out.println();
            
            // 2. Cargar vuelos
            System.out.println("2. Cargando vuelos...");
            List<Vuelo> vuelos = UtilArchivos.leerVuelos(vuelosTxt);
            System.out.println("   Vuelos cargados: " + vuelos.size());
            vuelos.stream().limit(5).forEach(vuelo -> {
                System.out.println("   - " + vuelo.origen + " -> " + vuelo.destino + 
                                 " (salida: " + vuelo.salidaMin + ", llegada: " + vuelo.llegadaMin + 
                                 ", capacidad: " + vuelo.capacidad + ")");
            });
            if (vuelos.size() > 5) {
                System.out.println("   ... y " + (vuelos.size() - 5) + " más");
            }
            System.out.println();
            
            // 3. Cargar pedidos
            System.out.println("3. Cargando pedidos...");
            YearMonth periodo = YearMonth.of(2025, 11);
            List<Pedido> pedidos = UtilArchivos.leerPedidos(pedidosTxt, periodo);
            System.out.println("   Pedidos cargados: " + pedidos.size());
            pedidos.stream().limit(5).forEach(pedido -> {
                System.out.println("   - " + pedido.id + " -> " + pedido.destinoIata + 
                                 " (cantidad: " + pedido.paquetes + 
                                 ", fecha: " + pedido.dia + "/" + pedido.hora + ":" + pedido.minuto + ")");
            });
            if (pedidos.size() > 5) {
                System.out.println("   ... y " + (pedidos.size() - 5) + " más");
            }
            System.out.println();
            
            // 4. Cargar cancelaciones (si existe)
            if (java.nio.file.Files.exists(cancelTxt)) {
                System.out.println("4. Cargando cancelaciones...");
                List<CancellationRecord> cancels = UtilArchivos.leerCancelaciones(cancelTxt, aeropuertos, periodo);
                System.out.println("   Cancelaciones cargadas: " + cancels.size());
                cancels.stream().limit(3).forEach(cancel -> {
                    System.out.println("   - " + cancel);
                });
                if (cancels.size() > 3) {
                    System.out.println("   ... y " + (cancels.size() - 3) + " más");
                }
            } else {
                System.out.println("4. Archivo de cancelaciones no existe, saltando...");
            }
            
            System.out.println();
            System.out.println("=== LECTURA COMPLETADA EXITOSAMENTE ===");
            
        } catch (Exception e) {
            System.err.println("ERROR durante la lectura:");
            e.printStackTrace();
        }
    }
}