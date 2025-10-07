package com.morapack.planificador.simulation;


import java.time.Instant;
import java.time.YearMonth;

public interface ReplannerService {

    record Summary(int replanificados, String newVersion) {}

    Summary replanPending(Instant now, YearMonth periodo);
}
