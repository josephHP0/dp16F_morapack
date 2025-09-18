package com.morapack.planificador.nucleo;

public class ParametrosAco {
    public double alpha = 1.0;     // importancia de feromona
    public double beta  = 1.0;     // reducido para dar más peso a la feromona
    public double rho   = 0.05;    // evaporación muy baja para mantener rutas encontradas
    public double Q     = 2.0;     // más refuerzo para buenas rutas
    public int hormigas = 40;      // más hormigas para explorar más
    public int iteraciones = 15;   // más iteraciones para encontrar rutas
    public int pasosMax = 8;       // permitir rutas más largas
}
