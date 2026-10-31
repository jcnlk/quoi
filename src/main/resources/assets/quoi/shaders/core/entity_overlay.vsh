#version 330
//~ entity_overlay_shader

//? if >=26.3 {
//#extension GL_ARB_separate_shader_objects : require
//#define LOCATION(n) layout(location = n)
//?} else {
#define LOCATION(n)
//?}

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

LOCATION(0) in vec3 Position;
LOCATION(1) in vec4 Color;
LOCATION(2) in vec2 UV0;
LOCATION(3) in ivec2 UV1;
LOCATION(4) in ivec2 UV2;
LOCATION(5) in vec3 Normal;

uniform sampler2D Sampler2;

LOCATION(0) out float sphericalVertexDistance;
LOCATION(1) out float cylindricalVertexDistance;
LOCATION(2) out vec4 vertexColor;
LOCATION(4) out vec4 lightMapColor;
LOCATION(5) out vec4 overlayColor;
LOCATION(6) out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
    lightMapColor = sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;

    // UV1 stores ARGB in two signed shorts
    // Vanilla's overlay blend uses inverse alpha
    overlayColor = vec4(
        float(UV1.y & 255),
        float((UV1.x >> 8) & 255),
        float(UV1.x & 255),
        255.0 - float((UV1.y >> 8) & 255)
    ) / 255.0;
}
