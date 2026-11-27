import { DOCUMENT } from '@angular/common';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Organization } from './models';

export interface StudioTheme {
  id: string; label: string; primary: string; strong: string; soft: string;
  sidebar: string; accent: string; source: string;
}
const neutral: StudioTheme = { id: 'neutral', label: 'API-GEN Studio', primary: '#334e68', strong: '#243b53', soft: '#e8eef4', sidebar: '#142536', accent: '#805b14', source: '' };

/** Visual context only. Does not select a tenant, grant rights or filter API data. */
@Injectable({ providedIn: 'root' })
export class BrandingService {
  private document = inject(DOCUMENT);
  readonly themes = signal<StudioTheme[]>([neutral]);
  readonly organization = signal<Organization | null>(null);
  readonly current = computed(() => {
    const org = this.organization();
    let id = 'neutral';
    if (org) {
      try { id = JSON.parse(org.contextConfig || '{}').variables?.studioTheme || 'neutral'; } catch { /* neutral fallback */ }
    }
    return this.themes().find(theme => theme.id === id) || neutral;
  });

  async load(): Promise<void> {
    try {
      const response = await fetch(new URL('branding.json', this.document.baseURI), { cache: 'no-store' });
      if (!response.ok) return;
      const value: unknown = await response.json();
      if (!Array.isArray(value)) return;
      const color = /^#[0-9a-fA-F]{6}$/;
      const themes = value.filter((item: any): item is StudioTheme => item &&
        typeof item.id === 'string' && /^[a-z][a-z0-9-]{0,30}$/.test(item.id) && item.id !== 'neutral' &&
        typeof item.label === 'string' && item.label.length <= 120 &&
        [item.primary, item.strong, item.soft, item.sidebar, item.accent].every(c => typeof c === 'string' && color.test(c)) &&
        typeof item.source === 'string' && /^https:\/\//.test(item.source));
      this.themes.set([neutral, ...themes]);
      this.apply();
    } catch { /* A branding file must never prevent access to the Studio. */ }
  }
  select(org: Organization | null): void { this.organization.set(org); this.apply(); }
  private apply(): void {
    const theme = this.current();
    const root = this.document.documentElement;
    root.dataset['studioTheme'] = theme.id;
    for (const [key, value] of Object.entries({ brand: theme.primary, 'brand-strong': theme.strong,
      'brand-soft': theme.soft, 'sidebar-bg': theme.sidebar, accent: theme.accent })) {
      root.style.setProperty('--' + key, value);
    }
  }
}
