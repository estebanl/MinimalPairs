# Articulatory Facial Landmark Tracking Specification

## Overview
Defines the computer vision contract for real-time facial landmark analysis measuring physical lip aperture, rounding, and jaw drop during vowel production.

## Key Landmark Indices (MediaPipe Face Mesh / 468 points)
- **Upper Lip (Inner/Center)**: Landmark `13`
- **Lower Lip (Inner/Center)**: Landmark `14`
- **Lip Left Corner**: Landmark `61`
- **Lip Right Corner**: Landmark `291`
- **Subnasale (Base of nose)**: Landmark `2`
- **Menton (Chin Tip)**: Landmark `152`
- **Inter-pupillary distance reference**: Left Pupil `468` / `473`, Right Pupil `473` / `468`

## Derived Biometric Metrics
1. **Lip Aperture Ratio**:
   $$\text{Aperture Ratio} = \frac{\lVert P_{13} - P_{14} \rVert}{\lVert P_{61} - P_{291} \rVert}$$
   - Spread unrounded vowels (e.g. `/iː/`): $0.10 \sim 0.18$
   - Open unrounded vowels (e.g. `/æ/`): $0.30 \sim 0.45$
   - Rounded vowels (e.g. `/uː/`): $0.10 \sim 0.15$ with high rounding index

2. **Jaw Drop (mm)**:
   $$\text{Jaw Drop} = \lVert P_{2} - P_{152} \rVert \times \text{Scale factor (calibrated against inter-ocular distance)}$$
   - Close vowels (`/iː/`, `/uː/`): $5 \sim 12\text{ mm}$
   - Open vowels (`/æ/`, `/ɑː/`): $14 \sim 24\text{ mm}$

3. **Lip Rounding Index**:
   $$\text{Rounding Index} = \frac{4 \pi \times \text{Area}}{\text{Perimeter}^2}$$

## Performance Budget
- Inference time $\le 33\text{ms}$ per frame (30 FPS tracking).
- Jitter smoothing via 1-Euro filter ($\beta = 0.05, f_c = 1.0\text{ Hz}$).
