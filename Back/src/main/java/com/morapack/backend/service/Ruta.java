package com.morapack.backend.service;

import java.util.*;

/**
 * Objeto auxiliar que almacena una ruta encontrada por una hormiga
 */
public class Ruta {
    public final List<Integer> vuelosUsados = new ArrayList<>();
    public final List<String> itinerario = new ArrayList<>(); // segmentos "ORIGEN->DESTINO (h)"
    public final List<String> nodos = new ArrayList<>(); // incluye hub inicial y destino
    public double horasTotales = Double.POSITIVE_INFINITY;
}