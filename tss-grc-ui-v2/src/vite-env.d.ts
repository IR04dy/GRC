/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE: string | undefined;
  readonly VITE_MODE: string | undefined;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
