// Only in-memory embedded image blobs may be fetched. No HTTP(S), files, or servers.
export const contentSecurityPolicy = "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data: blob:; connect-src blob:; object-src 'none'; base-uri 'none'; form-action 'none'";
