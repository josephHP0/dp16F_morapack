package com.morapack.planificador.util;

import com.morapack.planificador.dominio.*;
import com.morapack.planificador.nucleo.GrafoVuelos;

import java.nio.file.*;
import java.util.*;

import java.io.IOException;

/**
 * Utilidades de carga/parseo de archivos de texto para el planificador.
 * Formatos soportados:
 *  - Vuelos (CSV): ORIGEN,DESTINO,HH:MM_salida,HH:MM_llegada,capacidad (capacidad opcional)
 *  - Pedidos: dd-hh-mm-DEST-###-IdClien
 *  - (Opcional) Aeropuertos: IATA,Nombre,Lat,Lon   // si lo usas
 */
public class UtilArchivos {

    // =========================================================
    //  HH:MM <-> minutos
    // =========================================================

    /** Wrapper público solicitado por otros módulos. */
    public static int hhmmAMinutosPublic(String hhmm) {
        return hhmmAMinutos(hhmm);
    }

    /** "03:34" -> 214. Devuelve -1 si el formato no es válido. */
    public static int hhmmAMinutos(String hhmm) {
        if (hhmm == null) return -1;
        String s = hhmm.trim();
        if (s.isEmpty()) return -1;
        String[] t = s.split(":");
        if (t.length != 2) return -1;
        try {
            int hh = Integer.parseInt(t[0]);
            int mm = Integer.parseInt(t[1]);
            if (hh < 0 || hh > 23 || mm < 0 || mm > 59) return -1;
            return hh * 60 + mm;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 214 -> "03:34" */
    public static String minutosAHHMM(int minutos) {
        minutos = ((minutos % 1440) + 1440) % 1440; // normaliza
        int hh = minutos / 60;
        int mm = minutos % 60;
        return String.format("%02d:%02d", hh, mm);
    }

    // =========================================================
    //  VUELOS: ORIGEN,DESTINO,HH:MM_salida,HH:MM_llegada,capacidad
    //  Ejemplo: SKBO,SEQM,03:34,05:21,0300
    // =========================================================

    /**
     * Carga vuelos desde archivo CSV simple. Devuelve la lista de GrafoVuelos.Vuelo.
     * - Ignora líneas vacías o que comiencen con '#'.
     * - El campo "capacidad" es opcional, se ignora en este loader (si lo necesitas, guárdalo aparte).
     * - Asigna ids secuenciales 0..N-1 en el orden de lectura.
     */
    public static List<GrafoVuelos.Vuelo> cargarVuelos(Path path) throws IOException {
        List<GrafoVuelos.Vuelo> out = new ArrayList<>();
        if (!Files.exists(path)) return out;

        int id = 0;
        for (String raw : Files.readAllLines(path)) {
            String line = safeLine(raw);
            if (line == null) continue;

            // Esperado: ORIGEN,DESTINO,HH:MM,HH:MM[,capacidad]
            String[] t = line.split(",");
            if (t.length < 4) continue;

            String origen  = t[0].trim();
            String destino = t[1].trim();
            int salidaMin  = hhmmAMinutos(t[2].trim());
            int llegadaMin = hhmmAMinutos(t[3].trim());
            if (salidaMin < 0 || llegadaMin < 0) continue;

            // Nota: si cruzas medianoche, GrafoVuelos.Vuelo.duracionMin() ya lo corrige.
            out.add(new GrafoVuelos.Vuelo(id++, origen, destino, salidaMin, llegadaMin));
        }
        return out;
    }

    // =========================================================
    //  PEDIDOS: dd-hh-mm-DEST-###-IdClien
    //  Ejemplo: 01-18-47-EHAM-081-0000010
    // =========================================================

    /**
     * Carga pedidos asumiendo que todos salen desde un HUB común (p.ej. "SKBO").
     * - Si tu lógica real determina otro origen, puedes añadir una columna o un mapa externo.
     */
    public static List<Pedido> cargarPedidos(Path path, String hubOrigen) throws IOException {
        List<Pedido> out = new ArrayList<>();
        if (!Files.exists(path)) return out;

        for (String raw : Files.readAllLines(path)) {
            String line = safeLine(raw);
            if (line == null) continue;

            // dd-hh-mm-DEST-###-IdClien
            String[] t = line.split("-");
            if (t.length < 6) continue;

            String ddStr   = t[0].trim();
            String hhStr   = t[1].trim();
            String mmStr   = t[2].trim();
            String dest    = t[3].trim();
            // String cantidad = t[4].trim(); // disponible si lo necesitas
            String idCli   = t[5].trim();

            int hh = parseIntSafe(hhStr, -1);
            int mm = parseIntSafe(mmStr, -1);
            if (hh < 0 || hh > 23 || mm < 0 || mm > 59) continue;

            int minutoDisponible = hh * 60 + mm;

            // Si tu Pedido ya tiene otro constructor, ajusta aquí:
            out.add(new Pedido(idCli, hubOrigen, dest, minutoDisponible, null));
        }
        return out;
    }

    // =========================================================
    //  (Opcional) Aeropuertos
    // =========================================================

    /**
     * Carga un set de códigos IATA válidos desde archivo (IATA,Nombre,Lat,Lon o solo IATA).
     * Útil si quieres validar que los ORIGEN/DESTINO existan.
     */
    public static Set<String> cargarIatas(Path path) throws IOException {
        Set<String> iatas = new HashSet<>();
        if (!Files.exists(path)) return iatas;

        for (String raw : Files.readAllLines(path)) {
            String line = safeLine(raw);
            if (line == null) continue;

            String[] t = line.split(",");
            if (t.length >= 1) {
                String iata = t[0].trim();
                if (!iata.isEmpty()) iatas.add(iata);
            }
        }
        return iatas;
    }

    // =========================================================
    //  Helpers internos
    // =========================================================

    /** Limpia línea: trim, ignora vacías o comentarios. */
    private static String safeLine(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        if (s.startsWith("#")) return null;
        return s;
    }

    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
}
