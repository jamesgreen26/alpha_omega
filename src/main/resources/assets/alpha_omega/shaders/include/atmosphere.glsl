// The atmosphere of g_mungus.alpha_omega.sky.AtmosphereModel and SkyColors, ported line for line so that the GPU sky
// matches the CPU's fog, cloud and sky colours. Lengths in km; +Y up; the planet's centre straight below the viewer.

#define PI 3.14159265358979

uniform sampler2D Transmittance;
uniform sampler2D MultiScattering;

// x: ground radius, y: top radius
uniform vec2 Radii;
uniform vec3 RayleighScattering;
uniform float RayleighScaleHeight;
// x: scattering, y: extinction, z: scale height, w: g
uniform vec4 Mie;
uniform vec3 OzoneAbsorption;
// x: centre, y: half width
uniform vec2 Ozone;

const vec2 TRANSMITTANCE_SIZE = vec2(256.0, 64.0);
const float MULTI_SCATTERING_SIZE = 32.0;
const int VIEW_STEPS = 30;

float distanceToSphere(float r, float mu, float radius, bool nearest) {
    float discriminant = r * r * (mu * mu - 1.0) + radius * radius;
    if (discriminant < 0.0) return -1.0;
    float root = sqrt(discriminant);
    float far = -r * mu + root;
    float near = -r * mu - root;
    if (nearest) return near >= 0.0 ? near : (far >= 0.0 ? far : -1.0);
    return far >= 0.0 ? far : -1.0;
}

bool hitsGround(float r, float mu) {
    return mu < 0.0 && r * r * (mu * mu - 1.0) + Radii.x * Radii.x >= 0.0;
}

vec2 transmittanceUv(float r, float mu) {
    float h = sqrt(Radii.y * Radii.y - Radii.x * Radii.x);
    float rho = sqrt(max(0.0, r * r - Radii.x * Radii.x));
    float discriminant = r * r * (mu * mu - 1.0) + Radii.y * Radii.y;
    float d = max(0.0, -r * mu + sqrt(max(0.0, discriminant)));
    float dMin = Radii.y - r;
    float dMax = rho + h;
    return vec2((d - dMin) / (dMax - dMin), rho / h);
}

// Unit table coordinates to texture coordinates through texel centres, as the CPU's bilinear lookup does.
vec2 texelUv(vec2 unit, vec2 size) {
    return (clamp(unit, 0.0, 1.0) * (size - 1.0) + 0.5) / size;
}

vec3 transmittanceTo(float r, float mu) {
    return texture(Transmittance, texelUv(transmittanceUv(r, mu), TRANSMITTANCE_SIZE)).rgb;
}

vec3 lightTransmittance(float r, float mu) {
    return hitsGround(r, mu) ? vec3(0.0) : transmittanceTo(r, mu);
}

vec3 multiScattering(float r, float muS) {
    vec2 unit = vec2(muS * 0.5 + 0.5, (r - Radii.x) / (Radii.y - Radii.x));
    return texture(MultiScattering, texelUv(unit, vec2(MULTI_SCATTERING_SIZE))).rgb;
}

float rayleighPhase(float cosine) {
    return 3.0 / (16.0 * PI) * (1.0 + cosine * cosine);
}

float miePhase(float g, float cosine) {
    float k = 3.0 / (8.0 * PI) * (1.0 - g * g) / (2.0 + g * g);
    return k * (1.0 + cosine * cosine) / pow(1.0 + g * g - 2.0 * g * cosine, 1.5);
}

vec3 radiance(float altitude, vec3 dir, vec3 light) {
    float r = Radii.x + max(altitude, 1e-3);
    float mu = dir.y;
    float len = hitsGround(r, mu) ? distanceToSphere(r, mu, Radii.x, true) : distanceToSphere(r, mu, Radii.y, false);
    vec3 result = vec3(0.0);
    if (len <= 0.0) return result;
    float cosine = dot(dir, light);
    float phaseR = rayleighPhase(cosine);
    float phaseM = miePhase(Mie.w, cosine);
    vec3 throughput = vec3(1.0);
    float dt = len / float(VIEW_STEPS);
    for (int s = 0; s < VIEW_STEPS; s++) {
        float t = (float(s) + 0.3) * dt;
        vec3 p = vec3(0.0, r, 0.0) + dir * t;
        float radius = length(p);
        float h = radius - Radii.x;
        float muLight = dot(p, light) / radius;
        vec3 lightT = lightTransmittance(radius, muLight);
        vec3 multi = multiScattering(radius, muLight);
        float rayleigh = exp(-h / RayleighScaleHeight);
        float mie = exp(-h / Mie.z) * Mie.x;
        vec3 rayleighScattering = RayleighScattering * rayleigh;
        vec3 extinction = rayleighScattering + exp(-h / Mie.z) * Mie.y + OzoneAbsorption * max(0.0, 1.0 - abs(h - Ozone.x) / Ozone.y);
        vec3 source = lightT * (rayleighScattering * phaseR + mie * phaseM) + multi * (rayleighScattering + mie);
        vec3 step = exp(-extinction * dt);
        result += throughput * (source - source * step) / max(extinction, vec3(1e-12));
        throughput *= step;
    }
    return result;
}

// Hillaire's sky-view table: x is the azimuth from the sun (finer toward it), y the elevation (finer at the horizon;
// the upper half above the horizon, the lower half below).
vec2 skyViewUnit(float r, float mu, float cosAzimuth) {
    float horizon = sqrt(max(0.0, r * r - Radii.x * Radii.x));
    float beta = acos(clamp(horizon / r, -1.0, 1.0));
    float zenithHorizon = PI - beta;
    float zenith = acos(clamp(mu, -1.0, 1.0));
    float y = zenith < zenithHorizon
        ? 0.5 * (1.0 - sqrt(max(0.0, 1.0 - zenith / zenithHorizon)))
        : 0.5 + 0.5 * sqrt(max(0.0, (zenith - zenithHorizon) / beta));
    return vec2(sqrt(clamp(-cosAzimuth * 0.5 + 0.5, 0.0, 1.0)), y);
}

// Display colour: tonemap on luminance keeping the hue, then gamma (SkyColors.display).
vec3 displayColor(vec3 radiance, float exposure) {
    float y = dot(radiance, vec3(0.2126, 0.7152, 0.0722));
    if (y <= 0.0) return vec3(0.0);
    vec3 c = max(radiance, vec3(0.0)) * ((1.0 - exp(-exposure * y)) / y);
    float m = max(c.r, max(c.g, c.b));
    if (m > 1.0) c /= m;
    return pow(c, vec3(1.0 / 2.2));
}
