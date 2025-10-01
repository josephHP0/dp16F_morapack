package com.morapack.planificador.nucleo;

import com.morapack.planificador.dominio.*;
import java.util.*;
// src/main/java/com/morapack/planner/aco/PlanificadorAco.java

import com.morapack.planificador.nucleo.ReplanificadorAco.SemillaFeromonaSupport;

/**
 * Planificador ACO con soporte de semillas de feromona para replanificación incremental.
 * - planificarInicial: para el plan base semanal.
 * - planificarPedidos: recalcula un subconjunto (p. ej., impactados por cancelación).
 *
 * NOTA: Este esqueleto deja bien definidos los puntos de integración con tu grafo y validadores de negocio.
 */
public class PlanificadorAco implements SemillaFeromonaSupport {

    // =======================
    // Campos y configuración
    // =======================
    private final Random rng;

    // Semillas opcionales (inyectadas por el replanificador):
    private Map<Integer, Double> semillasPorVueloId = new HashMap<>();
    private Map<String,  Double> semillasPorLlave   = new HashMap<>();

    // Feromonas (por vueloId). Si manejas feromonas por arista, este map es suficiente:
    private final Map<Integer, Double> tau = new HashMap<>();

    public PlanificadorAco() {
        this.rng = new Random(42);
    }

    public PlanificadorAco(long seed) {
        this.rng = new Random(seed);
    }

    // =====================================
    // API pública usada por tu orquestador
    // =====================================

    /** Plan inicial completo (proxy a planificarPedidos con toda la lista). */
    public List<Asignacion> planificarInicial(List<Pedido> pedidos, GrafoVuelos grafo, ParametrosAco p) {
        return planificarPedidos(pedidos, grafo, p);
    }

    /**
     * Planificación de un subconjunto de pedidos (modo incremental).
     * Reutiliza feromonas existentes y potencia con semillas si las hubiera.
     */
    public List<Asignacion> planificarPedidos(List<Pedido> pedidosSubset, GrafoVuelos grafo, ParametrosAco p) {
        if (pedidosSubset == null || pedidosSubset.isEmpty()) return List.of();

        // 0) Inicialización de feromonas (si es primera vez, valor base)
        inicializarFeromonasSiFalta(grafo, p);

        // 1) Ejecuta ACO sobre el subset
        Solucion mejor = ejecutarAco(pedidosSubset, grafo, p);

        // 2) Devuelve asignaciones listas
        return mejor.asignaciones;
    }

    // =====================================
    // Implementación de semillas (opcional)
    // =====================================

    @Override
    public void cargarSemillasPorVueloId(Map<Integer, Double> semillasPorVueloId) {
        this.semillasPorVueloId = (semillasPorVueloId != null) ? new HashMap<>(semillasPorVueloId)
                                                               : new HashMap<>();
    }

    @Override
    public void cargarSemillasPorLlave(Map<String, Double> semillasPorLlave) {
        this.semillasPorLlave = (semillasPorLlave != null) ? new HashMap<>(semillasPorLlave)
                                                           : new HashMap<>();
    }

    // ======================
    // Núcleo sencillo de ACO
    // ======================

    private static final class Solucion {
        List<Asignacion> asignaciones = new ArrayList<>();
        double costo = Double.POSITIVE_INFINITY;
    }

    private static final class Ant {
        List<Asignacion> parcial = new ArrayList<>();
        double costo = 0.0;
    }

    private Solucion ejecutarAco(List<Pedido> pedidos, GrafoVuelos grafo, ParametrosAco p) {
        Solucion mejor = new Solucion();
        int numHormigas = Math.max(1, p.getNumAnts());
        int maxIter = Math.max(1, p.getMaxIter());

        for (int iter = 0; iter < maxIter; iter++) {
            List<Ant> colonia = new ArrayList<>(numHormigas);

            for (int k = 0; k < numHormigas; k++) {
                Ant ant = construirSolucion(pedidos, grafo, p);
                colonia.add(ant);
                if (ant.costo < mejor.costo) {
                    mejor.costo = ant.costo;
                    mejor.asignaciones = ant.parcial;
                }
            }

            // Actualización de feromonas (evaporación + refuerzo por mejores soluciones)
            evaporar(p);
            reforzar(colonia, p);
        }

        // Si no se encontró nada viable, regresa lista vacía: el orquestador marcará “riesgo/colapso”.
        return mejor;
    }

    /** Construcción greedy-probabilística de soluciones para cada hormiga. */
    private Ant construirSolucion(List<Pedido> pedidos, GrafoVuelos grafo, ParametrosAco p) {
        Ant ant = new Ant();

        // Puedes ordenar por vencimiento SLA, prioridad, o mantener orden de entrada:
        List<Pedido> orden = new ArrayList<>(pedidos);
        // TODO (si aplica): Collections.sort(orden, Comparator.comparing(Pedido::getSlaMinuto));

        for (Pedido pedido : orden) {
            Ruta ruta = construirRutaParaPedido(pedido, grafo, p);
            if (ruta == null) {
                // No se encontró ruta factible: opcionalmente penaliza fuerte
                ant.costo += penalizacionNoAtendido(pedido, p);
                // Puedes agregar Asignacion con ruta null para que el orquestador lo trate.
                ant.parcial.add(new Asignacion(pedido, null));
            } else {
                ant.parcial.add(new Asignacion(pedido, ruta));
                ant.costo += costoRuta(ruta, p);
                // TODO: si tu simulación consume capacidad aquí, descuéntala (slots, peso, etc.)
            }
        }

        return ant;
    }

    /**
     * Búsqueda de ruta para un pedido usando transición ACO (tau^alpha * eta^beta * biasSemilla).
     * Aquí es donde el bias de semillas hace que el ACO “prefiera” subrutas previas exitosas.
     */
    private Ruta construirRutaParaPedido(Pedido pedido, GrafoVuelos grafo, ParametrosAco p) {
        // Este método depende de tu representación:
        // Suponemos que construyes la ruta como una secuencia de vueloId desde un origen (hub o actual) hasta destino pedido.dest
        // respetando ventana de tiempo, conexiones y SLA.

        // TODO: punto de partida real según tu modelo (¿hub?, ¿origen del pedido?):
        String nodoActual = /* p. ej. hub */ pedido.getNodoOrigen(); // ajusta si corresponde
        int tiempoActual = pedido.getMinutoDisponible();             // desde cuándo puede salir

        List<Integer> vuelosUsados = new ArrayList<>();
        int guardLimit = 200; // evita ciclos infinitos

        while (!nodoActual.equals(pedido.getNodoDestino()) && guardLimit-- > 0) {
            // 1) Obtener candidatos (vuelos) desde nodoActual que respeten horarios/capacidad:
            List<Integer> candidatos = obtenerVuelosFactibles(nodoActual, tiempoActual, pedido, grafo, p);
            if (candidatos.isEmpty()) break;

            // 2) Calcular probas con ACO:
            double[] prob = new double[candidatos.size()];
            double suma = 0.0;

            for (int i = 0; i < candidatos.size(); i++) {
                int vueloId = candidatos.get(i);

                double tau_ij = tau.getOrDefault(vueloId, p.getTau0());
                double eta_ij = heuristica(vueloId, pedido, grafo, p); // 1/distancia, slack, etc.
                double bias = biasSemilla(vueloId, grafo, p);          // semillas (si hay)

                double val = Math.pow(tau_ij, p.getAlpha()) * Math.pow(eta_ij, p.getBeta()) * bias;
                prob[i] = val;
                suma += val;
            }

            int selIdx;
            if (rng.nextDouble() < p.getQ0() && suma > 0) {
                // Exploit: escoger el máximo
                selIdx = argmax(prob);
            } else {
                // Explore: ruleta
                selIdx = ruleta(prob, suma);
                if (selIdx < 0) {
                    // Sin suma positiva; romper:
                    break;
                }
            }

            int elegido = candidatos.get(selIdx);
            vuelosUsados.add(elegido);

            // 3) Avanzar estado (nodoActual, tiempoActual):
            // TODO: utiliza tus getters del grafo para conocer destino del vuelo, hora de llegada, etc.
            String siguienteNodo = grafo.destinoDe(elegido);
            int llegadaMin = grafo.llegadaMinDe(elegido); // incluye duración + esperas si aplica

            nodoActual = siguienteNodo;
            tiempoActual = llegadaMin;

            // 4) Criterios de parada extra: si excede SLA, aborta
            if (!cumpleSlaParcial(pedido, tiempoActual, p)) {
                return null;
            }

            // 5) Si alcanzó destino, arma ruta
            if (nodoActual.equals(pedido.getNodoDestino())) {
                return construirRutaDesdeListaVuelos(vuelosUsados, grafo);
            }
        }

        // No se alcanzó el destino:
        return null;
    }

    // ==========================
    // Helpers de transición ACO
    // ==========================

    private double biasSemilla(int vueloId, GrafoVuelos grafo, ParametrosAco p) {
        // Multiplicador suave: 1 + k * normalizedSeed
        // Para mantener estabilidad, acotamos en [1, 1 + kMax]
        double k = 1.0; // puedes exponerlo en ParametrosAco si prefieres
        double semId = semillasPorVueloId.getOrDefault(vueloId, 0.0);
        String llave = grafo.llaveDe(vueloId);
        double semLlave = (llave != null) ? semillasPorLlave.getOrDefault(llave, 0.0) : 0.0;

        // Combina (suma) y normaliza suavemente:
        double s = semId + semLlave;   // si no tienes llaves, semLlave será 0
        if (s <= 0) return 1.0;

        // Normalización simple por capeo:
        double norm = Math.min(s, 5.0); // evitar blow-up; 5 es un tope razonable
        return 1.0 + k * (norm / 5.0);  // en [1, 2] aprox.
    }

    private int argmax(double[] v) {
        int idx = 0;
        double best = v[0];
        for (int i = 1; i < v.length; i++) {
            if (v[i] > best) { best = v[i]; idx = i; }
        }
        return idx;
    }

    private int ruleta(double[] prob, double suma) {
        if (suma <= 0) return -1;
        double r = rng.nextDouble() * suma;
        double acc = 0.0;
        for (int i = 0; i < prob.length; i++) {
            acc += prob[i];
            if (r <= acc) return i;
        }
        return prob.length - 1;
    }

    private void evaporar(ParametrosAco p) {
        double rho = p.getRho();
        for (Map.Entry<Integer, Double> e : tau.entrySet()) {
            tau.put(e.getKey(), (1.0 - rho) * e.getValue());
        }
    }

    private void reforzar(List<Ant> colonia, ParametrosAco p) {
        // Refuerzo tipo "elitista": usa la mejor hormiga de la iteración
        Ant best = null;
        for (Ant a : colonia) {
            if (best == null || a.costo < best.costo) best = a;
        }
        if (best == null) return;

        // Deposita feromona proporcional a 1/costo en los vuelos usados de sus rutas:
        double delta = (best.costo > 0) ? (1.0 / best.costo) : 1.0;

        for (Asignacion asg : best.parcial) {
            if (asg == null || asg.ruta == null || asg.ruta.vuelosUsados == null) continue;
            for (Integer vueloId : asg.ruta.vuelosUsados) {
                tau.merge(vueloId, delta, Double::sum);
            }
        }
    }

    private void inicializarFeromonasSiFalta(GrafoVuelos grafo, ParametrosAco p) {
        // Si tu grafo expone el universo de vueloIds, inicialízalos con tau0:
        double tau0 = p.getTau0();
        for (Integer vueloId : grafo.todosLosVueloIds()) { // TODO: expón este método en tu grafo
            tau.putIfAbsent(vueloId, tau0);
        }
    }

    // ==============================
    // Heurística y validadores base
    // ==============================

    /** Heurística por arista (vuelo): ↑ si acerca al destino, si es rápido y con holgura SLA. */
    private double heuristica(int vueloId, Pedido pedido, GrafoVuelos grafo, ParametrosAco p) {
        // Ejemplo: 1 / (duración + penalización distancia al destino)
        int durMin = grafo.duracionMinDe(vueloId); // TODO
        double distH = grafo.heuristicaRestante(grafo.destinoDe(vueloId), pedido.getNodoDestino()); // TODO (0 si no tienes)
        double val = 1.0 / (1.0 + durMin + distH);
        return Math.max(val, 1e-6);
    }

    private boolean cumpleSlaParcial(Pedido pedido, int llegadaMinAcumulada, ParametrosAco p) {
        // TODO: valida con tu política de SLA (deadline en minutos, hops máximos, etc.)
        return true;
    }

    private double penalizacionNoAtendido(Pedido pedido, ParametrosAco p) {
        // TODO: si tienes criticidad/peso del pedido, úsalo aquí.
        return 1e6;
    }

    private double costoRuta(Ruta r, ParametrosAco p) {
        // TODO: combina duración total, hops, penalizaciones por cercanía al SLA, distancia km, etc.
        return r.costoTotal; // si ya lo calculas; si no, haz tu fórmula
    }

    private Ruta construirRutaDesdeListaVuelos(List<Integer> vuelosUsados, GrafoVuelos grafo) {
        Ruta r = new Ruta();
        r.vuelosUsados = new ArrayList<>(vuelosUsados);
        // TODO: si en tu clase Ruta calculas tiempos/distancia/costo, invoca aquí el recálculo:
        // r.recalcular(grafo);
        return r;
    }

    /**
     * Devuelve ids de vuelos saliendo de nodoActual que:
     * - No estén cancelados,
     * - Cumplan con hora salida >= tiempoActual (con espera razonable),
     * - Respeten restricciones de capacidad/peso si aplica,
     * - No violen ventanas/SLA intermedio.
     */
    private List<Integer> obtenerVuelosFactibles(String nodoActual, int tiempoActual, Pedido pedido,
                                                 GrafoVuelos grafo, ParametrosAco p) {
        List<Integer> out = new ArrayList<>();
        for (int vueloId : grafo.vuelosDesde(nodoActual)) { // TODO: expón vuelosDesde(nodo) -> List<Integer>
            String llave = grafo.llaveDe(vueloId);
            if (llave != null && grafo.estaCancelado(llave)) continue;

            int salidaMin = grafo.salidaMinDe(vueloId);       // TODO
            if (salidaMin < tiempoActual) continue;           // ya partió (o no alcanzas la conexión)

            // TODO: validaciones adicionales (capacidad, ventanas del pedido, etc.)
            out.add(vueloId);
        }
        return out;
    }
}
