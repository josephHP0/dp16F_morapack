package com.morapack.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.TreeMap;

/**
 * Entidad de Dominio que representa un Aeropuerto.
 * Contiene tanto los datos de persistencia (JPA) como el estado temporal
 * requerido por la lógica de simulación (Capa Service).
 */
@Entity
@Table(name = "aeropuertos")
@Data // Incluye Getters, Setters, toString, equals y hashCode
@NoArgsConstructor
@ToString(callSuper = true) // Incluye los campos de la superclase si existiera, o simplemente personaliza el toString
public class Aeropuerto {

    // --- 1. Campos de Persistencia (Base de Datos) ---

    // El código IATA/IOCA (e.g., 'SPIM') se usa como clave primaria por ser un identificador de negocio
    @Id
    private String codigo;

    // Se mantiene 'id' si es usado internamente por tu lógica para mantener la correspondencia con archivos
    // Si no es clave primaria, se debe remover o renombrar para evitar confusiones.
    @Transient // Indica que este campo NO se guardará en la DB (solo uso interno del modelo)
    private int id;

    @Column(nullable = false)
    private String ciudad;

    @Column(nullable = false)
    private String pais;

    @Column(name = "abreviatura_ciudad")
    private String abreviaturaCiudad;

    @Column(name = "gmt_offset")
    private int gmt; // offset GMT

    private int capacidad;
    private String latitud;
    private String longitud;
    private String continente;

    // --- 2. Campos de Estado Temporal (Solo para la Simulación ACO/Capa Service) ---
    // Usamos @Transient para indicar que JPA debe ignorar estos campos.

    @Transient
    public int cargaEntrante = 0; // Se mantiene en 'public' si es accedido directamente por la lógica de simulación

    @Transient
    // Almacena la ocupación por minuto: Minuto del día -> Paquetes
    public TreeMap<Integer, Integer> ocupacionPorMinuto = new TreeMap<>();


    // --- 3. Constructor (Para ser usado por la lógica de carga de archivos en UtilArchivos) ---
    public Aeropuerto(int id, String codigo, String ciudad, String pais,
                      String abreviaturaCiudad, int gmt, int capacidad, String latitud,
                      String longitud, String continente, int cargaEntrante) {
        this.id = id;
        this.codigo = codigo;
        this.ciudad = ciudad;
        this.pais = pais;
        this.abreviaturaCiudad = abreviaturaCiudad;
        this.gmt = gmt;
        this.capacidad = capacidad;
        this.latitud = latitud;
        this.longitud = longitud;
        this.continente = continente;
        this.cargaEntrante = cargaEntrante;
    }
}