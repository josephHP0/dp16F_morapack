package com.morapack.backend.model;

import com.morapack.backend.model.Enum.EstadoPedido;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Entidad de dominio que representa un pedido.
 * Persistida en la base de datos con JPA.
 */
@Entity
@Table(name = "pedidos")
@Data // Genera Getters, Setters, toString, equals, y hashCode
@NoArgsConstructor // Requerido por JPA
@AllArgsConstructor // Genera constructor con todos los campos (útil para inyección)
public class Pedido {

    // --- 1. Campos de Persistencia (Base de Datos) ---

    // Usamos un ID autogenerado para la persistencia,
    // pero mantenemos el 'id' original (código de negocio)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long dbId;

    @Column(unique = true, nullable = false)
    private String id; // Cliente ID (código original del pedido)

    @Column(name = "destino_iata", nullable = false)
    private String destinoIata;   // Airport code

    @Column(nullable = false)
    private int paquetes;         // Cantidad de paquetes

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPedido estado = EstadoPedido.PENDIENTE;

    // --- 2. Campos de Estado Temporal (Sólo para la Simulación ACO) ---
    // JPA ignora estos campos (@Transient), que son necesarios para la lógica del ACO.

    @Transient
    public int dia;              // Día de mes (1-31)

    @Transient
    public int hora;             // Hora (0-23)

    @Transient
    public int minuto;           // Minuto (0-59)

    // --- 3. Constructor de Lógica (Usado por UtilArchivos.cargarPedidos) ---
    // Este constructor no es final y se mantiene para la carga inicial de datos,
    // ya que @AllArgsConstructor no incluye los campos @Transient si no hay @lombok.experimental.FieldNameConstants
    public Pedido(String id, String destinoIata, int paquetes, int dia, int hora, int minuto) {
        this.id = id;
        this.destinoIata = destinoIata;
        this.paquetes = paquetes;
        this.dia = dia;
        this.hora = hora;
        this.minuto = minuto;
    }
}