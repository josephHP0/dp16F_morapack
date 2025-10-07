package com.morapack.backend.model.DTO;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.TreeMap;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AeropuertoDTO {

    private int id;
    private String codigo;
    private String ciudad;
    private String pais;
    private String abreviaturaCiudad;
    private int gmt; // offset GMT
    private int capacidad;
    private String latitud;
    private String longitud;
    private String continente;
}