package com.morapack.planificador.dominio;

public class VueloTemporal extends Vuelo {
    public final int dia;
    public final int horaSalida; // HHmm format
    public final int horaLlegada; // HHmm format
    
    public VueloTemporal(Vuelo vueloBase, int dia, int horaSalida, int horaLlegada) {
        super(vueloBase.id, vueloBase.origen, vueloBase.destino, 
              vueloBase.salidaMin, vueloBase.llegadaMin, vueloBase.capacidad,
              (int)(vueloBase.horasDuracion * 60), vueloBase.esContinental);
        this.dia = dia;
        this.horaSalida = horaSalida;
        this.horaLlegada = horaLlegada;
    }
    
    public static VueloTemporal crear(Vuelo vuelo, int dia) {
        // Convertir minutos a formato HHmm
        int horaSalida = (vuelo.salidaMin / 60) * 100 + (vuelo.salidaMin % 60);
        int horaLlegada = (vuelo.llegadaMin / 60) * 100 + (vuelo.llegadaMin % 60);
        
        return new VueloTemporal(vuelo, dia, horaSalida, horaLlegada);
    }
}