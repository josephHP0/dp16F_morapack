package com.morapack.planificador.simulation;



import com.morapack.planificador.nucleo.Asignacion;
import java.util.List;

public class PlanResult {
    private final List<Asignacion> asignaciones;

    public PlanResult(List<Asignacion> asignaciones) {
        this.asignaciones = asignaciones;
    }

    public List<Asignacion> getAsignaciones() { return asignaciones; }
}
