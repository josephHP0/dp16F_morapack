package com.morapack.planificador.nucleo;

import java.util.*;
import com.morapack.planificador.dominio.Cancelacion;
import com.morapack.planificador.dominio.Pedido;

/**
 * Orquestador de simulación semanal con replanificación por cancelaciones.
 *
 * Flujo:
 *  - inicializar(): obtiene solución base con el PlanificadorAco y crea índices
 *  - correrSemana(d1,d7): por cada día, procesa cancelaciones en orden de hora de salida,
 *    marca el vuelo cancelado (si aún no salió) y replanifica SOLO pedidos impactados.
 *
 * Requiere en GrafoVuelos:
 *  - vuelosDesde(String)
 *  - llaveDe(int), estaCancelado(String), cancelarVuelo(String)
 *  - destinoDe(int), salidaMinDe(int), llegadaMinDe(int), duracionMinDe(int)
 *
 * Requiere en Asignacion/Ruta:
 *  - Asignacion.getPedido(), Asignacion.getRuta(), Ruta.vuelosUsados (List<Integer>)
 */

/**
 * Orquestador de simulación semanal con replanificación por cancelaciones.
 * Imprime trazas a consola para visualizar el estado.
 */
public class Simulador {

    private final GrafoVuelos grafo;
    private final PlanificadorAco planificador;
    private final ReplanificadorAco replanificador;
    private final Map<Integer, List<Cancelacion>> cancelsPorDia;

    private List<Asignacion> asignaciones = new ArrayList<>();
    private final Map<String, List<Asignacion>> asignacionesPorLlave = new HashMap<>();
    private int relojMinDelDia = 0;

    // Métricas
    private int totalEventosCancel = 0;
    private int totalPedidosImpactados = 0;
    private int totalReplanificaciones = 0;

    public Simulador(GrafoVuelos grafo,
                     PlanificadorAco planificador,
                     Map<Integer, List<Cancelacion>> cancelsPorDia) {
        this.grafo = grafo;
        this.planificador = planificador;
        this.replanificador = new ReplanificadorAco(planificador, true, 2.0);
        this.cancelsPorDia = (cancelsPorDia != null) ? cancelsPorDia : new HashMap<>();
    }

    public void inicializar(List<Pedido> pedidos, ParametrosAco params) {
        if (params.isVerbose()) {
            System.out.printf("[SIM] Planificación inicial de %d pedidos...%n", pedidos.size());
        }
        this.asignaciones = new ArrayList<>(planificador.planificarInicial(pedidos, grafo, params));
        indexarAsignacionesPorVuelo();

        if (params.isVerbose()) {
            long sinRuta = asignaciones.stream().filter(a -> a == null || a.getRuta() == null).count();
            System.out.printf("[SIM] Plan inicial listo. Asignaciones: %d (sin ruta: %d)%n", asignaciones.size(), sinRuta);
        }
    }

    public void correrSemana(int diaIni, int diaFin, ParametrosAco params) {
        for (int dia = diaIni; dia <= diaFin; dia++) {
            procesarDia(dia, params);
        }
        if (params.isVerbose()) {
            System.out.printf("[SIM] RESUMEN — Cancelaciones: %d, Pedidos impactados: %d, Replans: %d%n",
                    totalEventosCancel, totalPedidosImpactados, totalReplanificaciones);
        }
    }

    public List<Asignacion> getAsignaciones() {
        return asignaciones;
    }

    // ===== Núcleo diario =====

    private void procesarDia(int dia, ParametrosAco params) {
        relojMinDelDia = 0;

        List<Cancelacion> hoy = new ArrayList<>(cancelsPorDia.getOrDefault(dia, List.of()));
        hoy.sort(Comparator.comparingInt(c -> c.salidaMin));

        if (params.isVerbose()) {
            System.out.printf("%n[SIM] ===== DÍA %02d ===== (cancelaciones programadas: %d)%n", dia, hoy.size());
        }

        for (Cancelacion c : hoy) {
            if (c.salidaMin > relojMinDelDia) {
                relojMinDelDia = c.salidaMin;
            }

            String llave = c.llave();
            if (!vueloNoHaSalido(c.salidaMin)) {
                if (params.isVerbose()) {
                    System.out.printf("[SIM] %02d:%02d Ignorada cancelación (vuelo ya despegó): %s%n",
                            c.salidaMin/60, c.salidaMin%60, llave);
                }
                continue;
            }

            boolean cancelado = grafo.cancelarVuelo(llave);
            if (!cancelado) {
                if (params.isVerbose()) {
                    System.out.printf("[SIM] %02d:%02d Cancelación no aplicada (llave inexistente o ya cancelado): %s%n",
                            c.salidaMin/60, c.salidaMin%60, llave);
                }
                continue;
            }
            totalEventosCancel++;

            List<Asignacion> impactadas = asignacionesPorLlave.getOrDefault(llave, List.of());
            int numImpactadas = impactadas.size();
            totalPedidosImpactados += numImpactadas;

            if (params.isVerbose()) {
                System.out.printf("[SIM] %02d:%02d Vuelo cancelado: %s — asignaciones impactadas: %d%n",
                        c.salidaMin/60, c.salidaMin%60, llave, numImpactadas);
            }

            if (numImpactadas == 0) {
                continue;
            }

            List<Pedido> pedidosReplan = extraerPedidosUnicos(impactadas);
            List<Asignacion> nuevas = replanificador.replanificar(
                    pedidosReplan, grafo, params, this.asignaciones, dia, relojMinDelDia
            );
            totalReplanificaciones++;

            aplicarReemplazos(impactadas, nuevas);
            indexarAsignacionesPorVuelo();

            if (params.isVerbose()) {
                long sinRuta = nuevas.stream().filter(a -> a == null || a.getRuta() == null).count();
                System.out.printf("[SIM] Replan aplicado. Nuevas asignaciones: %d (sin ruta: %d)%n",
                        nuevas.size(), sinRuta);
            }
        }

        simularCierreDelDia(dia, params);
    }

    // ===== Helpers =====

    private boolean vueloNoHaSalido(int salidaMin) {
        return relojMinDelDia <= salidaMin;
    }

    private void indexarAsignacionesPorVuelo() {
        asignacionesPorLlave.clear();
        for (Asignacion a : asignaciones) {
            if (a == null || a.getRuta() == null || a.getRuta().vuelosUsados == null) continue;
            for (Integer vueloId : a.getRuta().vuelosUsados) {
                if (vueloId == null) continue;
                String llave = grafo.llaveDe(vueloId);
                if (llave == null) continue;
                asignacionesPorLlave.computeIfAbsent(llave, k -> new ArrayList<>()).add(a);
            }
        }
    }

    private List<Pedido> extraerPedidosUnicos(List<Asignacion> impactadas) {
        LinkedHashSet<String> vistos = new LinkedHashSet<>();
        List<Pedido> out = new ArrayList<>();
        for (Asignacion a : impactadas) {
            if (a == null || a.getPedido() == null) continue;
            String id = a.getPedido().id;
            if (vistos.add(id)) {
                out.add(a.getPedido());
            }
        }
        return out;
    }

    private void aplicarReemplazos(List<Asignacion> viejas, List<Asignacion> nuevas) {
        Set<String> idsViejos = new HashSet<>();
        for (Asignacion a : viejas) {
            if (a != null && a.getPedido() != null) idsViejos.add(a.getPedido().id);
        }
        asignaciones.removeIf(a -> a != null && a.getPedido() != null && idsViejos.contains(a.getPedido().id));
        if (nuevas != null && !nuevas.isEmpty()) {
            asignaciones.addAll(nuevas);
        }
    }

    private void simularCierreDelDia(int dia, ParametrosAco params) {
        if (params.isVerbose()) {
            long atendidos = asignaciones.stream().filter(a -> a != null && a.getRuta() != null).count();
            long sinRuta = asignaciones.size() - atendidos;
            System.out.printf("[SIM] Cierre D%02d — Asignaciones totales: %d, atendidos: %d, sin ruta: %d%n",
                    dia, asignaciones.size(), atendidos, sinRuta);
        }
    }
}
