package com.morapack.planificador.nucleo;



import java.time.Duration;

/**
 * Parámetros de ACO y de la heurística "congestion-aware".
 * Ajustables sin recompilar el resto del núcleo.
 */
public class ParametrosAco {

    // ===== ACO Básico =====
    /** Peso de feromona en la prob. de transición */
    public double alpha = 0.8;        // antes ~1.0
    /** Peso de heurística en la prob. de transición */
    public double beta  = 3.5;        // antes ~2.0
    /** Tasa de evaporación de feromona (0..1) */
    public double rho   = 0.35;       // antes ~0.2

    /** Feromona inicial en arcos */
    public double pheromoneInit  = 1.0;
    /** Piso de feromona para evitar lockout de alternativas */
    public double pheromoneFloor = 1e-6;

    /** Tamaño de la lista candidata (top-k por heurística) */
    public int candidateK = 3;  // Muy reducido para pruebas rápidas

    /** Saltos máximos permitidos en una ruta (para cortar ciclos) */
    public int maxHops = 3;     // Muy reducido para pruebas rápidas

    // ===== Heurística de costo =====
    /** Peso del ETA (tiempo de llegada normalizado) */
    public double w_time = 1.0;
    /** Peso de la espera estimada / cola en destino */
    public double w_wait = 1.0;
    /** Peso del riesgo de violar SLA */
    public double w_sla  = 2.0;
    /** Peso de congestión (ocupación prevista) */
    public double w_cong = 3.0;
    /** Penalización ligera por número de saltos */
    public double w_hops = 0.3;

    // ===== Congestión =====
    /** Umbral a partir del cual empezamos a penalizar ocupación */
    public double congestionThreshold = 0.60; // 60%
    /** Curvatura de la penalización de congestión (más alto = más agresivo) */
    public double congestionGamma = 8.0;

    /**
     * f(U): penalización de congestión suave con umbral.
     * U en [0..1]. Si U <= threshold, 0; si U>threshold, gamma*(U-th)^2
     */
    public double congestionPenalty(double u) {
        if (u <= congestionThreshold) return 0.0;
        double d = (u - congestionThreshold);
        return congestionGamma * d * d;
    }

    // ===== Headroom predictivo =====
    /** Horizonte para validar headroom futuro en el destino */
    public Duration headroomHorizon = Duration.ofHours(6);

    /** Multiplicador de costo si no hay headroom en la ventana H */
    public double headroomBlockCostMultiplier = 10.0;

    // ===== Operación =====
    /** Dwell mínimo en almacenes (param. de negocio) */
    public Duration dwellMin = Duration.ofHours(1);

    /** Reserva de capacidad de salida en hubs calientes para evitar sumideros */
    public double reserveTransitRatio = 0.15; // 15% de asientos reservados para tránsito/salida

    // ===== Normalizaciones =====
    /** ETA de referencia para normalizar tiempos (p.ej. 24h) */
    public Duration etaRef = Duration.ofHours(24);

    /** Espera de referencia (para normalizar la cola esperada) */
    public Duration waitRef = Duration.ofHours(6);

    // ===== Tuning conveniente desde código =====
    public static ParametrosAco defaults() {
        return new ParametrosAco();
    }

    public ParametrosAco withAlpha(double v){ this.alpha=v; return this; }
    public ParametrosAco withBeta(double v){ this.beta=v; return this; }
    public ParametrosAco withRho(double v){ this.rho=v; return this; }

    public ParametrosAco withWeights(double wTime, double wWait, double wSla, double wCong, double wHops){
        this.w_time=wTime; this.w_wait=wWait; this.w_sla=wSla; this.w_cong=wCong; this.w_hops=wHops;
        return this;
    }

    public ParametrosAco withCongestion(double threshold, double gamma){
        this.congestionThreshold=threshold; this.congestionGamma=gamma; return this;
    }

    public ParametrosAco withHeadroom(Duration horizon, double blockMultiplier){
        this.headroomHorizon=horizon; this.headroomBlockCostMultiplier=blockMultiplier; return this;
    }

    public ParametrosAco withCandidateK(int k){ this.candidateK=k; return this; }
    public ParametrosAco withMaxHops(int h){ this.maxHops=h; return this; }

    public ParametrosAco withReserves(double ratio){ this.reserveTransitRatio=ratio; return this; }

    public ParametrosAco withRefs(Duration etaRef, Duration waitRef){
        this.etaRef = etaRef; this.waitRef = waitRef; return this;
    }
}
