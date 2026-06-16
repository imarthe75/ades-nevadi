package mx.ades.modules.admin.domain.model;

/**
 * Modela la jerarquía de permisos administrativos de ADES.
 * nivelAcceso 0 = ADMIN_GLOBAL, 1 = ADMIN_PLANTEL, >1 = sin permiso admin.
 */
public record PermisoAdmin(int nivelAcceso) {

    public static final int ADMIN_GLOBAL = 0;
    public static final int ADMIN_PLANTEL = 1;

    public boolean esAdminGlobal() {
        return nivelAcceso == ADMIN_GLOBAL;
    }

    public boolean esAdmin() {
        return nivelAcceso <= ADMIN_PLANTEL;
    }

    public boolean puedeAsignarRol(int nivelRolDestino) {
        return nivelRolDestino >= nivelAcceso;
    }

    public boolean puedeEditarOtrosPlantelUsuarios() {
        return esAdminGlobal();
    }
}
