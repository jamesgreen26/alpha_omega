#version 150

#moj_import <alpha_omega:atmosphere.glsl>

// Fills the sky-view table: radiance from the sun, the moon opposite it and airglow, for each azimuth from the sun
// and elevation, as seen from the camera's altitude. The sun sits in the x-y plane at azimuth 0.

uniform vec2 SkyViewSize;
uniform vec3 SunDir;
uniform float MoonIlluminance;
uniform float Altitude;
uniform vec3 Airglow;

out vec4 fragColor;

void main() {
    vec2 unit = (gl_FragCoord.xy - 0.5) / (SkyViewSize - 1.0);
    float r = Radii.x + max(Altitude, 1e-3);
    float horizon = sqrt(max(0.0, r * r - Radii.x * Radii.x));
    float beta = acos(clamp(horizon / r, -1.0, 1.0));
    float zenithHorizon = PI - beta;
    float zenith;
    if (unit.y < 0.5) {
        float c = 1.0 - 2.0 * unit.y;
        zenith = zenithHorizon * (1.0 - c * c);
    } else {
        float c = 2.0 * unit.y - 1.0;
        zenith = zenithHorizon + beta * c * c;
    }
    float cosAzimuth = 1.0 - 2.0 * unit.x * unit.x;
    float sinAzimuth = sqrt(max(0.0, 1.0 - cosAzimuth * cosAzimuth));
    float sinZenith = sin(zenith);
    vec3 dir = vec3(sinZenith * cosAzimuth, cos(zenith), sinZenith * sinAzimuth);
    float sunHorizontal = length(SunDir.xz);
    vec3 sun = vec3(sunHorizontal, SunDir.y, 0.0);
    vec3 light = radiance(Altitude, dir, sun);
    if (MoonIlluminance > 0.0) light += MoonIlluminance * radiance(Altitude, dir, -sun);
    fragColor = vec4(light + Airglow, 1.0);
}
