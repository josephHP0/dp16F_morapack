package com.morapack.planificador.nucleo;

public class ParametrosAco {

    // ===== Defaults seguros =====
    private int    numAnts  = 50;
    private int    maxIter  = 200;
    private double alpha    = 1.0;
    private double beta     = 3.0;
    private double rho      = 0.15;  // evaporación
    private double q0       = 0.10;  // explotación vs exploración
    private double tau0     = 0.01;  // feromona inicial

    // ===== Nuevo: control de verbosidad para impresión a consola =====
    private boolean verbose = true;

    public ParametrosAco() {}

    // ===== Getters requeridos =====
    public int getNumAnts() { return numAnts; }
    public int getMaxIter() { return maxIter; }
    public double getAlpha() { return alpha; }
    public double getBeta() { return beta; }
    public double getRho() { return rho; }
    public double getQ0() { return q0; }
    public double getTau0() { return tau0; }
    public boolean isVerbose() { return verbose; }

    // ===== Setters fluidos =====
    public ParametrosAco ants(int n) { this.numAnts = n; return this; }
    public ParametrosAco iter(int n) { this.maxIter = n; return this; }
    public ParametrosAco alpha(double v) { this.alpha = v; return this; }
    public ParametrosAco beta(double v) { this.beta = v; return this; }
    public ParametrosAco rho(double v) { this.rho = v; return this; }
    public ParametrosAco q0(double v) { this.q0 = v; return this; }
    public ParametrosAco tau0(double v) { this.tau0 = v; return this; }
    public ParametrosAco verbose(boolean v) { this.verbose = v; return this; }
}
