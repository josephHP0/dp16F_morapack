package com.morapack.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Entidad de dominio que representa un Vuelo programado.
 * Almacena la capacidad máxima y los datos fijos de la ruta.
 */
@Entity
@Table(name = "vuelos")
@Data // Genera Getters, Setters, toString, equals y hashCode
@NoArgsConstructor // Requerido por JPA
@AllArgsConstructor // Constructor con todos los campos (excepto los transient)
public class Vuelo {

    // --- 1. Campos de Persistencia (Base de Datos) ---

    // Usamos el ID original como clave primaria si es único
    @Id
    private int id;

    @Column(nullable = false)
    private String origen;

    @Column(nullable = false)
    private String destino;

    @Column(name = "salida_minutos", nullable = false)
    private int salidaMin;    // minutos desde 00:00 (Fijo)

    @Column(name = "llegada_minutos", nullable = false)
    private int llegadaMin;   // minutos desde 00:00 (Fijo)

    @Column(name = "capacidad_max", nullable = false)
    private int capacidadMax; // Capacidad TOTAL (Fijo en DB)

    @Column(name = "horas_duracion")
    private double horasDuracion;

    @Column(name = "es_continental")
    private boolean esContinental;

    // --- 2. Campos de Estado Temporal (Solo para la Simulación ACO) ---
    // El campo 'capacidad' se convierte en el estado disponible y se marca como Transient.

    @Transient // JPA ignora este campo
    public int capacidad; // Capacidad disponible (mutable y consumida durante la simulación)

    // --- 3. Constructor de Lógica (Usado por UtilArchivos.cargarVuelos) ---
    // Este constructor permite que UtilArchivos inicialice el estado "capacidad"
    // al valor de "capacidadMax" al cargar los datos.
    public Vuelo(int id, String origen, String destino, int salidaMin, int llegadaMin,
                 int capacidad, int duracionMinutos, boolean esContinental) {
        this.id = id;
        this.origen = origen;
        this.destino = destino;
        this.salidaMin = salidaMin;
        this.llegadaMin = llegadaMin;
        this.capacidadMax = capacidad;      // Capacidad Total
        this.capacidad = capacidad;         // Capacidad Disponible (Estado Temporal)
        this.horasDuracion = duracionMinutos / 60.0;
        this.esContinental = esContinental;
    }
}