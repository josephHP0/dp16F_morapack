package com.morapack.planificador.nucleo;

import java.util.*;

// === PÉGALO dentro de la clase Ruta (o reemplaza/añade los campos y métodos faltantes) ===

public class Ruta {

    // ids de vuelo en orden
    public List<Integer> vuelosUsados = new ArrayList<>();

    // métricas agregadas (útiles para KPIs)
    public int duracionTotalMin = 0;
    public int hops = 0;

    // <<--- ESTE ES EL CAMPO QUE TE FALTABA
    public double costoTotal = 0.0;

    /** Recalcula métricas y costo simple a partir del grafo. */
    public void recalcular(GrafoVuelos grafo) {
        int dur = 0;
        int h = (vuelosUsados == null) ? 0 : vuelosUsados.size();

        if (vuelosUsados != null) {
            for (int id : vuelosUsados) {
                dur += grafo.duracionMinDe(id);
            }
        }
        this.duracionTotalMin = dur;
        this.hops = h;

        // Costo base: duración + penalización suave por hops (ajústalo a tu función objetivo real).
        this.costoTotal = duracionTotalMin + 10.0 * hops;
    }

    @Override
    public String toString() {
        return "Ruta{vuelos=" + vuelosUsados +
               ", durMin=" + duracionTotalMin +
               ", hops=" + hops +
               ", costo=" + costoTotal + "}";
    }
}
