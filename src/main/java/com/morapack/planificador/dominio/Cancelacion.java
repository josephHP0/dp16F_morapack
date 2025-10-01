// src/main/java/com/morapack/planificador/dominio/Cancelacion.java
package com.morapack.planificador.dominio;

public class Cancelacion {
    public final int dia;          // 1..31
    public final String origen;    // IATA
    public final String destino;   // IATA
    public final int salidaMin;    // HH:MM -> minutos

    public Cancelacion(int dia, String origen, String destino, int salidaMin) {
        this.dia = dia; this.origen = origen; this.destino = destino; this.salidaMin = salidaMin;
    }

    public String llave() { // clave consistente con vuelos
        return origen + ">" + destino + "@" + salidaMin;
    }
}
