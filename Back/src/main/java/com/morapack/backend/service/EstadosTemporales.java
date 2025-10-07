package com.morapack.backend.service; // ¡Paquete actualizado a service!

import java.util.ArrayList;
import java.util.List;
import com.morapack.backend.model.Pedido; // ¡Importación de dominio actualizada!

/**
 * Clases para gestión de estados temporales del sistema MoraPack.
 * Estas estructuras de datos son utilizadas EXCLUSIVAMENTE por la capa de servicio
 * (PlanificadorAco) y no deben ser persistidas ni expuestas a la API.
 */
public class EstadosTemporales {

    /**
     * Representa un paquete almacenado en un aeropuerto.
     */
    public static class PaqueteEnAlmacen {
        public final int cantidad;
        public final int diaLlegada;
        public final int minutoLlegada;
        public final String pedidoId;

        public PaqueteEnAlmacen(int cantidad, int diaLlegada, int minutoLlegada, String pedidoId) {
            this.cantidad = cantidad;
            this.diaLlegada = diaLlegada;
            this.minutoLlegada = minutoLlegada;
            this.pedidoId = pedidoId;
        }
    }

    /**
     * Representa un paquete en tránsito en un vuelo.
     */
    public static class PaqueteEnVuelo {
        public final int cantidad;
        public final String pedidoId;

        public PaqueteEnVuelo(int cantidad, String pedidoId) {
            this.cantidad = cantidad;
            this.pedidoId = pedidoId;
        }
    }

    /**
     * Estado temporal de un vuelo (capacidad ocupada, paquetes a bordo).
     */
    public static class EstadoVuelo {
        public int capacidadDisponible;
        public List<PaqueteEnVuelo> paquetesABordo;
        public int ultimoDiaOperacion;

        public EstadoVuelo(int capacidadTotal) {
            this.capacidadDisponible = capacidadTotal;
            this.paquetesABordo = new ArrayList<>();
            this.ultimoDiaOperacion = 0;
        }
    }

    /**
     * Estado temporal de un aeropuerto (paquetes en almacén).
     */
    public static class EstadoAeropuerto {
        public List<PaqueteEnAlmacen> paquetesEnAlmacen;
        public int ocupacionTotal;

        public EstadoAeropuerto() {
            this.paquetesEnAlmacen = new ArrayList<>();
            this.ocupacionTotal = 0;
        }
    }

    /**
     * Clase para gestionar pedidos pendientes con control de SLA.
     */
    public static class PedidoPendiente {
        public final Pedido pedido;
        public int paquetesPendientes;
        public final int diaLimite;      // Día máximo para completar (pedido.dia + SLA)
        public final int diasSLA;        // Días SLA asignados (2 o 3)

        public PedidoPendiente(Pedido pedido, int diaLimite, int diasSLA) {
            this.pedido = pedido;
            this.paquetesPendientes = pedido.getPaquetes();
            this.diaLimite = diaLimite;
            this.diasSLA = diasSLA;
        }
    }
}