package com.morapack.planificador.nucleo;

import com.morapack.planificador.dominio.Vuelo;
import java.util.*;

public class GrafoVuelos {
    public static class Arista {
        public final String a;     // origen
        public final String b;     // destino
        public final double horas; // duración
        public final int vueloId;  // id del vuelo
        public Arista(String a, String b, double h, int id)
        { 
            this.a=a; 
            this.b=b; 
            this.horas=h; 
            this.vueloId=id; 
        }
    }

    private final Map<String, List<Arista>> ady = new HashMap<>();

    public GrafoVuelos(List<Vuelo> vuelos) {
        for (Vuelo v : vuelos) {
            ady.computeIfAbsent(v.origen, k -> new ArrayList<>())
                    .add(new Arista(v.origen, v.destino, v.horasDuracion, v.id));
        }
    }

    public List<Arista> aristasDesde(String origen) {
        return ady.getOrDefault(origen, Collections.emptyList());
    }
}
