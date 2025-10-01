package com.morapack.planificador.nucleo;

import com.morapack.planificador.dominio.Pedido;

import java.util.*;

/**
 * Una asignación vincula un Pedido con la Ruta encontrada por el Planificador/ACO.
 * Puede ser nula (ruta == null) si el pedido no pudo ser atendido.
 */
public class Asignacion {

    public final Pedido pedido;
    public final Ruta ruta; // puede ser null si no se encontró ruta

    // ===== CONSTRUCTOR =====
    public Asignacion(Pedido pedido, Ruta ruta) {
        this.pedido = pedido;
        this.ruta = ruta;
    }

    // ===== GETTERS =====
    public Pedido getPedido() { return pedido; }
    public Ruta getRuta() { return ruta; }

    public boolean tieneRuta() {
        return ruta != null && ruta.vuelosUsados != null && !ruta.vuelosUsados.isEmpty();
    }

    /** Devuelve los ids de vuelo usados en la ruta (o vacío si null). */
    public List<Integer> getVuelosUsados() {
        return (ruta != null && ruta.vuelosUsados != null)
                ? ruta.vuelosUsados
                : Collections.emptyList();
    }

    @Override
    public String toString() {
        if (ruta == null) {
            return "Asignacion[pedido=" + pedido.id + ", SIN RUTA]";
        } else {
            return "Asignacion[pedido=" + pedido.id + ", ruta=" + ruta + "]";
        }
    }
}
