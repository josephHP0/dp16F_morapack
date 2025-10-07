package com.morapack.planificador.dominio;




import java.util.Objects;

/**
 * Aeropuerto/Almacén. Capacidad es la del almacén.
 * Algunos hubs son "infiniteSource" (SPIM, EBCI, UBBB) para generación/abastecimiento.
 */
public class Aeropuerto {
    public final String iata;          // ej. SPIM
    public final String nombre;        // ej. Lima/Jorge Chavez
    public final double lat;
    public final double lon;
    public final int capacidadAlmacen; // si es infinito, este valor es ignorado en cálculo de % (pero útil para UI)
    public final boolean infiniteSource;

    public Aeropuerto(String iata, String nombre, double lat, double lon, int capacidadAlmacen, boolean infiniteSource) {
        this.iata = iata;
        this.nombre = nombre;
        this.lat = lat;
        this.lon = lon;
        this.capacidadAlmacen = capacidadAlmacen;
        this.infiniteSource = infiniteSource;
    }

    public boolean isInfiniteSource() { return infiniteSource; }
    public String getIata() { return iata; }
    public String getNombre() { return nombre; }
    public int getCapacidadAlmacen() { return capacidadAlmacen; }

    @Override
    public String toString() {
        return iata + " (" + nombre + ") cap=" + (infiniteSource ? "∞" : capacidadAlmacen);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Aeropuerto a)) return false;
        return Objects.equals(this.iata, a.iata);
    }

    @Override
    public int hashCode() { return Objects.hash(iata); }
}

