package com.morapack.planificador.simulation;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Inserta eventos CANCELLATION en el timeline para las instancias afectadas.
 * No “deshace” cargas previas; la replanificación se encargará de reasignar.
 */
public class CancellationServiceInMemory implements CancellationService {

    private final TimelineStore store;

    public CancellationServiceInMemory(TimelineStore store) {
        this.store = store;
    }

    @Override
    public CancellationSummary apply(List<CancellationRecord> records) {
        if (records == null || records.isEmpty()) {
            return new CancellationSummary(0, store.getVersion());
        }

        List<Event> base = store.getEvents();
        Set<String> ids = records.stream()
                .map(r -> r.instanceId)
                .collect(Collectors.toSet());

        // instante de publicación = min(fecha record) para orden del timeline
        Instant tsMin = records.stream().map(r -> r.effectiveAtUtc).min(Instant::compareTo).orElse(Instant.now());

        List<Event> cancel = new ArrayList<>();
        for (CancellationRecord r : records) {
            cancel.add(Event.cancellation(r.effectiveAtUtc, r.instanceId, r.origen));
        }

        store.appendAll(cancel);

        // afectados = #eventos LOAD/ARRIVAL con esas instancias
        int afectados = (int) base.stream()
                .filter(e -> e.flightInstanceId != null && ids.contains(e.flightInstanceId))
                .count();

        return new CancellationSummary(afectados, store.getVersion());
    }
}
