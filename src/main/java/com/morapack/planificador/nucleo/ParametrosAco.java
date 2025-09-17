package com.morapack.planificador.nucleo;

public class ParametrosAco {
    public double alpha = 1.0;     // importancia de feromona
    public double beta  = 2.0;     // importancia de heurística (1/duración)
    public double rho   = 0.3;     // evaporación (reducido para mantener buenas rutas)
    public double Q     = 2.0;     // depósito (aumentado para enfatizar buenas rutas)
    public int hormigas = 60;      // hormigas por pedido (balance entre exploración y tiempo)
    public int iteraciones = 20;   // iteraciones por pedido (balance entre calidad y tiempo)
    public int pasosMax = 8;       // saltos máximos por ruta
}
