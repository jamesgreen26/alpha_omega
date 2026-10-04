package g_mungus.alpha_omega.sky;

import static g_mungus.alpha_omega.sky.PlanetProjection.wrapTurns;

import g_mungus.alpha_omega.sky.PlanetProjection.Position;

/**
 * X is longitude, one turn per lap. Z is the angle round a meridian: walking toward -Z goes over the north pole (a
 * quarter lap), down the far side of the planet (half a lap away, 12 hours off and facing south) and back up over the
 * south pole, so walking either axis turns the sky steadily one way.
 */
public final class MeridianLoopProjection implements PlanetProjection.Projection {

    @Override
    public Position project(double x, double z) {
        double longitude = wrapTurns(x);
        double meridian = 2.0 * Math.PI * wrapTurns(-z);
        double latitude = Math.atan2(Math.sin(meridian), Math.abs(Math.cos(meridian)));
        if (Math.cos(meridian) >= 0.0) return new Position(longitude, latitude, 0.0);
        return new Position(wrapTurns(longitude + 0.5), latitude, Math.PI);
    }
}
