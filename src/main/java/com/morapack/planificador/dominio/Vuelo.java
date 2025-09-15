package com.morapack.planificador.dominio;

public class Vuelo {
    public final int id;
    public final String origen;
    public final String destino;
    public final int salidaMin;   // minutos desde 00:00
    public final int llegadaMin;  // minutos desde 00:00 (con wrap si cruza medianoche)
    public int capacidad;         // mutable: se consume al asignar
    public final double horasDuracion;
    public boolean esContinental; // Nuevo campo para indicar si el vuelo es continental

    public Vuelo(int id, String origen, String destino, int salidaMin, int llegadaMin, 
        int capacidad, int duracion, boolean esContinental) {
        this.id = id;
        this.origen = origen;
        this.destino = destino;
        this.salidaMin = salidaMin;
        this.llegadaMin = llegadaMin;
        this.capacidad = capacidad;
        this.horasDuracion = duracion;
        this.esContinental = esContinental;
    }
}
