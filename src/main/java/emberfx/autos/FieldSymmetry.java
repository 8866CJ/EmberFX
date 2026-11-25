package emberfx.autos;

/**
 * How a game's field maps one alliance's side onto the other. PathPlannerLib (FlippingUtil) and ChoreoLib
 * (ChoreoAllianceFlipUtil) both default to ROTATIONAL; MIRRORED is for games with a left-right mirrored field.
 */
public enum FieldSymmetry {
    /** (x, y, h) becomes (L - x, W - y, h + 180). */
    ROTATIONAL,
    /** (x, y, h) becomes (L - x, y, 180 - h). */
    MIRRORED
}
