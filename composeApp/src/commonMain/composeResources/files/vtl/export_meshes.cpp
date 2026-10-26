// SPDX-License-Identifier: GPL-3.0-or-later
// Export adapter for VocalTractLab (Peter Birkholz and contributors).
// Builds against the pinned upstream sources; no synthesis engine is bundled in the app.
#include "VocalTractLabBackend/VocalTract.h"
#include <cstdint>
#include <cstring>
#include <fstream>
#include <iostream>
#include <memory>
#include <string>
#include <vector>

static void u32(std::ostream& out, uint32_t n) {
    for (int i = 0; i < 4; ++i) out.put(static_cast<char>((n >> (i * 8)) & 255));
}
static void f32(std::ostream& out, double value) {
    float f = static_cast<float>(value); uint32_t bits; std::memcpy(&bits, &f, 4); u32(out, bits);
}
int main(int argc, char** argv) {
    if (argc != 3) { std::cerr << "usage: export_meshes speaker_file output_directory\n"; return 1; }
    auto tract = std::make_unique<VocalTract>();
    tract->readFromXml(argv[1]);
    // Explicit source names, not synthesized or interpolated English poses.
    const std::vector<std::pair<std::string, std::string>> presets = {
        {"i", "fleece"}, {"I", "kit"}, {"E", "dress"}, {"U", "foot"}, {"u", "goose"}, {"O", "thought"}
    };
    for (const auto& [name, asset] : presets) {
        int shape = tract->getShapeIndex(name);
        if (shape < 0) { std::cerr << "Missing shape: " << name; return 2; }
        tract->setParams(tract->shapes[shape].param);
        tract->calculateAll();
        // Part identifiers are serialized independently of upstream enum values.
        const std::vector<std::pair<int, int>> parts = {
            {VocalTract::UPPER_COVER_TWOSIDE, 0}, {VocalTract::LOWER_COVER_TWOSIDE, 4},
            {VocalTract::UPPER_TEETH_TWOSIDE, 2}, {VocalTract::LOWER_TEETH_TWOSIDE, 2},
            {VocalTract::UPPER_LIP_TWOSIDE, 3}, {VocalTract::LOWER_LIP_TWOSIDE, 3},
            {VocalTract::TONGUE, 1}, {VocalTract::UVULA_TWOSIDE, 0},
            {VocalTract::EPIGLOTTIS_TWOSIDE, 4},
            {VocalTract::LEFT_COVER, 4}, {VocalTract::RIGHT_COVER, 4}
        };
        std::ofstream out(std::string(argv[2]) + "/" + asset + ".vtlm", std::ios::binary);
        out.write("VTLM", 4); u32(out, 1); u32(out, parts.size());
        int vertices = 0, triangles = 0;
        for (auto [index, material] : parts) {
            auto& surface = tract->surface[index];
            surface.calculateNormals();
            // Match upstream VocalTractPicture.cpp: omit hidden filling triangles at
            // the lip seam and cheek covers. Keep original coordinates/normals.
            std::vector<int> visible;
            for (int t = 0; t < surface.numTriangles; ++t) {
                if ((index == VocalTract::LEFT_COVER || index == VocalTract::RIGHT_COVER) && t > 5) continue;
                if ((index == VocalTract::UPPER_LIP_TWOSIDE || index == VocalTract::LOWER_LIP_TWOSIDE)
                    && t % ((surface.numRibPoints - 1) * 2) < 2) continue;
                visible.push_back(t);
            }
            u32(out, material); u32(out, surface.numVertices); u32(out, visible.size());
            for (int v = 0; v < surface.numVertices; ++v) {
                const auto& p = surface.vertex[v].coord;
                // Upstream cm: x anterior, y superior, z lateral. Preserve raw coordinates.
                f32(out, p.x); f32(out, p.y); f32(out, p.z);
            }
            for (int t : visible) {
                for (int c = 0; c < 3; ++c) u32(out, surface.triangle[t].vertex[c]);
                for (int c = 0; c < 3; ++c) {
                    const auto& n = surface.triangle[t].cornerNormal[c];
                    f32(out, n.x); f32(out, n.y); f32(out, n.z);
                }
            }
            vertices += surface.numVertices; triangles += visible.size();
        }
        if (!out) return 3;
        std::cout << name << ": " << vertices << " vertices, " << triangles << " triangles\n";
    }
}
