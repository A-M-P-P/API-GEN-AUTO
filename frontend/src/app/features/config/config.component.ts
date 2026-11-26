import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ConfigService } from '../../core/config.service';
import { FrontendConfigService } from '../../core/frontend-config.service';
import { ToastService } from '../../core/toast.service';
import { ConfigEntry, ConfigView } from '../../core/models';

interface ConfigGroup {
  title: string;
  icon: string;
  entries: ConfigEntry[];
}

@Component({
  selector: 'app-config',
  standalone: true,
  imports: [],
  templateUrl: './config.component.html',
  styleUrl: './config.component.scss'
})
export class ConfigComponent implements OnInit {
  private service = inject(ConfigService);
  private toast = inject(ToastService);
  readonly frontendConfig = inject(FrontendConfigService);

  readonly view = signal<ConfigView | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  /** Valeurs saisies, indexées par clé de propriété. */
  readonly values = signal<Record<string, string>>({});
  readonly changedKeys = signal<string[]>([]);
  readonly dirty = computed(() => this.changedKeys().length > 0);

  readonly groups = computed<ConfigGroup[]>(() => {
    const entries = this.view()?.entries ?? [];
    const build = (predicate: (entry: ConfigEntry) => boolean) => entries.filter(predicate);
    return [
      {
        title: 'Base de données',
        icon: '⛁',
        entries: build((entry) => entry.key.startsWith('spring.datasource.'))
      },
      {
        title: 'Serveur et exposition',
        icon: '⇄',
        entries: build((entry) => entry.key.startsWith('server.'))
      },
      {
        title: 'Sécurité',
        icon: '◉',
        entries: build((entry) =>
          entry.key.startsWith('apigen.jwt') || entry.key.startsWith('apigen.crypto'))
      },
      {
        title: 'Fonctionnement',
        icon: '⚙',
        entries: build((entry) =>
          entry.key.startsWith('apigen.cors') ||
          entry.key.startsWith('apigen.scheduler') ||
          entry.key.startsWith('apigen.audit') ||
          entry.key.startsWith('apigen.files') ||
          entry.key.startsWith('apigen.seed'))
      }
    ].filter((group) => group.entries.length > 0);
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.view().subscribe({
      next: (view) => {
        this.view.set(view);
        const values: Record<string, string> = {};
        for (const entry of view.entries) {
          values[entry.key] = entry.value;
        }
        this.values.set(values);
        this.changedKeys.set([]);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Impossible de lire la configuration. Accès réservé aux administrateurs.');
      }
    });
  }

  valueOf(entry: ConfigEntry): string {
    return this.values()[entry.key] ?? '';
  }

  onInput(entry: ConfigEntry, raw: string): void {
    const value = raw ?? '';
    this.values.update((map) => ({ ...map, [entry.key]: value }));
    const changed = value !== entry.value && !(entry.masked && value === entry.value);
    this.changedKeys.update((list) =>
      changed
        ? Array.from(new Set([...list, entry.key]))
        : list.filter((key) => key !== entry.key));
  }

  save(): void {
    const payload: Record<string, string> = {};
    for (const key of this.changedKeys()) {
      payload[key] = (this.values()[key] ?? '').trim();
    }
    if (Object.keys(payload).length === 0) {
      this.toast.info('Aucune modification à enregistrer.');
      return;
    }
    this.saving.set(true);
    this.service.update(payload).subscribe({
      next: (result) => {
        this.saving.set(false);
        if (result.changed.length === 0) {
          this.toast.info('Aucune valeur différente : rien à écrire.');
        } else {
          this.toast.success(
            result.changed.length + ' propriété(s) enregistrée(s) — redémarrez le serveur pour les appliquer.'
          );
        }
        this.load();
      },
      error: () => {
        this.saving.set(false);
        this.toast.error('Écriture impossible : vérifiez les droits sur le fichier externe.');
      }
    });
  }

  discard(): void {
    this.load();
  }

  isSecretSet(entry: ConfigEntry): boolean {
    return !!entry.value && entry.value.length > 0;
  }

  sourceBadge(source: string): string {
    if (source === 'Fichier externe') {
      return 'badge-success';
    }
    if (source === 'Variable d\'environnement' || source === 'Ligne de commande') {
      return 'badge-info';
    }
    return 'badge-muted';
  }
}
