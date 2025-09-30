package com.morapack.planificador.nucleo;

import com.morapack.planificador.dominio.Vuelo;
import com.morapack.planificador.dominio.EstadoVuelo;
import com.morapack.planificador.dominio.CancelacionVuelo;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class GestorEstadoVuelos {
    private final ConcurrentHashMap<String, EstadoVuelo> estadosVuelos;
    private final RelojSimulacion reloj;
    private final List<CancelacionVuelo> cancelacionesPendientes;
    
    public GestorEstadoVuelos(RelojSimulacion reloj) {
        this.estadosVuelos = new ConcurrentHashMap<>();
        this.reloj = reloj;
        this.cancelacionesPendientes = new ArrayList<>();
    }
    
    public void inicializarVuelo(Vuelo vuelo) {
        // Para vuelos sin información temporal, solo crear entrada en estado PROGRAMADO
        for (int dia = 1; dia <= 31; dia++) {
            String claveVuelo = generarClaveVueloConDia(vuelo, dia);
            estadosVuelos.put(claveVuelo, EstadoVuelo.PROGRAMADO);
            
            // Agendar transiciones automáticas para este día
            agendarTransicionesVuelo(vuelo, dia);
        }
    }
    
    private void agendarTransicionesVuelo(Vuelo vuelo, int dia) {
        String claveVuelo = generarClaveVueloConDia(vuelo, dia);
        
        // Convertir minutos a formato HHmm
        int horaSalidaHHmm = (vuelo.salidaMin / 60) * 100 + (vuelo.salidaMin % 60);
        int horaLlegadaHHmm = (vuelo.llegadaMin / 60) * 100 + (vuelo.llegadaMin % 60);
        
        // Despegue (5 minutos antes de la hora programada)
        int tiempoDespegue = horaSalidaHHmm - 5;
        if (tiempoDespegue < 0) tiempoDespegue = 2395; // Día anterior
        
        reloj.agendarEvento(dia, tiempoDespegue, () -> {
            if (estadosVuelos.get(claveVuelo) == EstadoVuelo.PROGRAMADO) {
                estadosVuelos.put(claveVuelo, EstadoVuelo.EN_DESPEGUE);
            }
        });
        
        // En vuelo (hora programada de salida)
        reloj.agendarEvento(dia, horaSalidaHHmm, () -> {
            EstadoVuelo estadoActual = estadosVuelos.get(claveVuelo);
            if (estadoActual == EstadoVuelo.EN_DESPEGUE || estadoActual == EstadoVuelo.PROGRAMADO) {
                estadosVuelos.put(claveVuelo, EstadoVuelo.EN_VUELO);
            }
        });
        
        // Aterrizaje (hora programada de llegada)
        reloj.agendarEvento(dia, horaLlegadaHHmm, () -> {
            if (estadosVuelos.get(claveVuelo) == EstadoVuelo.EN_VUELO) {
                estadosVuelos.put(claveVuelo, EstadoVuelo.ATERRIZADO);
            }
        });
    }
    
    public void procesarCancelaciones(List<CancelacionVuelo> cancelaciones) {
        for (CancelacionVuelo cancelacion : cancelaciones) {
            reloj.agendarEvento(cancelacion.dia, cancelacion.horaCancelacion, () -> {
                cancelarVuelo(cancelacion);
            });
        }
    }
    
    private void cancelarVuelo(CancelacionVuelo cancelacion) {
        // Buscar vuelo que coincida con origen-destino en el día especificado
        for (Map.Entry<String, EstadoVuelo> entrada : estadosVuelos.entrySet()) {
            String claveVuelo = entrada.getKey();
            
            if (coincideConCancelacion(claveVuelo, cancelacion)) {
                EstadoVuelo estadoActual = entrada.getValue();
                
                // Solo cancelar si aún no ha despegado
                if (estadoActual == EstadoVuelo.PROGRAMADO || estadoActual == EstadoVuelo.EN_DESPEGUE) {
                    estadosVuelos.put(claveVuelo, EstadoVuelo.CANCELADO);
                    System.out.println("Vuelo cancelado: " + cancelacion);
                }
            }
        }
    }
    
    private boolean coincideConCancelacion(String claveVuelo, CancelacionVuelo cancelacion) {
        // Formato clave: "dia-origen-destino-HHmm"
        String[] partes = claveVuelo.split("-");
        if (partes.length >= 4) {
            int dia = Integer.parseInt(partes[0]);
            String origen = partes[1];
            String destino = partes[2];
            int horaVuelo = Integer.parseInt(partes[3]);
            
            // Verificar si coincide día, origen, destino y que la cancelación sea antes del despegue
            return dia == cancelacion.dia && 
                   origen.equals(cancelacion.origen) && 
                   destino.equals(cancelacion.destino) &&
                   cancelacion.horaCancelacion <= horaVuelo;
        }
        return false;
    }
    
    public EstadoVuelo obtenerEstado(Vuelo vuelo, int dia) {
        String claveVuelo = generarClaveVueloConDia(vuelo, dia);
        return estadosVuelos.getOrDefault(claveVuelo, EstadoVuelo.PROGRAMADO);
    }
    
    public boolean estaDisponible(Vuelo vuelo) {
        // Verificar disponibilidad en todos los días
        for (int dia = 1; dia <= 31; dia++) {
            EstadoVuelo estado = obtenerEstado(vuelo, dia);
            if (estado != EstadoVuelo.CANCELADO) {
                return true; // Al menos un día está disponible
            }
        }
        return false;
    }
    
    private String generarClaveVueloConDia(Vuelo vuelo, int dia) {
        int horaSalidaHHmm = (vuelo.salidaMin / 60) * 100 + (vuelo.salidaMin % 60);
        return String.format("%d-%s-%s-%04d", 
            dia, vuelo.origen, vuelo.destino, horaSalidaHHmm);
    }
    
    public Map<String, EstadoVuelo> obtenerTodosLosEstados() {
        return new ConcurrentHashMap<>(estadosVuelos);
    }
}