package com.morapack.backend.service;

import com.morapack.backend.model.Pedido; // Importación actualizada

/**
 * Objeto DTO interno de la capa Service para registrar la asignación final
 */
public class Asignacion {
    public Pedido pedido;
    public String hubOrigen;
    public Ruta ruta;                 // puede ser null si no hubo ruta
    public int paquetesAsignados;
    public int paquetesPendientes;
}