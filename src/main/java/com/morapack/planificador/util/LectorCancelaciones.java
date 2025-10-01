// src/main/java/com/morapack/planificador/util/LectorCancelaciones.java
package com.morapack.planificador.util;

import com.morapack.planificador.dominio.Cancelacion;
import java.nio.file.*; import java.io.*; import java.util.*; 

public class LectorCancelaciones {

    public static Map<Integer, List<Cancelacion>> cargarPorDia(Path archivo) throws IOException {
        Map<Integer, List<Cancelacion>> porDia = new HashMap<>();
        if (!Files.exists(archivo)) return porDia;
        for (String linea : Files.readAllLines(archivo)) {
            linea = linea.trim();
            if (linea.isEmpty() || linea.startsWith("#")) continue;
            // ej: 02.SKBO-SEQM-14:22  ó  02.SKBO-SEQM-14:22   (con o sin puntos)
            String[] partes = linea.split("\\.");
            int dia = Integer.parseInt(partes[0]);
            String[] seg = partes[1].split("-");
            String origen = seg[0], destino = seg[1];
            String hhmm = seg[2];
            int salidaMin = UtilArchivos.hhmmAMinutosPublic(hhmm); // expón hhmmAMinutos
            Cancelacion c = new Cancelacion(dia, origen, destino, salidaMin);
            porDia.computeIfAbsent(dia, k -> new ArrayList<>()).add(c);
        }
        return porDia;
    }
}
