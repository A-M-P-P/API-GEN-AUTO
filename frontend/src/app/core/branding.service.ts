import { DOCUMENT } from '@angular/common';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Organization } from './models';

export interface StudioTheme {
  id: string; label: string; primary: string; strong: string; soft: string;
  sidebar: string; accent: string; source: string; logo?: string;
}
const neutral: StudioTheme = { id: 'neutral', label: 'API-GEN Studio', primary: '#334e68', strong: '#243b53', soft: '#e8eef4', sidebar: '#142536', accent: '#805b14', source: '' };

/** Visual context only. Does not select a tenant, grant rights or filter API data. */
@Injectable({ providedIn: 'root' })
export class BrandingService {
  private document = inject(DOCUMENT);
  readonly themes = signal<StudioTheme[]>([neutral]);
  readonly loadError = signal('');
  readonly loading = signal(false);
  readonly organization = signal<Organization | null>(null);
  private readonly selectedPlatform = signal<string | null>(null);
  readonly current = computed(() => {
    const id = this.selectedPlatform();
    return id ? this.themes().find(theme => theme.id === id) || neutral : this.themeFor(this.organization());
  });

  themeFor(org: Organization | null): StudioTheme {
    if (!org) return neutral;
    let configured: string | undefined;
    try { configured = JSON.parse(org.contextConfig || '{}').variables?.studioTheme; } catch { /* infer below */ }
    const identity = (org.code + ' ' + org.name).normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
    const inferred = ['cnef', 'dgi', 'src', 'sigif'].find(id => new RegExp('(^|[^a-z])' + id + '(?:2)?([^a-z]|$)').test(identity));
    const id = configured && configured !== 'auto' ? configured : inferred;
    return this.themes().find(theme => theme.id === id) || neutral;
  }

  selectPlatform(id: string, organizations: Organization[]): void {
    this.organization.set(organizations.find(org => this.themeFor(org).id === id || org.code.toLowerCase() === id) || null);
    this.selectedPlatform.set(id);
    this.apply();
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.loadError.set('');
    try {
      const response = await fetch(new URL('branding.json', this.document.baseURI), { cache: 'no-store' });
      if (!response.ok) throw new Error('Branding unavailable');
      const value: unknown = await response.json();
      if (!Array.isArray(value)) throw new Error('Invalid branding document');
      const color = /^#[0-9a-fA-F]{6}$/;
      const themes = value.filter((item: any): item is StudioTheme => item &&
        typeof item.id === 'string' && /^[a-z][a-z0-9-]{0,30}$/.test(item.id) && item.id !== 'neutral' &&
        typeof item.label === 'string' && item.label.length <= 120 &&
        [item.primary, item.strong, item.soft, item.sidebar, item.accent].every(c => typeof c === 'string' && color.test(c)) &&
        typeof item.source === 'string' && /^https:\/\//.test(item.source));
      if (themes.length === 0) throw new Error('No usable themes');
      for (const theme of themes) {
        if (theme.logo && !(/^(branding\/[a-z0-9-]+\.(png|webp|jpg)|https:\/\/[^\s]+)$/i.test(theme.logo))) delete theme.logo;
      }
      this.themes.set([neutral, ...themes]);
      this.apply();
    } catch {
      this.loadError.set('Les thèmes ne peuvent pas être chargés. Vérifiez que branding.json et le dossier branding sont déployés à côté de index.html, puis réessayez.');
    } finally {
      this.loading.set(false);
    }
  }
  select(org: Organization | null): void { this.selectedPlatform.set(null); this.organization.set(org); this.apply(); }
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
