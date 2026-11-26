import { Injectable, computed, inject, signal } from '@angular/core';

/**
 * Configuration du studio lui-même (adresse du backend), lue au démarrage dans
 * `config.json` placé à côté de l'application. Ce fichier reste modifiable après
 * le build : changer d'environnement ne nécessite ni rebuild ni redéploiement du code.
 *
 * - `backendUrl` vide : le studio parle à son propre serveur (proxy Angular en dev,
 *   nginx en production). C'est le réglage par défaut.
 * - `backendUrl` renseigné : URL complète du backend, par exemple
 *   `http://serveur-backend:8081`.
 * - `contextPath` : préfixe global du backend, par exemple `/apigen`. Inutile si
 *   `backendUrl` contient déjà ce préfixe.
 */
export interface FrontendConfig {
  backendUrl: string;
  contextPath: string;
}

function normalizeContext(value: string): string {
  const trimmed = (value ?? '').trim();
  if (!trimmed || trimmed === '/') {
    return '';
  }
  const withSlash = trimmed.startsWith('/') ? trimmed : '/' + trimmed;
  return withSlash.replace(/\/+$/, '');
}

@Injectable({ providedIn: 'root' })
export class FrontendConfigService {
  private readonly settings = signal<FrontendConfig>({ backendUrl: '', contextPath: '' });

  /** Préfixe à ajouter devant `/api/...` ; vide signifie « même origine ». */
  readonly apiBaseUrl = computed(() => {
    const { backendUrl, contextPath } = this.settings();
    const backend = (backendUrl ?? '').trim().replace(/\/+$/, '');
    if (!backend) {
      return '';
    }
    const context = normalizeContext(contextPath);
    if (context && !backend.endsWith(context)) {
      return backend + context;
    }
    return backend;
  });

  async load(): Promise<void> {
    try {
      const url = new URL('config.json', document.baseURI).toString();
      const response = await fetch(url, { cache: 'no-store' });
      if (response.ok) {
        const raw = await response.json() as Partial<FrontendConfig>;
        this.settings.set({
          backendUrl: typeof raw.backendUrl === 'string' ? raw.backendUrl : '',
          contextPath: typeof raw.contextPath === 'string' ? raw.contextPath : ''
        });
      }
    } catch {
      // Fichier absent ou illisible : on garde le comportement « même origine ».
    }
  }
}
