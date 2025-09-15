package com.morapack.planificador.nucleo;

import com.morapack.planificador.dominio.Pedido;

public class Asignacion {
    public Pedido pedido;
    public String hubOrigen;
    public Ruta ruta;                 // puede ser null si no hubo ruta
    public int paquetesAsignados;
    public int paquetesPendientes;
}
