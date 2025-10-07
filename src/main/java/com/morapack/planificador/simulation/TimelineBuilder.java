package com.morapack.planificador.simulation;

import com.morapack.planificador.nucleo.Asignacion;
import com.morapack.planificador.nucleo.Ruta;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Construye la lista de Event a partir de las asignaciones. */
public class TimelineBuilder {

    // Mínimo de permanencia en almacén entre ARRIVAL y LOAD
    private static final Duration MIN_STAY = Duration.ofHours(1);

    public static List<Event> construir(List<Asignacion> asignaciones) {
        List<Event> evts = new ArrayList<>();

        for (Asignacion a : asignaciones) {
            if (a == null || !a.estaAsignado()) continue;
            int qty = a.paquetesAsignados;

            List<Ruta.Tramo> tramos = a.tramos();
            for (int i = 0; i < tramos.size(); i++) {
                Ruta.Tramo t = tramos.get(i);

                if (i == 0) {
                    // primer tramo: el producto "aparece" en hubOrigen (si lo hay) y espera
                    evts.add(Event.waitStart(t.depUtc.minus(MIN_STAY), t.origen, a.pedido.id, qty));
                    evts.add(Event.load(t.depUtc, t.origen, t.instanceId, a.pedido.id, qty));
                } else {
                    // tramos intermedios: ARRIVAL + WAIT + LOAD
                    Ruta.Tramo prev = tramos.get(i - 1);
                    // ARRIVAL del tramo anterior (en destino del anterior == origen del actual)
                    evts.add(Event.arrival(prev.arrUtc, prev.destino, prev.instanceId, a.pedido.id, qty));
                    evts.add(Event.waitStart(t.depUtc.minus(MIN_STAY), t.origen, a.pedido.id, qty));
                    evts.add(Event.waitEnd(t.depUtc, t.origen, a.pedido.id, qty));
                    evts.add(Event.load(t.depUtc, t.origen, t.instanceId, a.pedido.id, qty));
                }

                if (i == tramos.size() - 1) {
                    // último tramo: ARRIVAL final + PICKUP_READY tras 2h
                    evts.add(Event.arrival(t.arrUtc, t.destino, t.instanceId, a.pedido.id, qty));
                    evts.add(Event.pickupReady(t.arrUtc.plus(Duration.ofHours(2)), t.destino, a.pedido.id));
                }
            }
        }

        return evts;
    }
}
