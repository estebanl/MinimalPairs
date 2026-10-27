// Illustrative British-English teaching targets, pending specialist review.
// These are authored independently of the source viewer's A/I/U/Curl presets.
export const poses = Object.freeze({
  sheep: Object.freeze({height: .72, advance: .78, tip: 0, jaw: .12, round: 0}),
  ship: Object.freeze({height: .42, advance: .48, tip: 0, jaw: .24, round: 0})
});
export function parseState(value) {
  if (!value || value.version !== 1 || value.type !== 'state' ||
      !Object.hasOwn(poses, value.pose) || !['front', 'side'].includes(value.view) ||
      !Number.isFinite(value.opacity) || value.opacity < 0 || value.opacity > 1 ||
      typeof value.active !== 'boolean' || typeof value.reducedMotion !== 'boolean') return null;
  return {...value};
}
