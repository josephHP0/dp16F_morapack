package com.morapack.planificador.dominio;

public class Pedido {
    public final String id;            // Cliente ID (7 digits)
    public final String destinoIata;   // Airport code
    public final int paquetes;         // Quantity (3 digits)
    public final int dia;              // Day of month (1-31)
    public final int hora;             // Hour (0-23)
    public final int minuto;           // Minute (0-59)

    public Pedido(String id, String destinoIata, int paquetes, int dia, int hora, int minuto) {
        this.id = id;
        this.destinoIata = destinoIata;
        this.paquetes = paquetes;
        this.dia = dia;
        this.hora = hora;
        this.minuto = minuto;
    }
}
