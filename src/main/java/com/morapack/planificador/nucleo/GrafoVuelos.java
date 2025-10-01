package com.morapack.planificador.nucleo;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Grafo de vuelos con índices bidireccionales y soporte de cancelaciones.
 * Requisitos mínimos:
 *  - cada vuelo tiene un id entero único
 *  - salidaMin/llegadaMin en minutos del día
 */


/**
 * Grafo de vuelos con índices bidireccionales y soporte de cancelaciones.
 */
public class GrafoVuelos {

    public static final class Vuelo {
        public final int id;
        public final String origen;
        public final String destino;
        public final int salidaMin;   // 0..1439
        public final int llegadaMin;  // 0..1439 (si cruza medianoche, duracionMin lo corrige)
        public boolean cancelado = false;

        public Vuelo(int id, String origen, String destino, int salidaMin, int llegadaMin) {
            this.id = id;
            this.origen = origen;
            this.destino = destino;
            this.salidaMin = salidaMin;
            this.llegadaMin = llegadaMin;
        }

        public int duracionMin() {
            int d = llegadaMin - salidaMin;
            return d >= 0 ? d : (1440 + d);
        }

        /** llave: ORIGEN>DESTINO@salidaMin */
        public String llave() { return origen + ">" + destino + "@" + salidaMin; }
    }

    // ====== CAMPOS ======
    private final List<Vuelo> vuelos;                            
    private final Map<Integer, Vuelo> vueloPorId = new HashMap<>();
    private final Map<String, List<Integer>> salidasDesde = new HashMap<>(); // origen -> ids
    private final Map<String, Integer> idPorLlave = new HashMap<>();
    private final Map<Integer, String> llavePorId = new HashMap<>();

    // ====== CONSTRUCTOR ======
    public GrafoVuelos(List<Vuelo> vuelos) {
        this.vuelos = new ArrayList<>(vuelos);
        for (Vuelo v : this.vuelos) {
            vueloPorId.put(v.id, v);
            idPorLlave.put(v.llave(), v.id);
            llavePorId.put(v.id, v.llave());
            salidasDesde.computeIfAbsent(v.origen, k -> new ArrayList<>()).add(v.id);
        }
        for (List<Integer> lst : salidasDesde.values()) {
            lst.sort(Comparator.comparingInt(a -> vueloPorId.get(a).salidaMin));
        }
    }

    // ====== API PARA ACO/REPLAN ======

    public List<Integer> todosLosVueloIds() {
        return vuelos.stream().map(v -> v.id).collect(Collectors.toList());
    }

    public List<Integer> vuelosDesde(String origen) {
        return salidasDesde.getOrDefault(origen, Collections.emptyList());
    }

    public String destinoDe(int vueloId) {
        Vuelo v = vueloPorId.get(vueloId);
        return (v != null) ? v.destino : null;
    }

    public int salidaMinDe(int vueloId) {
        Vuelo v = vueloPorId.get(vueloId);
        return (v != null) ? v.salidaMin : Integer.MIN_VALUE;
    }

    public int llegadaMinDe(int vueloId) {
        Vuelo v = vueloPorId.get(vueloId);
        return (v != null) ? v.llegadaMin : Integer.MAX_VALUE;
    }

    public int duracionMinDe(int vueloId) {
        Vuelo v = vueloPorId.get(vueloId);
        return (v != null) ? v.duracionMin() : Integer.MAX_VALUE;
    }

    public String llaveDe(int vueloId) {
        return llavePorId.get(vueloId);
    }

    public boolean estaCancelado(String llave) {
        Integer id = idPorLlave.get(llave);
        if (id == null) return false;
        Vuelo v = vueloPorId.get(id);
        return v != null && v.cancelado;
    }

    /**
     * Cancela el vuelo por llave y lo elimina de la lista de adyacencia.
     * Imprime traza a consola.
     */
    public boolean cancelarVuelo(String llave) {
        Integer id = idPorLlave.get(llave);
        if (id == null) {
            System.out.printf("[GRAFO] Solicitud de cancelación ignorada (llave inexistente): %s%n", llave);
            return false;
        }
        Vuelo v = vueloPorId.get(id);
        if (v == null) {
            System.out.printf("[GRAFO] Solicitud de cancelación ignorada (id inexistente): %s%n", llave);
            return false;
        }
        if (v.cancelado) {
            System.out.printf("[GRAFO] Vuelo ya cancelado: %s%n", llave);
            return false;
        }

        v.cancelado = true;
        List<Integer> lista = salidasDesde.getOrDefault(v.origen, new ArrayList<>());
        lista.removeIf(x -> x == v.id);
        System.out.printf("[GRAFO] Vuelo CANCELADO: %s (id=%d)%n", llave, v.id);
        return true;
    }

    // ====== Heurística restante (hops estimados) ======
    public double heuristicaRestante(String nodo, String destino) {
        if (nodo == null || destino == null) return 0.0;
        if (nodo.equals(destino)) return 0.0;
        int hops = estimarHopsMinimos(nodo, destino, 4); // límite de 4
        return (double) hops;
    }

    private int estimarHopsMinimos(String origen, String destino, int maxDepth) {
        if (origen.equals(destino)) return 0;
        Set<String> visit = new HashSet<>();
        ArrayDeque<String> q = new ArrayDeque<>();
        visit.add(origen);
        q.add(origen);
        int depth = 0;

        while (!q.isEmpty() && depth < maxDepth) {
            int sz = q.size();
            for (int i = 0; i < sz; i++) {
                String u = q.poll();
                for (int vueloId : vuelosDesde(u)) {
                    String v = destinoDe(vueloId);
                    if (v == null || visit.contains(v)) continue;
                    String llave = llaveDe(vueloId);
                    if (llave != null && estaCancelado(llave)) continue;
                    if (v.equals(destino)) return depth + 1;
                    visit.add(v);
                    q.add(v);
                }
            }
            depth++;
        }
        return maxDepth + 1;
    }

    // ====== Utilidad ======
    public List<Integer> candidatosConexion(String origen, int minutoDisponible) {
        List<Integer> all = salidasDesde.getOrDefault(origen, Collections.emptyList());
        List<Integer> out = new ArrayList<>();
        for (int id : all) {
            Vuelo v = vueloPorId.get(id);
            if (v.cancelado) continue;
            if (v.salidaMin >= minutoDisponible) out.add(id);
        }
        return out;
    }
}
