#version 150

#moj_import <alpha_omega:atmosphere.glsl>

// The sky behind everything: each pixel's view direction looks up the sky-view table, which is tonemapped like the
// CPU's colours, takes vanilla's rain, thunder and lightning tints, and fades into the fog colour at the horizon.

uniform sampler2D SkyView;
uniform vec2 SkyViewSize;
uniform mat4 InvViewProj;
uniform vec3 SunDir;
uniform float Altitude;
uniform float Exposure;
uniform vec4 FogColor;
// x: rain, y: thunder, z: lightning flash
uniform vec3 Weather;
// sine of the elevation by which the sky has fully emerged from the fog
uniform float HorizonFade;

in vec2 screenPos;

out vec4 fragColor;

vec3 weather(vec3 c, float strength, float greyScale, float keep) {
    float grey = dot(c, vec3(0.3, 0.59, 0.11)) * greyScale;
    float k = 1.0 - strength * keep;
    return c * k + vec3(grey) * (1.0 - k);
}

void main() {
    vec4 far = InvViewProj * vec4(screenPos, 1.0, 1.0);
    vec3 dir = normalize(far.xyz / far.w);
    float r = Radii.x + max(Altitude, 1e-3);
    vec2 sunH = SunDir.xz;
    vec2 viewH = dir.xz;
    float cosAzimuth = (length(sunH) < 1e-5 || length(viewH) < 1e-5) ? 1.0 : dot(normalize(viewH), normalize(sunH));
    vec2 unit = skyViewUnit(r, dir.y, cosAzimuth);
    vec3 light = texture(SkyView, texelUv(unit, SkyViewSize)).rgb;
    vec3 c = displayColor(light, Exposure);
    if (Weather.x > 0.0) c = weather(c, Weather.x, 0.6, 0.75);
    if (Weather.y > 0.0) c = weather(c, Weather.y, 0.2, 0.75);
    if (Weather.z > 0.0) c = c * (1.0 - Weather.z) + vec3(0.8, 0.8, 1.0) * Weather.z;
    float emerged = smoothstep(0.0, HorizonFade, dir.y);
    fragColor = vec4(mix(FogColor.rgb, c, emerged), 1.0);
}
