package com.morapack.planificador.simulation;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Snapshot “listo para UI”:
 *  - ocupación por aeropuerto (almacén)
 *  - ocupación por vuelo (instanceId)
 *  - pedidos presentes por aeropuerto
 *  - estado por pedido
 *  - información de colapso (opcional)
 */
public class EstadosTemporales {

    private final Instant at;
    private final Map<String, Integer> ocupacionAeropuerto;     // airportId -> paquetes en almacén
    private final Map<String, Integer> ocupacionVueloInstance;  // instanceId -> paquetes en vuelo
    private final Map<String, List<String>> pedidosEnAeropuerto;// airportId -> [pedidoId...]
    private final Map<String, String> estadoPedido;             // pedidoId -> EN_VUELO/EN_ALMACEN/LISTO_PICKUP
    private final CollapseInfo collapse;                        // null si no hay colapso

    public EstadosTemporales(
            Instant at,
            Map<String, Integer> ocupacionAeropuerto,
            Map<String, Integer> ocupacionVueloInstance,
            Map<String, List<String>> pedidosEnAeropuerto,
            Map<String, String> estadoPedido
    ) {
        this(at, ocupacionAeropuerto, ocupacionVueloInstance, pedidosEnAeropuerto, estadoPedido, null);
    }

    public EstadosTemporales(
            Instant at,
            Map<String, Integer> ocupacionAeropuerto,
            Map<String, Integer> ocupacionVueloInstance,
            Map<String, List<String>> pedidosEnAeropuerto,
            Map<String, String> estadoPedido,
            CollapseInfo collapse
    ) {
        this.at = at;
        this.ocupacionAeropuerto = ocupacionAeropuerto != null ? ocupacionAeropuerto : Collections.emptyMap();
        this.ocupacionVueloInstance = ocupacionVueloInstance != null ? ocupacionVueloInstance : Collections.emptyMap();
        this.pedidosEnAeropuerto = pedidosEnAeropuerto != null ? pedidosEnAeropuerto : Collections.emptyMap();
        this.estadoPedido = estadoPedido != null ? estadoPedido : Collections.emptyMap();
        this.collapse = collapse;
    }

    public Instant getAt() { return at; }
    public Map<String, Integer> getOcupacionAeropuerto() { return ocupacionAeropuerto; }
    public Map<String, Integer> getOcupacionVueloInstance() { return ocupacionVueloInstance; }
    public Map<String, List<String>> getPedidosEnAeropuerto() { return pedidosEnAeropuerto; }
    public Map<String, String> getEstadoPedido() { return estadoPedido; }

    /** null si no hay colapso */
    public CollapseInfo getCollapse() { return collapse; }
}
