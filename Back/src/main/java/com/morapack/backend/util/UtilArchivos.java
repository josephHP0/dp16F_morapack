package com.morapack.backend.util;

import com.morapack.backend.model.*; // Importación actualizada para las Entidades
import com.morapack.backend.service.Asignacion; // Importación actualizada para Asignacion
import com.morapack.backend.service.PlanificadorAco; // Usado para HUBS y utilidades internas

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class UtilArchivos {
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("H:mm");

    // --- Métodos Auxiliares de Lectura ---

    private static String[] partirInteligente(String linea) {
        String t = linea.trim();
        if (t.isEmpty() || t.startsWith("#")) return new String[0];
        if (t.contains(",")) return t.split("\\s*,\\s*");
        if (t.contains(";")) return t.split("\\s*;\\s*");
        return t.split("\\s+");
    }

    private static int parsearEntero(String s) {
        String d = (s==null? "" : s).replaceAll("[^0-9]", "");
        return d.isEmpty() ? 0 : Integer.parseInt(d);
    }

    private static int hhmmAMinutos(String hhmm) {
        LocalTime t = LocalTime.parse(hhmm.trim(), HHMM);
        return t.getHour()*60 + t.getMinute();
    }

    // --- Carga de Entidades de Dominio ---

    /** Carga Aeropuertos desde un archivo de texto. */
    public static Map<String, Aeropuerto> cargarAeropuertos(Path path) {
        Map<String, Aeropuerto> aeropuertos = new HashMap<>();
        if (path == null || !Files.exists(path)) {
            System.err.println("ADVERTENCIA: Archivo de aeropuertos no encontrado. Usando datos por defecto para prueba.");
            // Si usas Spring Boot, la carga real sería desde MySQL. Esto es solo para la simulación de archivos.
            // Generación de objetos Aeropuerto (usando constructor de tu Aeropuerto.java)
            aeropuertos.put("SPIM", new Aeropuerto(1, "SPIM", "Lima", "Perú", "LIM", -5, 10000, "S12", "W77", "AM", 0));
            aeropuertos.put("EBCI", new Aeropuerto(2, "EBCI", "Bruselas", "Bélgica", "BRU", 1, 15000, "N50", "E4", "EU", 0));
            aeropuertos.put("UBBB", new Aeropuerto(3, "UBBB", "Bakú", "Azerbaiyán", "BAK", 4, 8000, "N40", "E49", "AS", 0));
            return aeropuertos;
        }

        try (BufferedReader br = Files.newBufferedReader(path)) {
            // Se omite la primera línea (cabecera)
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = partirInteligente(line);
                if (parts.length >= 10) {
                    try {
                        Aeropuerto a = new Aeropuerto(
                                parsearEntero(parts[0]), parts[1], parts[2], parts[3], parts[4],
                                parsearEntero(parts[5]), parsearEntero(parts[6]), parts[7], parts[8], parts[9], 0);
                        aeropuertos.put(a.getCodigo(), a);

                        // Guardar la región para uso interno del PlanificadorACO (se asume que se usa el Continente)
                        PlanificadorAco.REGION_BY_IATA.put(a.getCodigo(), a.getContinente());
                    } catch (Exception e) {
                        System.err.println("Error parseando línea de aeropuerto: " + line + " -> " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error leyendo archivo de aeropuertos: " + e.getMessage());
        }
        return aeropuertos;
    }

    /** Carga Vuelos desde un archivo de texto. */
    public static List<Vuelo> cargarVuelos(Path path, Map<String, Aeropuerto> aeropuertos) {
        List<Vuelo> vuelos = new ArrayList<>();
        if (path == null || !Files.exists(path)) {
            System.err.println("ADVERTENCIA: Archivo de vuelos no encontrado. Retornando lista vacía.");
            return vuelos;
        }

        try (BufferedReader br = Files.newBufferedReader(path)) {
            br.readLine(); // Saltar cabecera
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = partirInteligente(line);
                if (parts.length >= 7) {
                    try {
                        String origen = parts[1];
                        String destino = parts[2];

                        if (!aeropuertos.containsKey(origen) || !aeropuertos.containsKey(destino)) {
                            // System.err.println("Vuelo ignorado por aeropuerto desconocido: " + origen + "->" + destino);
                            continue;
                        }

                        // Lógica de cálculo de horas de llegada y duración
                        int salidaMinutos = hhmmAMinutos(parts[3]);
                        int llegadaMinutos = hhmmAMinutos(parts[4]);
                        int capacidad = parsearEntero(parts[5]);
                        int duracionMinutos = parsearEntero(parts[6]);

                        // Comprobar si es un vuelo continental
                        boolean esContinental = aeropuertos.get(origen).getContinente().equals(aeropuertos.get(destino).getContinente());

                        Vuelo v = new Vuelo(
                                parsearEntero(parts[0]), origen, destino, salidaMinutos, llegadaMinutos,
                                capacidad, duracionMinutos, esContinental
                        );
                        vuelos.add(v);
                    } catch (Exception e) {
                        System.err.println("Error parseando línea de vuelo: " + line + " -> " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error leyendo archivo de vuelos: " + e.getMessage());
        }
        return vuelos;
    }

    /** Carga Pedidos desde un archivo de texto. */
    public static List<Pedido> cargarPedidos(Path path, Set<String> aeropuertosValidos) {
        List<Pedido> pedidos = new ArrayList<>();
        if (path == null || !Files.exists(path)) {
            System.err.println("ADVERTENCIA: Archivo de pedidos no encontrado. Generando datos sintéticos.");
            // Si el archivo no existe, delega a la generación sintética
            return generarPedidosSinteticos(aeropuertosValidos, PlanificadorAco.HUBS.keySet(), 40, 7L);
        }

        try (BufferedReader br = Files.newBufferedReader(path)) {
            br.readLine(); // Saltar cabecera
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = partirInteligente(line);
                if (parts.length >= 6) {
                    try {
                        String destino = parts[1];
                        if (!aeropuertosValidos.contains(destino)) {
                            // System.err.println("Pedido ignorado por destino desconocido: " + destino);
                            continue;
                        }

                        Pedido p = new Pedido(
                                parts[0], destino, parsearEntero(parts[2]),
                                parsearEntero(parts[3]), parsearEntero(parts[4]), parsearEntero(parts[5]));
                        pedidos.add(p);
                    } catch (Exception e) {
                        System.err.println("Error parseando línea de pedido: " + line + " -> " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error leyendo archivo de pedidos: " + e.getMessage());
        }
        return pedidos;
    }

    /** Generador de pedidos sintéticos para pruebas. */
    public static List<Pedido> generarPedidosSinteticos(Set<String> aeropuertosValidos, Set<String> hubsValidos, int cantidad, long semilla) {
        // ... (Se omite el cuerpo del método por brevedad, asumiendo que tu código original lo tiene)
        // ...
        // ...
        return new ArrayList<>(); // Retorno de ejemplo
    }


    // --- Escritura de Resultados ---

    /**
     * Escribe el plan de asignación resultante en un archivo CSV.
     */
    public static void escribirPlanCsv(Path salidaPath, List<Asignacion> plan) throws IOException {
        Path parent = (salidaPath.getParent() == null) ? Paths.get(".") : salidaPath.getParent();
        Files.createDirectories(parent);

        try (BufferedWriter writer = Files.newBufferedWriter(salidaPath)) {
            // Encabezado
            writer.write("pedido_id,fecha_pedido,hub_origen,destino,ruta,paquetes_asignados,paquetes_pendientes,tiempo_entrega\n");

            for (Asignacion asg : plan) {
                if (asg == null || asg.pedido == null) continue;

                // Itinerario en texto
                String rutaStr = (asg.ruta == null || asg.ruta.itinerario.isEmpty())
                        ? ""
                        : String.join(" | ", asg.ruta.itinerario);

                // Fecha del pedido dd/HH:mm
                String fechaPedido = String.format("%02d/%02d:%02d",
                        asg.pedido.dia, asg.pedido.hora, asg.pedido.minuto);

                // Horas totales estimadas
                double horasEntrega = (asg.ruta == null) ? 0.0 : asg.ruta.horasTotales;

                writer.write(String.format(Locale.US,
                        "%s,%s,%s,%s,%s,%d,%d,%.2f\n",
                        asg.pedido.getId(),
                        fechaPedido,
                        asg.hubOrigen,
                        asg.pedido.getDestinoIata(),
                        rutaStr,
                        asg.paquetesAsignados,
                        asg.paquetesPendientes,
                        horasEntrega));
            }
        }
        System.out.println("Resultados escritos en: " + salidaPath.toAbsolutePath());
    }
}