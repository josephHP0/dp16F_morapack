package com.morapack.planificador.nucleo;

public class ParametrosAco {
    public double alpha = 1.0;     // importancia de feromona
    public double beta  = 2.0;     // importancia de heurística (1/duración)
    public double rho   = 0.5;     // evaporación
    public double Q     = 1.0;     // depósito
    public int hormigas = 50;       // hormigas por pedido
    public int iteraciones = 15;    // iteraciones por pedido
    public int pasosMax = 8;       // saltos máximos por ruta
}
