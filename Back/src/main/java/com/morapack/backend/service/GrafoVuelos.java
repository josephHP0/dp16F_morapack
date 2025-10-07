package com.morapack.backend.service;

import com.morapack.backend.model.Vuelo; // Importación actualizada
import java.util.*;

/**
 * Representación del grafo de rutas de vuelo.
 */
public class GrafoVuelos {
    public static class Arista {
        public final String origen;     // origen
        public final String destino;     // destino
        public final double horas; // duración
        public final int vueloId;  // id del vuelo
        public Arista(String origen, String destino, double h, int id)
        {
            this.origen=origen;
            this.destino=destino;
            this.horas=h;
            this.vueloId=id;
        }
    }

    private final Map<String, List<Arista>> ady = new HashMap<>();
    private final List<Vuelo> vuelos;

    public GrafoVuelos(List<Vuelo> vuelos) {
        this.vuelos = new ArrayList<>(vuelos); // Store a copy of the vuelos list
        for (Vuelo v : vuelos) {
            ady.computeIfAbsent(v.getOrigen(), k -> new ArrayList<>())
                    .add(new Arista(v.getOrigen(), v.getDestino(), v.getHorasDuracion(), v.getId()));
        }
    }

    public List<Arista> aristasDesde(String origen) {
        return ady.getOrDefault(origen, Collections.emptyList());
    }

}