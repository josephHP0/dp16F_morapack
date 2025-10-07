package com.morapack.planificador.nucleo;



import com.morapack.planificador.dominio.Pedido;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Resultado de planificación para UN pedido (o sub-grupo del pedido).
 * Compatible con la construcción de Timeline (carga/arribo/esperas).
 */
public class Asignacion {
    public Pedido pedido;

    /** Si el pedido se abastece desde un hub "infinito" (SPIM/EBCI/UBBB), aquí va ese IATA. Puede ser null. */
    public String hubOrigen;

    /** Ruta temporalizada completa (uno o más tramos) */
    public Ruta ruta;

    /** Cuántos paquetes quedaron efectivamente asignados a esta ruta */
    public int paquetesAsignados;

    /** Cuántos paquetes del pedido quedan sin asignar (pendientes de replan) */
    public int paquetesPendientes;

    public Asignacion() {}

    public Asignacion(Pedido pedido, String hubOrigen, Ruta ruta, int paquetesAsignados, int paquetesPendientes) {
        this.pedido = pedido;
        this.hubOrigen = hubOrigen;
        this.ruta = ruta;
        this.paquetesAsignados = paquetesAsignados;
        this.paquetesPendientes = paquetesPendientes;
    }

    // === Azúcar: acceso directo a los tramos ===
    public List<Ruta.Tramo> tramos() {
        return (ruta != null) ? ruta.tramos : Collections.emptyList();
    }

    // === Utilitarios / Getters (usados por builder y reportes) ===
    public Pedido getPedido() { return pedido; }
    public String getHubOrigen() { return hubOrigen; }
    public Ruta getRuta() { return ruta; }
    public int getPaquetesAsignados() { return paquetesAsignados; }
    public int getPaquetesPendientes() { return paquetesPendientes; }

    public boolean estaAsignado() { return paquetesAsignados > 0 && ruta != null && !tramos().isEmpty(); }

    @Override
    public String toString() {
        return "Asignacion{pedido=" + (pedido!=null?pedido.id:"null") +
                ", hubOrigen=" + hubOrigen +
                ", asignados=" + paquetesAsignados +
                ", pendientes=" + paquetesPendientes +
                ", tramos=" + tramos().size() + "}";
    }

    @Override
    public int hashCode() {
        return Objects.hash(pedido != null ? pedido.id : null, hubOrigen, paquetesAsignados, paquetesPendientes);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Asignacion other)) return false;
        return Objects.equals(this.pedido != null ? this.pedido.id : null, other.pedido != null ? other.pedido.id : null)
                && Objects.equals(this.hubOrigen, other.hubOrigen)
                && this.paquetesAsignados == other.paquetesAsignados
                && this.paquetesPendientes == other.paquetesPendientes
                && Objects.equals(this.ruta, other.ruta);
    }
}

