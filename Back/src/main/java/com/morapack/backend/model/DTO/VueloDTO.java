package com.morapack.backend.model.DTO;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO para la transferencia de datos de Vuelo en la API REST.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VueloDTO {

    private int id;
    private String origen;
    private String destino;
    private int capacidadMax; // Capacidad total
    private double horasDuracion;
    private boolean esContinental;

    // Opcional: Se podrían incluir las horas en formato HH:mm (String),
    // pero se omite la representación en minutos para una API más limpia.
}