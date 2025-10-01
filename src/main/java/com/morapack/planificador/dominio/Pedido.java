package com.morapack.planificador.dominio;

/**
 * Pedido mínimo compatible con el Planificador/Replanificador.
 * Si ya tienes esta clase, agrega solo los getters que falten.
 */
public class Pedido {

    // ====== Campos base (ajusta nombres si ya existen) ======
    public final String id;           // "0000010" (o similar)
    private final String nodoOrigen;  // IATA/hub de salida real para planificar
    private final String nodoDestino; // IATA destino (del string de pedido)
    private final int minutoDisponible; // desde cuándo puede salir (minutos del día de creación/ventana)

    // (Opcional) deadline SLA en minutos absolutos o relativo; ajusta según tu modelo
    private final Integer slaDeadlineMin; // puede ser null si no aplicas

    public Pedido(String id, String nodoOrigen, String nodoDestino, int minutoDisponible, Integer slaDeadlineMin) {
        this.id = id;
        this.nodoOrigen = nodoOrigen;
        this.nodoDestino = nodoDestino;
        this.minutoDisponible = minutoDisponible;
        this.slaDeadlineMin = slaDeadlineMin;
    }

    // ====== Getters usados por el ACO ======
    public String getNodoOrigen() { return nodoOrigen; }
    public String getNodoDestino() { return nodoDestino; }
    public int getMinutoDisponible() { return minutoDisponible; }
    public Integer getSlaDeadlineMin() { return slaDeadlineMin; }

    // ====== Helpers de compatibilidad (si parseas desde tu string dd-hh-mm-DEST-###-IdClien) ======
    /**
     * Crea un Pedido a partir del formato:
     *   dd-hh-mm-DEST-###-IdClien
     * 'nodoOrigen' puede ser tu hub (p.ej. "SKBO") o inferido según tu lógica.
     */
    public static Pedido fromLinea(String linea, String hubOrigen) {
        // Ej: 01-18-47-EHAM-081-0000010
        String[] t = linea.trim().split("-");
        int dd = Integer.parseInt(t[0]);
        int hh = Integer.parseInt(t[1]);
        int mm = Integer.parseInt(t[2]);
        String dest = t[3];
        // String cantidad = t[4]; // si no lo usas, omite
        String idCliente = t[5];

        int minutoDisponible = hh * 60 + mm; // simplificado a minuto del día
        return new Pedido(idCliente, hubOrigen, dest, minutoDisponible, null);
    }
}
