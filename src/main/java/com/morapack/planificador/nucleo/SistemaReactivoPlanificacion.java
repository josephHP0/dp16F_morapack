package com.morapack.planificador.nucleo;

import com.morapack.planificador.dominio.*;
import com.morapack.planificador.util.UtilArchivos;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SistemaReactivoPlanificacion {
    private final RelojSimulacion reloj;
    private final GestorEstadoVuelos gestorEstados;
    private final ParametrosAco parametros;
    private final ConcurrentLinkedQueue<Pedido> pedidosPendientes;
    private final List<CancelacionVuelo> cancelaciones;
    private final Map<String, Aeropuerto> aeropuertos;
    private final List<Vuelo> vuelosOriginales;
    private List<VueloTemporal> vuelosExpandidos;
    private volatile boolean sistemaActivo;
    
    public SistemaReactivoPlanificacion(Map<String, Aeropuerto> aeropuertos, 
                                       List<Vuelo> vuelos,
                                       ParametrosAco parametros) {
        this.reloj = new RelojSimulacion(1, 0, 60); // Día 1, 00:00, 1 hora virtual por segundo
        this.gestorEstados = new GestorEstadoVuelos(reloj);
        this.parametros = parametros;
        this.pedidosPendientes = new ConcurrentLinkedQueue<>();
        this.cancelaciones = new ArrayList<>();
        this.aeropuertos = aeropuertos;
        this.vuelosOriginales = new ArrayList<>(vuelos);
        this.sistemaActivo = false;
        
        inicializarVuelos(vuelos);
    }
    
    private void inicializarVuelos(List<Vuelo> vuelos) {
        // Expandir vuelos para todos los días del mes (1-31)
        for (Vuelo vuelo : vuelos) {
            gestorEstados.inicializarVuelo(vuelo);
        }
        
        // Crear vuelos expandidos para todos los días
        vuelosExpandidos = new ArrayList<>();
        for (int dia = 1; dia <= 31; dia++) {
            for (Vuelo vueloBase : vuelos) {
                VueloTemporal vueloTemporal = VueloTemporal.crear(vueloBase, dia);
                vuelosExpandidos.add(vueloTemporal);
            }
        }
        
        System.out.println("Vuelos expandidos: " + vuelosExpandidos.size() + " (vuelos diarios × 31 días)");
    }
    
    public void cargarCancelaciones(Path archivoCancelaciones) {
        try {
            List<CancelacionVuelo> cancelacionesCargadas = UtilArchivos.cargarCancelaciones(archivoCancelaciones);
            cancelaciones.addAll(cancelacionesCargadas);
            gestorEstados.procesarCancelaciones(cancelacionesCargadas);
            System.out.println("Cargadas " + cancelacionesCargadas.size() + " cancelaciones");
        } catch (IOException e) {
            System.err.println("Error cargando cancelaciones: " + e.getMessage());
        }
    }
    
    public void iniciarSistema() {
        if (sistemaActivo) return;
        
        sistemaActivo = true;
        reloj.iniciar();
        
        // Agendar replanificación periódica cada 6 horas virtuales
        agendarReplanificacionPeriodica();
        
        System.out.println("Sistema reactivo iniciado - Simulación en progreso...");
    }
    
    public void detenerSistema() {
        sistemaActivo = false;
        reloj.detener();
        System.out.println("Sistema reactivo detenido");
    }
    
    private void agendarReplanificacionPeriodica() {
        for (int dia = 1; dia <= 31; dia++) {
            for (int hora = 0; hora < 24; hora += 6) {
                final int diaFinal = dia;
                final int horaFinal = hora * 100; // Convertir a formato HHmm
                
                reloj.agendarEvento(dia, horaFinal, () -> {
                    if (sistemaActivo) {
                        ejecutarReplanificacion(diaFinal, horaFinal);
                    }
                });
            }
        }
    }
    
    private void ejecutarReplanificacion(int dia, int hora) {
        System.out.println(String.format("Replanificación día %d, hora %04d", dia, hora));
        
        // Obtener vuelos disponibles para este momento
        List<Vuelo> vuelosDisponibles = obtenerVuelosDisponibles(dia, hora);
        
        // Convertir pedidos pendientes a lista
        List<Pedido> pedidosActuales = new ArrayList<>();
        while (!pedidosPendientes.isEmpty()) {
            Pedido pedido = pedidosPendientes.poll();
            if (pedido != null) {
                pedidosActuales.add(pedido);
            }
        }
        
        if (!pedidosActuales.isEmpty() && !vuelosDisponibles.isEmpty()) {
            // Ejecutar planificación usando método estático
            List<Asignacion> planActualizado = PlanificadorAco.planificarConAco(
                aeropuertos, vuelosDisponibles, pedidosActuales, parametros, System.currentTimeMillis());
            
            // Reencolar pedidos no asignados completamente
            for (Asignacion asignacion : planActualizado) {
                if (asignacion.paquetesPendientes > 0) {
                    Pedido pedidoPendiente = new Pedido(
                        asignacion.pedido.id,
                        asignacion.pedido.destinoIata,
                        asignacion.paquetesPendientes,
                        asignacion.pedido.dia,
                        asignacion.pedido.hora,
                        asignacion.pedido.minuto
                    );
                    pedidosPendientes.offer(pedidoPendiente);
                }
            }
            
            // Mostrar estadísticas
            int pedidosCompletos = (int) planActualizado.stream()
                .mapToLong(a -> a.paquetesPendientes == 0 ? 1 : 0).sum();
            int paquetesAsignados = planActualizado.stream()
                .mapToInt(a -> a.paquetesAsignados).sum();
            
            System.out.println(String.format("  Pedidos completados: %d/%d", 
                pedidosCompletos, planActualizado.size()));
            System.out.println(String.format("  Paquetes asignados: %d", paquetesAsignados));
            System.out.println(String.format("  Vuelos disponibles: %d", vuelosDisponibles.size()));
        }
    }
    
    private List<Vuelo> obtenerVuelosDisponibles(int diaActual, int horaActual) {
        List<Vuelo> disponibles = new ArrayList<>();
        
        // Usar vuelos expandidos con información temporal
        for (VueloTemporal vueloTemporal : vuelosExpandidos) {
            // Solo considerar vuelos del día actual o posteriores
            if (vueloTemporal.dia >= diaActual) {
                // Si es el mismo día, verificar que el vuelo no haya salido ya
                if (vueloTemporal.dia > diaActual || vueloTemporal.horaSalida > horaActual) {
                    if (gestorEstados.obtenerEstado(vueloTemporal, vueloTemporal.dia) != EstadoVuelo.CANCELADO) {
                        disponibles.add(vueloTemporal); // VueloTemporal extends Vuelo
                    }
                }
            }
        }
        
        return disponibles;
    }
    
    public void agregarPedido(Pedido pedido) {
        pedidosPendientes.offer(pedido);
        System.out.println("Nuevo pedido agregado: " + pedido.id);
    }
    
    public void simularEntradaPedidos(List<Pedido> pedidos) {
        for (Pedido pedido : pedidos) {
            // Agendar la entrada del pedido en su momento programado
            int tiempoPedido = pedido.hora * 100 + pedido.minuto;
            reloj.agendarEvento(pedido.dia, tiempoPedido, () -> {
                agregarPedido(pedido);
            });
        }
    }
    
    public EstadisticasSimulacion obtenerEstadisticas() {
        Map<String, EstadoVuelo> estadosVuelos = gestorEstados.obtenerTodosLosEstados();
        
        int vuelosCancelados = 0;
        int vuelosCompletados = 0;
        int vuelosEnProceso = 0;
        
        for (EstadoVuelo estado : estadosVuelos.values()) {
            switch (estado) {
                case CANCELADO:
                    vuelosCancelados++;
                    break;
                case ATERRIZADO:
                    vuelosCompletados++;
                    break;
                case EN_VUELO:
                case EN_DESPEGUE:
                    vuelosEnProceso++;
                    break;
                case PROGRAMADO:
                    // Vuelos programados no se cuentan en ninguna categoría específica
                    break;
            }
        }
        
        return new EstadisticasSimulacion(
            reloj.getDiaActual(),
            reloj.getTiempoActual(),
            pedidosPendientes.size(),
            vuelosCancelados,
            vuelosCompletados,
            vuelosEnProceso,
            cancelaciones.size()
        );
    }
    
    public void establecerTiempo(int dia, int tiempo) {
        reloj.establecerTiempo(dia, tiempo);
    }
    
    public static class EstadisticasSimulacion {
        public final int diaActual;
        public final int tiempoActual;
        public final int pedidosPendientes;
        public final int vuelosCancelados;
        public final int vuelosCompletados;
        public final int vuelosEnProceso;
        public final int totalCancelaciones;
        
        public EstadisticasSimulacion(int diaActual, int tiempoActual, int pedidosPendientes,
                                     int vuelosCancelados, int vuelosCompletados, 
                                     int vuelosEnProceso, int totalCancelaciones) {
            this.diaActual = diaActual;
            this.tiempoActual = tiempoActual;
            this.pedidosPendientes = pedidosPendientes;
            this.vuelosCancelados = vuelosCancelados;
            this.vuelosCompletados = vuelosCompletados;
            this.vuelosEnProceso = vuelosEnProceso;
            this.totalCancelaciones = totalCancelaciones;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Día %d, %04d | Pendientes: %d | Vuelos [Cancelados: %d, Completados: %d, En proceso: %d] | Cancelaciones programadas: %d",
                diaActual, tiempoActual, pedidosPendientes, vuelosCancelados, 
                vuelosCompletados, vuelosEnProceso, totalCancelaciones
            );
        }
    }
}