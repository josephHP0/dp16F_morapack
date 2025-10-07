# Sistema de Planificación de Rutas MoraPack

## Descripción

Sistema inteligente de planificación de rutas logísticas que utiliza el algoritmo ACO (Ant Colony Optimization) con capacidades reactivas para manejar cancelaciones de vuelos en tiempo real. El sistema implementa un planificador temporal realista que gestiona recursos y estados a través de simulaciones de múltiples días.

## Características Principales

### Planificación Inteligente
- **Algoritmo ACO optimizado** con parámetros adaptativos según el tamaño del problema
- **Análisis automático** de configuración y recomendaciones de parámetros óptimos
- **Planificación reactiva** con replanificación automática ante cancelaciones

### Simulación Temporal Realista
- **Simulación de 31 días** con estados persistentes entre días
- **Gestión temporal de recursos** con ciclos de recogida de clientes (2 horas)
- **Estados de vuelos** que mantienen cargo hasta completar rutas
- **Capacidades de almacén** con ocupación real y liberación gradual

### Monitoreo y Análisis
- **Monitoreo día a día** con análisis detallado de cancelaciones
- **Métricas de eficiencia** en tiempo real
- **Análisis de impactos** de cancelaciones sobre pedidos
- **Diagnóstico automático** de problemas de conectividad y distribución

## Estructura del Proyecto

```
dp16F_morapack/
├── src/main/java/com/morapack/planificador/
│   ├── dominio/
│   │   ├── Aeropuerto.java          # Entidad aeropuerto
│   │   ├── Pedido.java              # Entidad pedido
│   │   └── Vuelo.java               # Entidad vuelo
│   ├── nucleo/
│   │   ├── AppPlanificador.java     # Aplicación principal con simulación temporal
│   │   ├── AnalisisAlgoritmo.java   # Análisis y optimización de parámetros ACO
│   │   ├── Asignacion.java          # Gestión de asignaciones
│   │   ├── GrafoVuelos.java         # Estructura de grafo de vuelos
│   │   ├── ParametrosAco.java       # Configuración de parámetros ACO
│   │   ├── PlanificadorAco.java     # Implementación del algoritmo ACO
│   │   └── Ruta.java                # Representación de rutas
│   └── util/
│       └── UtilArchivos.java        # Utilidades para manejo de archivos
├── data/                            # Archivos de datos
│   ├── aeropuertos.txt
│   ├── vuelos.txt
│   ├── pedidos.txt
│   └── cancelaciones.txt
├── pom.xml                          # Configuración Maven
└── README.md                        # Este archivo
```

## Instalación y Configuración

### Prerrequisitos
- Java 8 o superior
- Maven 3.6 o superior

### Instalación
1. Clonar el repositorio:
```bash
git clone [URL_DEL_REPOSITORIO]
cd dp16F_morapack
```

2. Compilar el proyecto:
```bash
mvn clean compile
```

3. Ejecutar el proyecto:
```bash
mvn exec:java -Dexec.mainClass="com.morapack.planificador.nucleo.AppPlanificador"
```

## Uso del Sistema

### Ejecución Básica
El sistema ejecuta automáticamente una simulación de 31 días con los datos disponibles en la carpeta `data/`.

### Configuración de Parámetros ACO
El sistema incluye análisis automático que recomienda parámetros óptimos:

```java
// Configuración automática según tamaño del problema
AnalisisAlgoritmo analisis = new AnalisisAlgoritmo();
ParametrosAco optimizados = analisis.recomendarParametrosOptimos(
    pedidos, aeropuertos, vuelos);
```

### Monitoreo en Tiempo Real
```java
// Ejecutar simulación con monitoreo detallado
planificador.ejecutarSimulacionConMonitoreo(31, cancelaciones);
```

## Algoritmo ACO Optimizado

### Parámetros Adaptativos
- **Problemas pequeños** (<100 pedidos): Velocidad optimizada (12 iteraciones)
- **Problemas medianos** (100-500 pedidos): Balance optimizado (25 iteraciones)  
- **Problemas grandes** (>500 pedidos): Calidad optimizada (40 iteraciones)

### Configuración por Defecto
- **Alpha (feromonas)**: 2.0
- **Beta (visibilidad)**: 3.0
- **Rho (evaporación)**: 0.4
- **Q (refuerzo)**: 75.0

## Gestión Temporal Realista

### Estados Persistentes
- **PaqueteEnAlmacen**: Gestiona ocupación de almacenes con tiempo de recogida
- **PaqueteEnVuelo**: Controla cargo en vuelos hasta completar rutas
- **EstadoVuelo**: Mantiene capacidad real disponible
- **EstadoAeropuerto**: Rastrea ocupación de almacenes

### Ciclos Temporales
- **Recogida de clientes**: Cada 2 horas liberan ubicaciones en almacén
- **Descargas de vuelos**: Al completar rutas liberan capacidad de carga
- **Persistencia entre días**: Estados se mantienen entre simulaciones diarias

## Métricas y Análisis

### Indicadores de Rendimiento
- **Eficiencia general**: Porcentaje de pedidos completados
- **Tiempo de ejecución**: Performance por día de simulación
- **Impacto de cancelaciones**: Análisis de pedidos afectados
- **Utilización de recursos**: Ocupación de almacenes y vuelos

### Diagnósticos Automáticos
- Análisis de conectividad de red
- Detección de problemas de distribución
- Recomendaciones de mejora de parámetros
- Identificación de cuellos de botella

## Resultados Típicos

### Rendimiento Optimizado
- **86.7%** eficiencia promedio en simulaciones de 31 días
- **Reducción del 68%** en paquetes pendientes (de 81% a 13.3%)
- **Tiempo promedio**: 2-3 segundos por día de simulación

### Capacidad de Respuesta
- **Replanificación automática** ante cancelaciones
- **Análisis de impactos** en tiempo real
- **Continuidad operacional** con estados persistentes

## API Principal

### Clases Clave

#### AppPlanificador
```java
// Ejecución de simulación completa
public void ejecutarSimulacionConMonitoreo(int dias, 
                                          List<PlanificadorAco.Cancelacion> cancelaciones)

// Análisis de día específico
private void analizarDia(int dia, List<Asignacion> asignaciones, 
                        List<PlanificadorAco.Cancelacion> cancelaciones)
```

#### AnalisisAlgoritmo
```java
// Análisis completo de configuración
public void analizarConfiguracion(ParametrosAco params, 
                                 List<Pedido> pedidos, 
                                 List<Aeropuerto> aeropuertos, 
                                 List<Vuelo> vuelos)

// Recomendación de parámetros óptimos
public ParametrosAco recomendarParametrosOptimos(List<Pedido> pedidos,
                                                List<Aeropuerto> aeropuertos,
                                                List<Vuelo> vuelos)
```

#### PlanificadorAco
```java
// Planificación principal
public List<Asignacion> planificar(List<Pedido> pedidos, 
                                  List<Vuelo> vuelos, 
                                  ParametrosAco parametros)

// Replanificación reactiva
public List<Asignacion> replanificar(List<Pedido> pedidosPendientes,
                                    List<Vuelo> vuelosDisponibles,
                                    ParametrosAco parametros)
```

## Archivos de Configuración

### Formato de Datos
- **aeropuertos.txt**: `codigo,nombre,capacidad_almacen`
- **vuelos.txt**: `codigo,origen,destino,capacidad`
- **pedidos.txt**: `id,origen,destino,dia`
- **cancelaciones.txt**: `vuelo,dia`

### Ejemplo de Datos
```
# aeropuertos.txt
LAX,Los Angeles,1000
JFK,New York,800

# vuelos.txt
AA101,LAX,JFK,150
UA202,JFK,LAX,200

# pedidos.txt
P001,LAX,JFK,1
P002,JFK,LAX,2
```

## Contribución

### Estructura de Commits
- `feat:` Nuevas características
- `fix:` Corrección de errores
- `docs:` Documentación
- `refactor:` Refactorización de código
- `test:` Pruebas

### Proceso de Desarrollo
1. Fork del repositorio
2. Crear rama de feature: `git checkout -b feature/nueva-caracteristica`
3. Commit de cambios: `git commit -m 'feat: agregar nueva característica'`
4. Push a la rama: `git push origin feature/nueva-caracteristica`
5. Crear Pull Request

## Licencia

Este proyecto está bajo la licencia MIT. Ver el archivo `LICENSE` para más detalles.

## Soporte y Contacto

Para soporte técnico o consultas sobre el proyecto:
- **Issues**: [GitHub Issues](link-to-issues)
- **Documentación**: [Wiki del proyecto](link-to-wiki)
- **Email**: [correo-de-contacto]

## Changelog

### v1.0.0 (2024)
- ✅ Implementación completa del algoritmo ACO
- ✅ Sistema de planificación reactiva
- ✅ Simulación temporal realista de 31 días
- ✅ Análisis automático de parámetros
- ✅ Monitoreo día a día con métricas detalladas
- ✅ Gestión de estados persistentes entre días
- ✅ Optimización de rendimiento (86.7% eficiencia)