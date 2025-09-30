# Sistema Reactivo de Planificación con ACO

Este documento explica la implementación completa del **Sistema Reactivo de Planificación** que maneja cancelaciones de vuelos y pedidos dinámicos usando algoritmos ACO (Ant Colony Optimization).

## 🏗️ Arquitectura del Sistema

### Componentes Principales

1. **EstadoVuelo** - Enum que define los estados de vuelos
2. **CancelacionVuelo** - Modelo de cancelaciones
3. **RelojSimulacion** - Manejo del tiempo virtual
4. **GestorEstadoVuelos** - Control de estados y cancelaciones
5. **SistemaReactivoPlanificacion** - Coordinador principal
6. **DemoSistemaReactivo** - Demostración del sistema

### Estados de Vuelos

```java
public enum EstadoVuelo {
    PROGRAMADO    // Vuelo programado, puede ser cancelado
    EN_DESPEGUE   // 5 minutos antes del despegue
    EN_VUELO      // Vuelo en curso
    ATERRIZADO    // Vuelo completado
    CANCELADO     // Vuelo cancelado
}
```

## 📋 Funcionalidades Implementadas

### ✈️ Gestión de Cancelaciones
- Carga de 66 cancelaciones mensuales desde `data/cancelaciones_vuelos.txt`
- Formato: `dd.ORIG-DEST-HHmm` (ej: `15.SPIM-EBCI-0830`)
- Cancelación automática cuando se cumple el tiempo programado
- Solo se cancelan vuelos que no han despegado

### ⏰ Simulación Temporal
- **Tiempo Virtual**: 1 minuto virtual = 1 segundo real (configurable)
- **Eventos Programados**: Transiciones automáticas de estado
- **Sincronización**: Múltiples hilos coordinados
- **Control**: Inicio/pausa/detención del reloj

### 🔄 Replanificación Reactiva
- **Frecuencia**: Cada 6 horas virtuales (6 segundos reales)
- **Triggers**: Cancelaciones, nuevos pedidos, cambios de estado
- **Algoritmo**: ACO optimizado para respuesta rápida
- **Adaptación**: Usa solo vuelos disponibles en cada momento

### 📊 Monitoreo en Tiempo Real
- **Estadísticas Continuas**: Estado de vuelos, pedidos pendientes
- **Alertas**: Cancelaciones, pedidos urgentes
- **Métricas**: Eficiencia operacional, completitud

## 🚀 Cómo Usar el Sistema

### Ejecutar la Demostración

```bash
# Compilar el proyecto
mvn compile

# Ejecutar la demo
mvn exec:java -Dexec.mainClass="com.morapack.planificador.nucleo.DemoSistemaReactivo"
```

### Integración Programática

```java
// 1. Crear sistema
SistemaReactivoPlanificacion sistema = new SistemaReactivoPlanificacion(
    aeropuertos, vuelos, parametrosACO);

// 2. Cargar cancelaciones
sistema.cargarCancelaciones(Paths.get("data/cancelaciones_vuelos.txt"));

// 3. Programar pedidos
sistema.simularEntradaPedidos(listaPedidos);

// 4. Iniciar simulación
sistema.iniciarSistema();

// 5. Monitorear
EstadisticasSimulacion stats = sistema.obtenerEstadisticas();

// 6. Agregar pedidos dinámicamente
sistema.agregarPedido(nuevoPedido);

// 7. Finalizar
sistema.detenerSistema();
```

## 📁 Estructura de Archivos

### Archivos de Datos
```
data/
├── aeropuertos.txt         # Información de aeropuertos
├── vuelos.txt              # Horarios y capacidades
├── pedidos.txt             # Pedidos de clientes
└── cancelaciones_vuelos.txt # 66 cancelaciones mensuales
```

### Formato de Cancelaciones
```
01.SPIM-EBCI-0630    # Día 1: Lima->Bruselas cancelado a las 06:30
15.EBCI-UBBB-1420    # Día 15: Bruselas->Bakú cancelado a las 14:20
28.UBBB-SPIM-0845    # Día 28: Bakú->Lima cancelado a las 08:45
```

## ⚙️ Configuración del Sistema

### Parámetros ACO Reactivo
```java
ParametrosAco parametros = new ParametrosAco();
parametros.hormigas = 30;      // Menos hormigas = mayor velocidad
parametros.iteraciones = 50;   // Iteraciones rápidas
parametros.rho = 0.15;         // Evaporación media
parametros.alpha = 1.2;        // Peso feromona
parametros.beta = 2.5;         // Peso heurística
```

### Velocidad de Simulación
```java
RelojSimulacion reloj = new RelojSimulacion(
    1,    // día inicial
    0,    // hora inicial (00:00)  
    60    // minutos virtuales por segundo real
);
```

## 📈 Salida del Sistema

### Estadísticas en Tiempo Real
```
⏰ T+00s: Día 1, 0000 | Pendientes: 20 | Vuelos [Cancelados: 0, Completados: 0, En proceso: 0] | Cancelaciones programadas: 66
⏰ T+05s: Día 1, 0300 | Pendientes: 18 | Vuelos [Cancelados: 1, Completados: 5, En proceso: 12] | Cancelaciones programadas: 66
📬 Lote adicional de 20 pedidos agregado
🚨 Pedido urgente agregado: URG001
⏰ T+20s: Día 1, 1200 | Pendientes: 15 | Vuelos [Cancelados: 3, Completados: 23, En proceso: 8] | Cancelaciones programadas: 66
```

### Resumen Final
```
📊 RESUMEN FINAL DE LA SIMULACIÓN
============================================================
🕐 Tiempo simulado final: Día 1, 1500
📦 Pedidos pendientes: 12
✈️ Estado de vuelos:
   • Cancelados: 4
   • Completados: 28
   • En proceso: 5
🗂️ Cancelaciones programadas: 66
📈 Eficiencia operacional: 75.7% (28/37 vuelos completados)
```

## 🔧 Aspectos Técnicos

### Manejo de Concurrencia
- **Thread Safety**: ConcurrentHashMap para estados de vuelos
- **Sincronización**: RelojSimulacion con hilos coordinados
- **Colas Thread-Safe**: ConcurrentLinkedQueue para pedidos

### Optimizaciones de Rendimiento
- **ACO Rápido**: Parámetros ajustados para respuesta rápida
- **Filtrado Inteligente**: Solo considera vuelos disponibles
- **Capacidad Dinámica**: Tracking por día y renovación automática

### Gestión de Estados
- **Transiciones Automáticas**: Basadas en tiempo simulado
- **Validación de Cancelaciones**: Solo antes del despegue
- **Persistencia de Estado**: Mantiene histórico completo

## 🎯 Casos de Uso Demostrados

1. **Carga Inicial**: Sistema arranca con datos estáticos
2. **Cancelaciones Programadas**: 66 cancelaciones procesadas automáticamente
3. **Pedidos Dinámicos**: Entrada de pedidos durante la simulación
4. **Replanificación**: Adaptación automática cada 6 horas virtuales
5. **Pedidos Urgentes**: Manejo de pedidos de alta prioridad
6. **Monitoreo Continuo**: Estadísticas en tiempo real

## 🏆 Ventajas del Sistema

✅ **Reactividad**: Responde a cambios en tiempo real  
✅ **Escalabilidad**: Maneja múltiples eventos concurrentes  
✅ **Flexibilidad**: Configurable y extensible  
✅ **Robustez**: Manejo de errores y estados inconsistentes  
✅ **Observabilidad**: Monitoreo y métricas completas  
✅ **Realismo**: Simula condiciones operacionales reales  

---

## 🚀 Próximos Pasos

Para extensiones futuras, el sistema puede incorporar:

- **Machine Learning**: Predicción de cancelaciones
- **Optimización Multi-objetivo**: Balancear costo, tiempo y satisfacción
- **Integración REST**: APIs para sistemas externos
- **Persistencia**: Base de datos para históricos
- **Dashboard**: Interfaz web para monitoreo
- **Alertas**: Notificaciones automáticas

El sistema reactivo implementado proporciona una base sólida para logística avanzada con capacidad de adaptación en tiempo real.