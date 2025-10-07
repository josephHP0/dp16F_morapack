package com.morapack.backend.model.DTO;

import com.morapack.backend.model.Enum.EstadoPedido;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO para la transferencia de datos de Pedido en la API REST.
 */
@Data
@Builder // Útil para crear DTOs de forma concisa
@NoArgsConstructor
@AllArgsConstructor
public class PedidoDTO {

    private Long id; // Corresponde al dbId de la entidad para el frontend
    private String codigoPedido; // Corresponde al id de la entidad (código de negocio)
    private String destinoIata;
    private int paquetes;
    private EstadoPedido estado;

    // NOTA: Se ha omitido 'dia', 'hora', y 'minuto'
    // Si la API REST necesita mostrar la fecha/hora, es mejor usar un solo campo
    // de tipo String o LocalDateTime en el DTO, derivado de los 3 campos de la entidad.
}