package com.morapack.planificador.simulation;



import java.util.List;

public interface CancellationService {

    record CancellationSummary(int afectados, String newVersion) {}

    CancellationSummary apply(List<CancellationRecord> records);
}
