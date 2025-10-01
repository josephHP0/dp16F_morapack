// src/main/java/com/morapack/planificador/nucleo/ReplanificadorAco.java
package com.morapack.planificador.nucleo;

import java.util.*;
import com.morapack.planificador.dominio.Pedido;


/**
 * Replanificador ACO incremental con trazas a consola.
 */
public class ReplanificadorAco {

    public interface SemillaFeromonaSupport {
        void cargarSemillasPorVueloId(Map<Integer, Double> semillasPorVueloId);
        void cargarSemillasPorLlave(Map<String, Double> semillasPorLlave);
    }

    private final PlanificadorAco planificadorBase;
    private final boolean usarSemillas;
    private final double pesoSemillaBase;

    public ReplanificadorAco(PlanificadorAco planificadorBase, boolean usarSemillas, double pesoSemillaBase) {
        this.planificadorBase = planificadorBase;
        this.usarSemillas = usarSemillas;
        this.pesoSemillaBase = Math.max(0.0, pesoSemillaBase);
    }

    public ReplanificadorAco(PlanificadorAco planificadorBase) {
        this(planificadorBase, false, 0.0);
    }

    public List<Asignacion> replanificar(
            List<Pedido> pedidosReplan,
            GrafoVuelos grafo,
            ParametrosAco params,
            List<Asignacion> solucionActualVigente,
            int diaActual,
            int minutoActual
    ) {
        if (pedidosReplan == null || pedidosReplan.isEmpty()) {
            if (params.isVerbose()) {
                System.out.println("[REPLAN] No hay pedidos para replanificar.");
            }
            return Collections.emptyList();
        }

        if (params.isVerbose()) {
            System.out.printf("[REPLAN] Dia %02d @ %02d:%02d — pedidos impactados: %d%n",
                    diaActual, minutoActual/60, minutoActual%60, pedidosReplan.size());
        }

        if (usarSemillas && planificadorBase instanceof SemillaFeromonaSupport) {
            Seeds seeds = extraerSemillasFeromona(solucionActualVigente, grafo, pedidosReplan);
            SemillaFeromonaSupport seedable = (SemillaFeromonaSupport) planificadorBase;
            if (!seeds.porVueloId.isEmpty()) seedable.cargarSemillasPorVueloId(seeds.porVueloId);
            if (!seeds.porLlave.isEmpty())   seedable.cargarSemillasPorLlave(seeds.porLlave);

            if (params.isVerbose()) {
                System.out.printf("[REPLAN] Semillas: %d por vueloId, %d por llave%n",
                        seeds.porVueloId.size(), seeds.porLlave.size());
            }
        }

        List<Asignacion> res = planificadorBase.planificarPedidos(pedidosReplan, grafo, params);

        if (params.isVerbose()) {
            long sinRuta = res.stream().filter(a -> a == null || a.getRuta() == null).count();
            System.out.printf("[REPLAN] Nuevas asignaciones: %d (sin ruta: %d)%n", res.size(), sinRuta);
        }

        return res;
    }

    public List<Asignacion> replanificar(List<Pedido> pedidosReplan, GrafoVuelos grafo, ParametrosAco params) {
        if (params.isVerbose()) {
            System.out.printf("[REPLAN] Replanificación simple de %d pedidos%n",
                    (pedidosReplan == null ? 0 : pedidosReplan.size()));
        }
        if (pedidosReplan == null || pedidosReplan.isEmpty()) return Collections.emptyList();
        return planificadorBase.planificarPedidos(pedidosReplan, grafo, params);
    }

    // ===== Seeds =====
    private static final class Seeds {
        final Map<Integer, Double> porVueloId = new HashMap<>();
        final Map<String,  Double> porLlave   = new HashMap<>();
    }

    private Seeds extraerSemillasFeromona(
            List<Asignacion> solucionActualVigente,
            GrafoVuelos grafo,
            List<Pedido> pedidosReplan
    ) {
        Seeds seeds = new Seeds();
        if (solucionActualVigente == null || solucionActualVigente.isEmpty()) return seeds;

        Set<String> pedidosImpactados = new HashSet<>();
        for (Pedido p : pedidosReplan) {
            pedidosImpactados.add(p.id);
        }

        for (Asignacion a : solucionActualVigente) {
            if (a == null || a.getPedido() == null || a.getRuta() == null) continue;
            if (pedidosImpactados.contains(a.getPedido().id)) continue;

            Ruta r = a.getRuta();
            List<Integer> vuelos = r.vuelosUsados;
            if (vuelos == null || vuelos.isEmpty()) continue;

            boolean tieneCancelado = false;
            for (Integer vueloId : vuelos) {
                String llave = grafo.llaveDe(vueloId);
                if (llave != null && grafo.estaCancelado(llave)) {
                    tieneCancelado = true;
                    break;
                }
            }
            if (tieneCancelado) continue;

            for (Integer vueloId : vuelos) {
                if (vueloId == null) continue;
                seeds.porVueloId.merge(vueloId, pesoSemillaBase, Double::sum);
                String llave = grafo.llaveDe(vueloId);
                if (llave != null) {
                    seeds.porLlave.merge(llave, pesoSemillaBase, Double::sum);
                }
            }
        }

        return seeds;
    }
}
