package com.morapack.planificador.simulation;


import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class TimelineStore {
    private final List<Event> events = new ArrayList<>();
    private final AtomicInteger version = new AtomicInteger(0);

    public synchronized void load(PlanResult plan, List<Event> timeline) {
        events.clear();
        events.addAll(timeline);
        events.sort(Comparator.naturalOrder());
        version.incrementAndGet();
    }

    public synchronized String getVersion() {
        return "v" + version.get();
    }

    public synchronized List<Event> getEvents() {
        return List.copyOf(events);
    }

    public synchronized List<Event> eventsBetween(Instant from, Instant to) {
        return events.stream()
                .filter(e -> !e.ts.isBefore(from) && e.ts.isBefore(to))
                .sorted()
                .collect(Collectors.toList());
    }

    public synchronized void appendAll(Collection<Event> more) {
        events.addAll(more);
        events.sort(Comparator.naturalOrder());
        version.incrementAndGet();
    }
}
