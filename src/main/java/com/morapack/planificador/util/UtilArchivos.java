package com.morapack.planificador.util;

import com.morapack.planificador.dominio.*;
import com.morapack.planificador.nucleo.Asignacion;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class UtilArchivos {
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("H:mm");

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

    public static Map<String, Aeropuerto> cargarAeropuertos(Path p) throws IOException {
        Map<String, Aeropuerto> mapa = new HashMap<>();
        for (String linea : Files.readAllLines(p)) {
            if (linea == null || linea.isBlank()) continue; // evita líneas vacías
            Aeropuerto aeropuerto = parseAeropuerto(linea);
            mapa.put(aeropuerto.getCodigo(), aeropuerto);
        }
        System.out.println("Aeropuertos cargados: " + String.join(", ", mapa.keySet())); // Debug line
        return mapa;
    }

    public static Aeropuerto parseAeropuerto(String linea) {
        String[] partes = linea.split(",");
        int id = Integer.parseInt(partes[0].trim());
        String codigo = partes[1].trim();
        String ciudad = partes[2].trim();
        String pais = partes[3].trim();
        String abrev = partes[4].trim();
        int gmt = Integer.parseInt(partes[5].trim());
        int capacidad = Integer.parseInt(partes[6].trim());
        String latitud = partes[7].trim();
        String longitud = partes[8].trim();
        String continente = partes[9].trim(); // Nuevo campo
        return new Aeropuerto(id, codigo, ciudad, pais, abrev, gmt, capacidad, latitud, longitud, continente, 0);
    }

    public static List<Vuelo> cargarVuelos(Path p, Map<String, Aeropuerto> aeropuertos) throws IOException {
        List<Vuelo> vuelos = new ArrayList<>();
        int id = 0;
        for (String linea : Files.readAllLines(p)) {
            String[] f = partirInteligente(linea);
            if (f.length < 5) continue;
            String origen = f[0].trim();
            String destino = f[1].trim();
            if (!aeropuertos.containsKey(origen) || !aeropuertos.containsKey(destino)) continue;

            int salida = hhmmAMinutos(f[2]);
            int llegada = hhmmAMinutos(f[3]);
            int capacidad = parsearEntero(f[4]);

            // Obtener GMT de origen y destino
            int gmtOrigen = aeropuertos.get(origen).getGmt();
            int gmtDestino = aeropuertos.get(destino).getGmt();

            // Ajustar llegada a la zona horaria del origen
            int llegadaEnOrigen = llegada - (gmtDestino - gmtOrigen) * 60;

            // Calcular duración real
            int duracion = llegadaEnOrigen - salida;
            if (duracion < 0) duracion += 24 * 60; // si cruza medianoche

            // ...existing code...
            String continenteOrigen = aeropuertos.get(origen).getContinente();
            String continenteDestino = aeropuertos.get(destino).getContinente();
            boolean esContinental = continenteOrigen.equals(continenteDestino);
            // ...puedes guardar este dato en el objeto Vuelo o usarlo para lógica de restricciones...

            // Crea el vuelo usando la duración real
            vuelos.add(new Vuelo(id++, origen, destino, salida, llegada, capacidad, duracion,esContinental));
        }
        return vuelos;
    }

    public static List<Pedido> cargarPedidos(Path p, Set<String> iatasValidas) throws IOException {
        List<Pedido> pedidos = new ArrayList<>();
        if (p == null || !Files.exists(p)) return pedidos;
        for (String linea : Files.readAllLines(p)) {
            if (linea.trim().isEmpty() || linea.startsWith("#")) continue;
            
            // Format: dd-hh-mm-DEST-XXX-YYYYYYY
            // dd: day (01-31)
            // hh: hour (00-23)
            // mm: minutes (00-59)
            // DEST: airport code (4 chars)
            // XXX: quantity (001-999)
            // YYYYYYY: client ID (7 digits)
            
            try {
                String[] partes = linea.split("-");
                if (partes.length != 6) continue;
                
                int dia = Integer.parseInt(partes[0]);
                int hora = Integer.parseInt(partes[1]);
                int minuto = Integer.parseInt(partes[2]);
                String dest = partes[3].trim();
                int cantidad = Integer.parseInt(partes[4]);
                String clientId = partes[5].trim();
                
                // Validate data
                if (dia < 1 || dia > 31 || hora < 0 || hora > 23 || 
                    minuto < 0 || minuto > 59 || cantidad < 1 || 
                    !iatasValidas.contains(dest)) {
                    System.out.println("Skipping invalid line: " + linea); // Debug line
                    continue;
                }
                
                pedidos.add(new Pedido(clientId, dest, cantidad, dia, hora, minuto));
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                System.out.println("Error parsing line: " + linea + " - " + e.getMessage()); // Debug line
                continue;
            }
        }
        return pedidos;
    }

    public static List<Pedido> generarPedidosSinteticos(Set<String> iatas, Set<String> hubs, int n, long semilla) {
        Random rnd = new Random(semilla);
        List<String> destinos = iatas.stream().filter(a -> !hubs.contains(a)).sorted().collect(Collectors.toList());
        List<Pedido> lista = new ArrayList<>();
        for (int i=1;i<=n;i++) {
            String d = destinos.get(rnd.nextInt(destinos.size()));
            int pk = 1 + rnd.nextInt(10);
            int dia = 1 + rnd.nextInt(28); // Random day in month
            int hora = rnd.nextInt(24);     // Random hour
            int minuto = rnd.nextInt(60);   // Random minute
            lista.add(new Pedido(String.format("%07d", i), d, pk, dia, hora, minuto));
        }
        return lista;
    }

    public static void escribirAsignacionesCSV(Path out, List<com.morapack.planificador.nucleo.Asignacion> asgs) throws IOException {
        Files.createDirectories(out.getParent() == null ? Paths.get(".") : out.getParent());
        try (BufferedWriter bw = Files.newBufferedWriter(out)) {
            bw.write("order_id,hub_origen,destino,paquetes_asignados,paquetes_pendientes,hops,ruta,horas_estimadas,itinerario\n");
            for (var a : asgs) {
                String ruta = (a.ruta==null) ? "" : String.join(" > ", a.ruta.nodos);
                int hops = (a.ruta==null) ? 0 : Math.max(0, a.ruta.nodos.size()-1);
                double h = (a.ruta==null) ? Double.NaN : Math.round(a.ruta.horasTotales*100.0)/100.0;
                String iti = (a.ruta==null || a.ruta.itinerario.isEmpty()) ? "" : String.join(" | ", a.ruta.itinerario);

                bw.write(String.format(Locale.US,
                        "%s,%s,%s,%d,%d,%d,%s,%.2f,%s\n",
                        a.pedido.id, a.hubOrigen, a.pedido.destinoIata,
                        a.paquetesAsignados, a.paquetesPendientes, hops, ruta, h, iti));
            }
        }
    }
    public static void escribirPlanCsv(Path salidaPath, List<Asignacion> plan) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(salidaPath)) {
            writer.write("pedido_id,fecha_pedido,hub_origen,destino,ruta,paquetes_asignados,paquetes_pendientes,tiempo_entrega\n");
            for (Asignacion asg : plan) {
                String rutaStr = (asg.ruta == null) ? "" : String.join(" | ", asg.ruta.itinerario);
                // Format fecha as dd/HH:mm
                String fechaPedido = String.format("%02d/%02d:%02d", 
                    asg.pedido.dia, asg.pedido.hora, asg.pedido.minuto);
                // Calculate delivery time based on route total hours
                double horasEntrega = (asg.ruta == null) ? 0.0 : asg.ruta.horasTotales;
                writer.write(String.format(Locale.US, "%s,%s,%s,%s,%s,%d,%d,%.2f\n",
                        asg.pedido.id,
                        fechaPedido,
                        asg.hubOrigen,
                        asg.pedido.destinoIata,
                        rutaStr,
                        asg.paquetesAsignados,
                        asg.paquetesPendientes,
                        horasEntrega));
            }
        }
    }

    public static double distanciaKm(Aeropuerto a1, Aeropuerto a2) {
        double lat1 = parseLatLon(a1.getLatitud());
        double lon1 = parseLatLon(a1.getLongitud());
        double lat2 = parseLatLon(a2.getLatitud());
        double lon2 = parseLatLon(a2.getLongitud());
        double R = 6371.0; // Radio de la Tierra en km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double la1 = Math.toRadians(lat1);
        double la2 = Math.toRadians(lat2);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2) +
                Math.sin(dLon/2)*Math.sin(dLon/2)*Math.cos(la1)*Math.cos(la2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }

    // Convierte "04°42'05\"N" o "74°08'49\"W" a decimal
    public static double parseLatLon(String s) {
        s = s.replace("°", " ").replace("'", " ").replace("\"", " ");
        String[] parts = s.trim().split("\\s+");
        double deg = Double.parseDouble(parts[0]);
        double min = Double.parseDouble(parts[1]);
        double sec = Double.parseDouble(parts[2]);
        double sign = (s.contains("S") || s.contains("W")) ? -1 : 1;
        return sign * (deg + min/60.0 + sec/3600.0);
    }
}
