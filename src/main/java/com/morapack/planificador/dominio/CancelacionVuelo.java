package com.morapack.planificador.dominio;

public class CancelacionVuelo {
    public final int dia;
    public final String origen;
    public final String destino;
    public final int horaCancelacion; // HHmm format
    
    public CancelacionVuelo(int dia, String origen, String destino, int horaCancelacion) {
        this.dia = dia;
        this.origen = origen;
        this.destino = destino;
        this.horaCancelacion = horaCancelacion;
    }
    
    @Override
    public String toString() {
        return String.format("Día %d: %s->%s cancelado a las %04d", 
            dia, origen, destino, horaCancelacion);
    }
}