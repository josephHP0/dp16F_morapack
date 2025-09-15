package com.morapack.planificador.dominio;

public class Pedido {
    public final String id;
    public final String destinoIata;
    public final int paquetes;

    public Pedido(String id, String destinoIata, int paquetes) {
        this.id = id;
        this.destinoIata = destinoIata;
        this.paquetes = paquetes;
    }
}
