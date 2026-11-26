import { Injectable, computed, signal } from '@angular/core';

/**
 * Configuration du studio lui-même (adresse du backend), lue au démarrage dans
 * `config.json` placé à côté de l'application. Ce fichier reste modifiable après
 * le build : changer d'environnement ne nécessite ni rebuild ni redéploiement du code.
 *
 * L'écran de connexion permet aussi de surcharger ces deux valeurs et de les
 * enregistrer dans le navigateur, quand on n'a pas accès au fichier déployé.
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

const BACKEND_KEY = 'apigen.backendUrl';
const CONTEXT_KEY = 'apigen.contextPath';

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

  /** Valeurs affichées dans les champs de l'écran de connexion. */
  readonly snapshot = computed(() => this.settings());

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
    let fromFile: FrontendConfig = { backendUrl: '', contextPath: '' };
    try {
      const url = new URL('config.json', document.baseURI).toString();
      const response = await fetch(url, { cache: 'no-store' });
      if (response.ok) {
        const raw = await response.json() as Partial<FrontendConfig>;
        fromFile = {
          backendUrl: typeof raw.backendUrl === 'string' ? raw.backendUrl : '',
          contextPath: typeof raw.contextPath === 'string' ? raw.contextPath : ''
        };
      }
    } catch {
      // Fichier absent ou illisible : on garde le comportement « même origine ».
    }
    this.settings.set(this.mergeWithOverride(fromFile));
  }

  /** Enregistre l'adresse saisie dans l'écran de connexion pour ce navigateur. */
  applyOverride(values: FrontendConfig): void {
    localStorage.setItem(BACKEND_KEY, values.backendUrl ?? '');
    localStorage.setItem(CONTEXT_KEY, values.contextPath ?? '');
    this.settings.set({ backendUrl: values.backendUrl ?? '', contextPath: values.contextPath ?? '' });
  }

  /** Revient aux valeurs du fichier config.json. */
  resetOverride(): void {
    localStorage.removeItem(BACKEND_KEY);
    localStorage.removeItem(CONTEXT_KEY);
    void this.load();
  }

  private mergeWithOverride(base: FrontendConfig): FrontendConfig {
    const backendOverride = localStorage.getItem(BACKEND_KEY);
    const contextOverride = localStorage.getItem(CONTEXT_KEY);
    return {
      backendUrl: backendOverride === null ? base.backendUrl : backendOverride,
      contextPath: contextOverride === null ? base.contextPath : contextOverride
    };
  }
}
