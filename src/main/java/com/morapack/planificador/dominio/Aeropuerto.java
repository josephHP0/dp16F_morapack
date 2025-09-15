package com.morapack.planificador.dominio;

public class Aeropuerto {
    public int id;
    public String codigo;
    public String ciudad;
    public String pais;
    public String abreviaturaCiudad;
    public int gmt; // offset GMT
    public int capacidad; 
    public String latitud;
    public String longitud;
    public String continente;
    public int cargaEntrante = 0;

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
    }
    //getters y setters
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public String getCodigo() {
        return codigo;
    }
    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }
    public String getCiudad() {
        return ciudad;
    }
    public void setCiudad(String ciudad) {
        this.ciudad = ciudad;
    }
    public String getPais() {
        return pais;
    }
    public void setPais(String pais) {
        this.pais = pais;
    }
    public String getAbreviaturaCiudad() {
        return abreviaturaCiudad;
    }
    public void setAbreviaturaCiudad(String abreviaturaCiudad) {
        this.abreviaturaCiudad = abreviaturaCiudad;
    }
    public int getGmt() {
        return gmt;
    }
    public void setGmt(int gmt) {
        this.gmt = gmt;
    }
    public int getCapacidad() {
        return capacidad;
    }
    public void setCapacidad(int capacidad) {
        this.capacidad = capacidad;
    }
    public String getLatitud() {
        return latitud;
    }
    public void setLatitud(String latitud) {
        this.latitud = latitud;
    }
    public String getLongitud() {
        return longitud;
    }
    public void setLongitud(String longitud) {
        this.longitud = longitud;
    }
    public String getContinente() {
        return continente;
    }
    public void setContinente(String continente) {
        this.continente = continente;
    }
    public int getCargaEntrante() {
        return cargaEntrante;
    }
    public void setCargaEntrante(int cargaEntrante) {
        this.cargaEntrante = cargaEntrante;
    }    

}
