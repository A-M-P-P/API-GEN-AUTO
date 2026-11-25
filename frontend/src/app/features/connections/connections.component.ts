import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ConnectionService } from '../../core/connection.service';
import { ToastService } from '../../core/toast.service';
import { Connection, ConnectionType, DiscoveryQuery } from '../../core/models';
import { SchemaBrowserComponent } from '../../shared/schema-browser.component';

interface DbDefaults {
  port: number;
  databaseLabel: string;
  databasePlaceholder: string;
}

interface TestOutcome {
  ok: boolean;
  message: string;
  latencyMs: number;
}

interface TableRef {
  schema: string;
  table: string;
}

@Component({
  selector: 'app-connections',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe, SchemaBrowserComponent],
  templateUrl: './connections.component.html',
  styleUrl: './connections.component.scss'
})
export class ConnectionsComponent implements OnInit {
  private service = inject(ConnectionService);
  private fb = inject(FormBuilder);
  private toast = inject(ToastService);

  readonly connections = signal<Connection[]>([]);
  readonly loading = signal(true);
  readonly editorOpen = signal(false);
  readonly editing = signal<Connection | null>(null);
  readonly saving = signal(false);
  readonly testing = signal(false);
  readonly testResult = signal<TestOutcome | null>(null);
  readonly lastTests = signal<Record<number, TestOutcome>>({});
  readonly discoveryOpen = signal(false);
  readonly discoveryQuery = signal<DiscoveryQuery | null>(null);
  readonly selectedTable = signal<TableRef | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    type: ['REST' as ConnectionType, Validators.required],
    description: [''],
    // Base de données
    host: [''],
    port: [0],
    database: [''],
    addressing: ['SERVICE'],
    username: [''],
    password: [''],
    maxPoolSize: [5, [Validators.min(1), Validators.max(20)]],
    driverProperties: [''],
    // REST
    baseUrl: [''],
    authType: ['NONE'],
    token: [''],
    apiKey: [''],
    apiKeyHeader: ['X-API-Key'],
    // Fichiers
    basePath: [''],
    active: [true]
  });

  readonly dbDefaults: Record<string, DbDefaults> = {
    ORACLE: { port: 1521, databaseLabel: 'Service', databasePlaceholder: 'ex. XEPDB1 ou ORCLPDB1' },
    POSTGRESQL: { port: 5432, databaseLabel: 'Base de données', databasePlaceholder: 'ex. inventaire' },
    MYSQL: { port: 3306, databaseLabel: 'Base de données', databasePlaceholder: 'ex. rh' },
    SQLSERVER: { port: 1433, databaseLabel: 'Base de données', databasePlaceholder: 'ex. ventes' }
  };

  readonly dbTypes: ConnectionType[] = ['ORACLE', 'POSTGRESQL', 'MYSQL', 'SQLSERVER'];

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.service.list().subscribe({
      next: (list) => {
        this.connections.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.toast.error('Impossible de charger les connexions.');
      }
    });
  }

  openNew(): void {
    this.editing.set(null);
    this.testResult.set(null);
    this.discoveryOpen.set(false);
    this.discoveryQuery.set(null);
    this.selectedTable.set(null);
    this.form.reset({
      name: '', type: 'REST', description: '', host: '', port: 0, database: '',
      addressing: 'SERVICE', username: '', password: '', maxPoolSize: 5, driverProperties: '',
      baseUrl: '', authType: 'NONE', token: '', apiKey: '', apiKeyHeader: 'X-API-Key',
      basePath: '', active: true
    });
    this.applyTypeDefaults('REST');
    this.editorOpen.set(true);
  }

  openEdit(connection: Connection): void {
    this.editing.set(connection);
    this.testResult.set(null);
    this.discoveryOpen.set(false);
    this.discoveryQuery.set(null);
    this.selectedTable.set(null);
    const cfg: any = connection.baseConfig ?? {};
    // Les champs décomposés sont enregistrés avec la connexion ; on ne re-parse l'URL
    // que pour les connexions créées directement via l'API sans ces champs.
    const stored = {
      host: String(cfg.host ?? ''),
      port: Number(cfg.port ?? 0),
      database: String(cfg.database ?? ''),
      addressing: String(cfg.addressing ?? 'SERVICE')
    };
    const parsed = stored.host && stored.port && stored.database
      ? stored
      : this.parseJdbcUrl(String(cfg.jdbcUrl ?? ''), connection.type);

    this.form.reset({
      name: connection.name,
      type: connection.type,
      description: connection.description ?? '',
      host: parsed.host,
      port: parsed.port,
      database: parsed.database,
      addressing: parsed.addressing,
      username: '',
      password: '',
      maxPoolSize: Number(cfg.maxPoolSize ?? 5),
      baseUrl: String(cfg.baseUrl ?? ''),
      authType: String(cfg.authType ?? 'NONE'),
      token: '',
      apiKey: '',
      apiKeyHeader: String(cfg.apiKeyHeader ?? 'X-API-Key'),
      basePath: String(cfg.basePath ?? ''),
      driverProperties: cfg.driverProperties && typeof cfg.driverProperties === 'object'
        ? JSON.stringify(cfg.driverProperties, null, 2)
        : '',
      active: connection.active
    });
    this.editorOpen.set(true);
  }

  closeEditor(): void {
    this.editorOpen.set(false);
    this.editing.set(null);
    this.testResult.set(null);
    this.discoveryOpen.set(false);
    this.discoveryQuery.set(null);
    this.selectedTable.set(null);
  }

  /** Ouvre ou referme l'explorateur de schémas de la base courant. */
  toggleDiscovery(): void {
    if (this.discoveryOpen()) {
      this.discoveryOpen.set(false);
      return;
    }
    const current = this.editing();
    if (current) {
      // Connexion enregistrée : on s'appuie sur les identifiants chiffrés en base,
      // ce qui fonctionne même si le mot de passe n'a pas été ressaisi.
      this.discoveryQuery.set({ connectionId: current.id });
    } else {
      const { baseConfig, credentials } = this.buildPayload();
      this.discoveryQuery.set({ type: this.form.getRawValue().type, baseConfig, credentials });
    }
    this.selectedTable.set(null);
    this.discoveryOpen.set(true);
  }

  onTableChosen(ref: TableRef): void {
    this.selectedTable.set(ref);
  }

  qualifiedTable(): string {
    const ref = this.selectedTable();
    return ref ? `${ref.schema}.${ref.table}` : '';
  }

  copyTable(): void {
    const value = this.qualifiedTable();
    if (!value) {
      return;
    }
    navigator.clipboard?.writeText(value).then(
      () => this.toast.success('Nom de table copié : ' + value),
      () => this.toast.error('Copie impossible.')
    );
  }

  /** Appelé au changement de type : remplit les valeurs par défaut sans écraser le nom. */
  onTypeChange(): void {
    const type = this.form.getRawValue().type;
    this.applyTypeDefaults(type);
    this.testResult.set(null);
    this.discoveryOpen.set(false);
    this.discoveryQuery.set(null);
    this.selectedTable.set(null);
  }

  private applyTypeDefaults(type: ConnectionType): void {
    if (this.isDb(type)) {
      const defaults = this.dbDefaults[type];
      this.form.patchValue({ port: defaults.port, database: '', host: '', addressing: 'SERVICE' });
    } else if (type === 'FILE') {
      this.form.patchValue({ basePath: './data/exchanges' });
    } else if (type === 'REST') {
      this.form.patchValue({ baseUrl: '', authType: 'NONE', apiKeyHeader: 'X-API-Key' });
    }
  }

  isDb(type: ConnectionType): boolean {
    return this.dbTypes.includes(type);
  }

  isRest(type: ConnectionType): boolean {
    return type === 'REST';
  }

  isFile(type: ConnectionType): boolean {
    return type === 'FILE';
  }

  databaseLabel(): string {
    return this.dbDefaults[this.form.getRawValue().type]?.databaseLabel ?? 'Base de données';
  }

  databasePlaceholder(): string {
    return this.dbDefaults[this.form.getRawValue().type]?.databasePlaceholder ?? '';
  }

  /** Construit l'URL JDBC à partir des champs structurés. */
  buildJdbcUrl(): string {
    const raw = this.form.getRawValue();
    const host = raw.host.trim();
    const port = raw.port;
    const database = raw.database.trim();
    if (!host || !port || !database) {
      return '';
    }
    switch (raw.type) {
      case 'ORACLE':
        return raw.addressing === 'SID'
          ? `jdbc:oracle:thin:@${host}:${port}:${database}`
          : `jdbc:oracle:thin:@//${host}:${port}/${database}`;
      case 'POSTGRESQL':
        return `jdbc:postgresql://${host}:${port}/${database}`;
      case 'MYSQL':
        return `jdbc:mysql://${host}:${port}/${database}`;
      case 'SQLSERVER':
        return `jdbc:sqlserver://${host}:${port};databaseName=${database};encrypt=true`;
      default:
        return '';
    }
  }

  /** Champs obligatoires manquants, affichés sous forme de message lisible. */
  private missingFields(): string[] {
    const raw = this.form.getRawValue();
    const missing: string[] = [];
    if (!raw.name.trim()) {
      missing.push('le nom');
    }
    if (this.isDb(raw.type)) {
      if (!raw.host.trim()) missing.push("l'hôte");
      if (!raw.port) missing.push('le port');
      if (!raw.database.trim()) missing.push(this.databaseLabel().toLowerCase());
    } else if (raw.type === 'REST') {
      if (!raw.baseUrl.trim()) missing.push("l'URL de base");
    } else if (raw.type === 'FILE') {
      if (!raw.basePath.trim()) missing.push('le répertoire');
    }
    return missing;
  }

  /** Construit les objets envoyés au backend à partir des champs structurés. */
  private buildPayload(): { baseConfig: any; credentials?: any } {
    const raw = this.form.getRawValue();
    if (this.isDb(raw.type)) {
      const baseConfig: any = {
        jdbcUrl: this.buildJdbcUrl(),
        host: raw.host.trim(),
        port: raw.port,
        database: raw.database.trim(),
        maxPoolSize: raw.maxPoolSize
      };
      if (raw.type === 'ORACLE') {
        baseConfig['addressing'] = raw.addressing;
      }
      const driverProperties = this.parseDriverProperties();
      if (driverProperties && Object.keys(driverProperties).length) {
        baseConfig['driverProperties'] = driverProperties;
      }
      const credentials: any = {};
      if (raw.username.trim()) credentials['username'] = raw.username.trim();
      if (raw.password) credentials['password'] = raw.password;
      return { baseConfig, credentials: Object.keys(credentials).length ? credentials : undefined };
    }
    if (raw.type === 'REST') {
      const baseConfig: any = { baseUrl: raw.baseUrl.trim(), authType: raw.authType };
      const credentials: any = {};
      if (raw.authType === 'BASIC') {
        credentials['username'] = raw.username.trim();
        credentials['password'] = raw.password;
      } else if (raw.authType === 'BEARER') {
        credentials['token'] = raw.token;
      } else if (raw.authType === 'API_KEY') {
        baseConfig['apiKeyHeader'] = raw.apiKeyHeader || 'X-API-Key';
        credentials['apiKey'] = raw.apiKey;
      }
      return { baseConfig, credentials: Object.keys(credentials).length ? credentials : undefined };
    }
    return { baseConfig: { basePath: raw.basePath.trim() } };
  }

  /** Propriétés du pilote JDBC saisies en JSON (objet plat clé/valeur). */
  parseDriverProperties(): Record<string, string> | null {
    const raw = (this.form.getRawValue().driverProperties ?? '').trim();
    if (!raw) {
      return {};
    }
    try {
      const parsed = JSON.parse(raw);
      if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
        return null;
      }
      const out: Record<string, string> = {};
      for (const [key, value] of Object.entries(parsed)) {
        if (value === null || value === undefined) {
          continue;
        }
        out[key] = String(value);
      }
      return out;
    } catch {
      return null;
    }
  }

  /** Aperçu JSON de ce qui sera enregistré. */
  previewJson(): string {
    const raw = this.form.getRawValue();
    if (this.isDb(raw.type) && !this.buildJdbcUrl()) {
      return JSON.stringify({ baseConfig: {}, credentials: { chiffrés: true } }, null, 2);
    }
    const payload = this.buildPayload();
    return JSON.stringify({ baseConfig: payload.baseConfig, credentials: 'chiffrés (AES-256-GCM)' }, null, 2);
  }

  save(): void {
    const missing = this.missingFields();
    if (missing.length) {
      this.toast.error('Champs obligatoires : ' + missing.join(', ') + '.');
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    if (this.isDb(raw.type) && this.parseDriverProperties() === null) {
      this.toast.error('Les propriétés du pilote JDBC doivent être un objet JSON valide.');
      return;
    }
    const { baseConfig, credentials } = this.buildPayload();
    const payload = {
      name: raw.name.trim(),
      type: raw.type,
      description: raw.description,
      baseConfig,
      // En édition, on ne renvoie les identifiants que s'ils ont été ressaisis.
      credentials: this.editing() && !this.hasCredentialInput() ? undefined : credentials,
      active: raw.active
    };

    this.saving.set(true);
    const current = this.editing();
    const operation = current ? this.service.update(current.id, payload) : this.service.create(payload);
    operation.subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success(current ? 'Connexion mise à jour.' : 'Connexion créée.');
        this.closeEditor();
        this.load();
      },
      error: () => {
        this.saving.set(false);
        this.toast.error('Enregistrement impossible.');
      }
    });
  }

  private hasCredentialInput(): boolean {
    const raw = this.form.getRawValue();
    return !!(raw.username.trim() || raw.password || raw.token || raw.apiKey);
  }

  testForm(): void {
    const missing = this.missingFields();
    if (missing.length) {
      this.toast.error('Champs obligatoires : ' + missing.join(', ') + '.');
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    if (this.isDb(raw.type) && this.parseDriverProperties() === null) {
      this.toast.error('Les propriétés du pilote JDBC doivent être un objet JSON valide.');
      return;
    }
    const { baseConfig, credentials } = this.buildPayload();
    this.testing.set(true);
    this.testResult.set(null);
    this.service.test({ type: raw.type, baseConfig, credentials }).subscribe({
      next: (result) => {
        this.testing.set(false);
        this.testResult.set(result);
        if (!result.ok) this.toast.error(result.message);
      },
      error: () => {
        this.testing.set(false);
        this.toast.error('Test de connexion impossible.');
      }
    });
  }

  testExisting(connection: Connection): void {
    this.testing.set(true);
    this.service.testExisting(connection.id).subscribe({
      next: (result) => {
        this.testing.set(false);
        this.lastTests.update((map) => ({ ...map, [connection.id]: result }));
        result.ok ? this.toast.success(result.message) : this.toast.error(result.message);
        this.load();
      },
      error: () => {
        this.testing.set(false);
        this.toast.error('Test impossible.');
      }
    });
  }

  toggleActive(connection: Connection): void {
    this.service.update(connection.id, {
      name: connection.name,
      type: connection.type,
      description: connection.description,
      baseConfig: connection.baseConfig,
      active: !connection.active
    }).subscribe({ next: () => this.load(), error: () => this.toast.error('Mise à jour impossible.') });
  }

  remove(connection: Connection): void {
    if (!confirm(`Supprimer la connexion « ${connection.name} » ?`)) return;
    this.service.delete(connection.id).subscribe({
      next: () => {
        this.toast.success('Connexion supprimée.');
        this.load();
      },
      error: () => this.toast.error('Suppression impossible.')
    });
  }

  /** Extrait hôte / port / base d'une URL JDBC existante pour pré-remplir le formulaire. */
  private parseJdbcUrl(url: string, type: ConnectionType): { host: string; port: number; database: string; addressing: string } {
    const empty = { host: '', port: 0, database: '', addressing: 'SERVICE' };
    if (!url) return empty;
    try {
      if (type === 'ORACLE') {
        const sid = url.match(/^jdbc:oracle:thin:@([^:/]+):(\d+):([^/]+)$/);
        if (sid) {
          return { host: sid[1], port: Number(sid[2]), database: sid[3], addressing: 'SID' };
        }
        const service = url.match(/^jdbc:oracle:thin:@\/\/([^:/]+):(\d+)\/([^/?]+)/);
        if (service) {
          return { host: service[1], port: Number(service[2]), database: service[3], addressing: 'SERVICE' };
        }
        return empty;
      }
      if (type === 'SQLSERVER') {
        const match = url.match(/^jdbc:sqlserver:\/\/([^:;]+):(\d+);.*?databaseName=([^;]+)/i);
        if (match) {
          return { host: match[1], port: Number(match[2]), database: match[3], addressing: 'SERVICE' };
        }
        const noPort = url.match(/^jdbc:sqlserver:\/\/([^:;]+);.*?databaseName=([^;]+)/i);
        if (noPort) {
          return { host: noPort[1], port: 1433, database: noPort[2], addressing: 'SERVICE' };
        }
        return empty;
      }
      const parsed = new URL(url.replace(/^jdbc:/, ''));
      return {
        host: parsed.hostname || '',
        port: Number(parsed.port || 0),
        database: parsed.pathname.replace(/^\//, ''),
        addressing: 'SERVICE'
      };
    } catch {
      return empty;
    }
  }

  latencyClass(latencyMs: number): string {
    if (latencyMs < 300) return 'lat-good';
    if (latencyMs < 1000) return 'lat-warn';
    return 'lat-bad';
  }

  poolSizeOf(connection: Connection): number {
    return Number(connection.baseConfig?.['maxPoolSize'] ?? 5);
  }

  typeLabel(type: ConnectionType): string {
    const labels: Record<ConnectionType, string> = {
      REST: 'REST / HTTP', ORACLE: 'Oracle', POSTGRESQL: 'PostgreSQL', MYSQL: 'MySQL',
      SQLSERVER: 'SQL Server', FILE: 'Fichiers', FTP: 'FTP', SFTP: 'SFTP'
    };
    return labels[type];
  }

  typeGlyph(type: ConnectionType): string {
    const glyphs: Record<ConnectionType, string> = {
      REST: '⇄', ORACLE: '⛁', POSTGRESQL: '⛁', MYSQL: '⛁', SQLSERVER: '⛁',
      FILE: '▤', FTP: '⇡', SFTP: '⇡'
    };
    return glyphs[type];
  }

  configSummary(connection: Connection): string {
    const config = connection.baseConfig ?? {};
    if (this.isDb(connection.type)) {
      const host = config['host'] || '—';
      const database = config['database'] || '—';
      return `${host} · ${database}`;
    }
    const value = config['baseUrl'] || config['jdbcUrl'] || config['basePath'] || '—';
    return String(value);
  }

  isImplemented(type: ConnectionType): boolean {
    return ['REST', 'ORACLE', 'POSTGRESQL', 'MYSQL', 'SQLSERVER', 'FILE'].includes(type);
  }
}
