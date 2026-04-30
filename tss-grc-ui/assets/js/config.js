/**
 * Runtime configuration for TSS GRC UI.
 *
 * Standalone dev mode: UI on :8086, backend on :8084.
 * Production: both on the same origin — remove or blank GRC_BACKEND_URL.
 */
window.GRC_BACKEND_URL = 'http://localhost:8084';
