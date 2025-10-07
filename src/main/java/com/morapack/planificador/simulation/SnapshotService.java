package com.morapack.planificador.simulation;

import java.time.Instant;

public interface SnapshotService {
    EstadosTemporales stateAt(Instant at);
}
