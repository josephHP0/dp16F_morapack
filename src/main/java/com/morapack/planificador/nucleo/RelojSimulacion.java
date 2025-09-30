package com.morapack.planificador.nucleo;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RelojSimulacion {
    private volatile int tiempoActual; // HHmm format
    private volatile int diaActual;
    private final int velocidadSimulacion; // minutos virtuales por segundo real
    private final ConcurrentHashMap<Integer, ConcurrentLinkedQueue<Runnable>> eventosAgendados;
    private volatile boolean ejecutandose;
    private Thread hiloSimulacion;
    
    public RelojSimulacion(int diaInicial, int tiempoInicial, int velocidadSimulacion) {
        this.diaActual = diaInicial;
        this.tiempoActual = tiempoInicial;
        this.velocidadSimulacion = velocidadSimulacion;
        this.eventosAgendados = new ConcurrentHashMap<>();
        this.ejecutandose = false;
    }
    
    public synchronized void iniciar() {
        if (ejecutandose) return;
        
        ejecutandose = true;
        hiloSimulacion = new Thread(this::bucleSimulacion);
        hiloSimulacion.start();
    }
    
    public synchronized void detener() {
        ejecutandose = false;
        if (hiloSimulacion != null) {
            hiloSimulacion.interrupt();
        }
    }
    
    private void bucleSimulacion() {
        while (ejecutandose) {
            try {
                Thread.sleep(1000); // 1 segundo real
                avanzarTiempo(velocidadSimulacion);
                ejecutarEventosActuales();
            } catch (InterruptedException e) {
                break;
            }
        }
    }
    
    private void avanzarTiempo(int minutosVirtuales) {
        int nuevosMinutos = (tiempoActual % 100) + (tiempoActual / 100) * 60 + minutosVirtuales;
        int nuevaHora = nuevosMinutos / 60;
        int minutos = nuevosMinutos % 60;
        
        if (nuevaHora >= 24) {
            diaActual++;
            nuevaHora %= 24;
        }
        
        tiempoActual = nuevaHora * 100 + minutos;
    }
    
    private void ejecutarEventosActuales() {
        int claveEvento = diaActual * 10000 + tiempoActual;
        ConcurrentLinkedQueue<Runnable> eventos = eventosAgendados.get(claveEvento);
        
        if (eventos != null) {
            while (!eventos.isEmpty()) {
                Runnable evento = eventos.poll();
                if (evento != null) {
                    try {
                        evento.run();
                    } catch (Exception e) {
                        System.err.println("Error ejecutando evento: " + e.getMessage());
                    }
                }
            }
        }
    }
    
    public void agendarEvento(int dia, int tiempo, Runnable evento) {
        int claveEvento = dia * 10000 + tiempo;
        eventosAgendados.computeIfAbsent(claveEvento, k -> new ConcurrentLinkedQueue<>()).add(evento);
    }
    
    public int getTiempoActual() {
        return tiempoActual;
    }
    
    public int getDiaActual() {
        return diaActual;
    }
    
    public void establecerTiempo(int dia, int tiempo) {
        this.diaActual = dia;
        this.tiempoActual = tiempo;
    }
}