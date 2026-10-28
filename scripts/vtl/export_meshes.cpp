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
#include <iomanip>

static void u32(std::ostream& out, uint32_t n) {
    for (int i = 0; i < 4; ++i) out.put(static_cast<char>((n >> (i * 8)) & 255));
}
static void f32(std::ostream& out, double value) {
    float f = static_cast<float>(value); uint32_t bits; std::memcpy(&bits, &f, 4); u32(out, bits);
}
int main(int argc, char** argv) {
    if (argc < 3 || argc > 4 || (argc == 4 && std::string(argv[3]) != "--consonants-only")) {
        std::cerr << "usage: export_meshes speaker_file output_directory [--consonants-only]\n"; return 1;
    }
    const bool consonantsOnly = argc == 4;
    auto tract = std::make_unique<VocalTract>();
    tract->readFromXml(argv[1]);
    // Original presets remain byte-for-byte unchanged. Additional English teaching
    // poses are explicit parameter recipes, not claimed native speaker presets.
    struct Recipe { std::string name, first, second; double secondWeight; };
    const std::vector<Recipe> recipes = {
        {"derived-trap", "a", "E", 0.35},
        {"derived-palm", "a", "a", 0.0},
        {"derived-strut", "a", "@", 0.50},
        {"derived-lot", "a", "O", 0.35}
    };
    std::ofstream provenance(std::string(argv[2]) + "/derived-poses.json");
    provenance << std::setprecision(10) << "{\n";
    for (size_t r = 0; r < recipes.size(); ++r) {
        const auto& recipe = recipes[r];
        const int first = tract->getShapeIndex(recipe.first), second = tract->getShapeIndex(recipe.second);
        if (first < 0 || second < 0) { std::cerr << "Missing recipe source"; return 2; }
        VocalTract::Shape shape;
        shape.name = recipe.name;
        for (int p = 0; p < VocalTract::NUM_PARAMS; ++p)
            shape.param[p] = tract->shapes[first].param[p] * (1 - recipe.secondWeight)
                + tract->shapes[second].param[p] * recipe.secondWeight;
        if (recipe.name == "derived-palm") {
            shape.param[VocalTract::TCX] -= 0.45;
            shape.param[VocalTract::TBX] -= 0.25;
        }
        if (recipe.name == "derived-lot") {
            // Keep the open jaw from /a/ while borrowing the rounded lips of /O/.
            shape.param[VocalTract::JA] = tract->shapes[first].param[VocalTract::JA];
            shape.param[VocalTract::LP] = tract->shapes[second].param[VocalTract::LP];
            shape.param[VocalTract::LD] = tract->shapes[second].param[VocalTract::LD];
        }
        provenance << "  \"" << recipe.name << "\": {\"sources\": [\"" << recipe.first << "\", \""
            << recipe.second << "\"], \"second_weight\": " << recipe.secondWeight << ", \"parameters\": {";
        for (int p = 0; p < VocalTract::NUM_PARAMS; ++p) {
            if (shape.param[p] < tract->param[p].min || shape.param[p] > tract->param[p].max) return 4;
            provenance << (p ? ", " : "") << "\"" << tract->param[p].name << "\": " << shape.param[p];
        }
        provenance << "}}" << (r + 1 < recipes.size() ? "," : "") << "\n";
        tract->shapes.push_back(shape);
    }
    provenance << "}\n";
    if (!provenance) return 3;
    const std::vector<std::pair<std::string, std::string>> presets = {
        {"i", "fleece"}, {"I", "kit"}, {"E", "dress"}, {"U", "foot"}, {"u", "goose"}, {"O", "thought"},
        {"derived-trap", "trap"}, {"derived-palm", "palm"}, {"derived-strut", "strut"}, {"derived-lot", "lot"},
        {"ll-labial-closure(a)", "bilabial-closure"},
        {"tt-alveolar-closure(a)", "alveolar-closure"},
        {"tb-velar-closure(a)", "velar-closure"},
        {"ll-dental-fricative(a)", "labiodental-fricative"},
        {"tt-dental-fricative(a)", "dental-fricative"},
        {"tt-alveolar-fricative(a)", "alveolar-fricative"},
        {"tt-postalveolar-fricative(a)", "postalveolar-fricative"},
        {"tt-postalveolar-closure(a)", "postalveolar-closure"},
        {"tt-alveolar-lateral(a)", "alveolar-lateral"}
    };
    std::ofstream gestures(std::string(argv[2]) + "/consonant-poses.json");
    gestures << std::setprecision(10) << "{\n";
    bool firstGesture = true;
    for (const auto& [name, asset] : presets) {
        if (consonantsOnly && name.find("(a)") == std::string::npos) continue;
        int shape = tract->getShapeIndex(name);
        if (shape < 0) { std::cerr << "Missing shape: " << name; return 2; }
        if (name.find("(a)") != std::string::npos) {
            gestures << (firstGesture ? "" : ",\n") << "  \"" << asset << "\": {\"source_preset\": \"" << name
                << "\", \"context\": \"a\", \"parameters\": {";
            for (int p = 0; p < VocalTract::NUM_PARAMS; ++p) {
                const double value = tract->shapes[shape].param[p];
                if (value < tract->param[p].min || value > tract->param[p].max) return 4;
                gestures << (p ? ", " : "") << "\"" << tract->param[p].name << "\": " << value;
            }
            gestures << "}}";
            firstGesture = false;
        }
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
    gestures << "\n}\n";
    if (!gestures) return 3;
}
