import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { ConnectionService } from '../../core/connection.service';
import { OrganizationService } from '../../core/organization.service';
import { ToastService } from '../../core/toast.service';
import {
  ApiDetail,
  ApiPayload,
  Connection,
  Direction,
  EndpointDetail,
  EndpointPayload,
  EndpointSummary,
  HttpMethod,
  InboundAuth,
  Organization,
  Run,
  Schedule
} from '../../core/models';
import { SchemaBrowserComponent } from '../../shared/schema-browser.component';

@Component({
  selector: 'app-api-builder',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, DatePipe, SchemaBrowserComponent],
  templateUrl: './api-builder.component.html',
  styleUrl: './api-builder.component.scss'
})
export class ApiBuilderComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private fb = inject(FormBuilder);
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private connections = inject(ConnectionService);
  private organizationService = inject(OrganizationService);

  readonly apiData = signal<ApiDetail | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly savingEndpoint = signal(false);
  readonly deletingEndpoint = signal(false);
  readonly running = signal(false);

  readonly apiKey = signal<string | null>(null);

  /** Organisations disponibles pour le rattachement de l'API. */
  readonly organizations = signal<Organization[]>([]);
  readonly selectedEndpointId = signal<number | null>(null);
  readonly selectedEndpoint = computed(() => {
    const id = this.selectedEndpointId();
    return this.apiData()?.endpoints.find((endpoint) => endpoint.id === id) ?? null;
  });

  readonly pipelineError = signal<string | null>(null);
  readonly runResult = signal<Run | null>(null);
  readonly runPayload = signal('{}');

  /** Explorateur de base : connexions disponibles, connexion choisie, table sélectionnée. */
  readonly browserOpen = signal(false);
  readonly browserConnections = signal<Connection[]>([]);
  readonly browserConnectionId = signal<number | null>(null);
  readonly browserQuery = computed(() => {
    const id = this.browserConnectionId();
    return id ? { connectionId: id } : null;
  });
  readonly browserTable = signal<{ schema: string; table: string } | null>(null);
  readonly browserConnectionType = computed(() => {
    const id = this.browserConnectionId();
    return this.browserConnections().find((connection) => connection.id === id)?.type ?? 'POSTGRESQL';
  });

  private scheduleId: number | null = null;

  readonly apiForm = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^[a-z0-9][a-z0-9-]{1,79}$/)]],
    name: ['', Validators.required],
    description: [''],
    version: ['1.0'],
    status: ['DRAFT' as ApiPayload['status']],
    inboundAuth: ['NONE' as InboundAuth],
    organizationId: this.fb.control<number | null>(null)
  });

  readonly endpointForm = this.fb.nonNullable.group({
    name: ['', Validators.required],
    method: ['GET' as HttpMethod, Validators.required],
    path: ['/', Validators.required],
    direction: ['INBOUND' as Direction],
    description: [''],
    active: [true],
    pipeline: ['[]'],
    cron: [''],
    scheduleEnabled: [false],
    schedulePayload: ['{}']
  });

  ngOnInit(): void {
    this.organizationService.list().subscribe({
      next: (organizations) => this.organizations.set(organizations),
      error: () => this.organizations.set([])
    });

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadApi(Number(id));
    } else {
      this.loading.set(false);
      this.resetEndpointForm();
    }
  }

  get isCreateMode(): boolean {
    return !this.route.snapshot.paramMap.get('id');
  }

  /** Code de l'organisation qui préfixe l'URL de la passerelle. */
  gatewayOrgCode(): string {
    const organizationId = this.apiForm.getRawValue().organizationId;
    return this.organizations().find((organization) => organization.id === organizationId)?.code ?? 'default';
  }

  loadApi(id: number, selectId?: number | null): void {
    this.loading.set(true);
    this.api.get(id).subscribe({
      next: (data) => {
        this.apiData.set(data);
        this.apiForm.patchValue({
          code: data.code,
          name: data.name,
          description: data.description ?? '',
          version: data.version,
          status: data.status,
          inboundAuth: data.inboundAuth,
          organizationId: data.organizationId ?? null
        });
        this.loading.set(false);
        const target = selectId ?? data.endpoints[0]?.id ?? null;
        if (target) {
          this.selectEndpoint(data.endpoints.find((endpoint) => endpoint.id === target)!);
        } else {
          this.resetEndpointForm();
        }
      },
      error: () => {
        this.loading.set(false);
        this.toast.error('API introuvable.');
        this.router.navigate(['/apis']);
      }
    });
  }

  saveApi(): void {
    if (this.apiForm.invalid || this.saving()) {
      this.apiForm.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const payload: ApiPayload = this.apiForm.getRawValue();
    const current = this.apiData();
    const operation = current ? this.api.update(current.id, payload) : this.api.create(payload);

    operation.subscribe({
      next: (data) => {
        this.saving.set(false);
        this.apiData.set(data);
        this.toast.success(current ? 'API mise à jour.' : 'API créée.');
        if (!current) {
          this.router.navigate(['/apis', data.id]);
        }
      },
      error: () => {
        this.saving.set(false);
        this.toast.error('Enregistrement de l\u2019API impossible.');
      }
    });
  }

  generateKey(): void {
    const current = this.apiData();
    if (!current) return;
    this.api.generateKey(current.id).subscribe({
      next: (response) => {
        this.apiKey.set(response.apiKey);
        this.apiForm.patchValue({ inboundAuth: 'API_KEY' });
        this.toast.success('Nouvelle clé API générée. Copiez-la maintenant, elle ne sera plus affichée.');
        this.loadApi(current.id, this.selectedEndpointId());
      },
      error: () => this.toast.error('Génération de clé impossible.')
    });
  }

  revokeKey(): void {
    const current = this.apiData();
    if (!current || !confirm('Révoquer la clé API actuelle ?')) return;
    this.api.revokeKey(current.id).subscribe({
      next: () => {
        this.apiKey.set(null);
        this.apiForm.patchValue({ inboundAuth: 'NONE' });
        this.toast.success('Clé API révoquée.');
        this.loadApi(current.id, this.selectedEndpointId());
      },
      error: () => this.toast.error('Révocation impossible.')
    });
  }

  copyKey(): void {
    const key = this.apiKey();
    if (!key) return;
    navigator.clipboard?.writeText(key).then(
      () => this.toast.success('Clé copiée dans le presse-papiers.'),
      () => this.toast.error('Copie impossible.')
    );
  }

  selectEndpoint(summary: EndpointSummary): void {
    this.selectedEndpointId.set(summary.id);
    this.api.getEndpoint(summary.id).subscribe({
      next: (detail) => {
        this.patchEndpointForm(detail);
        this.runResult.set(null);
        this.pipelineError.set(null);
      },
      error: () => this.toast.error('Chargement de l\u2019endpoint impossible.')
    });
  }

  patchEndpointForm(detail: EndpointDetail): void {
    this.scheduleId = detail.schedule?.id ?? null;
    this.endpointForm.patchValue({
      name: detail.name,
      method: detail.method,
      path: detail.path,
      direction: detail.direction,
      description: detail.description ?? '',
      active: detail.active,
      pipeline: JSON.stringify(detail.pipeline ?? [], null, 2),
      cron: detail.schedule?.cronExpression ?? '',
      scheduleEnabled: detail.schedule?.enabled ?? false,
      schedulePayload: detail.schedule?.payload ? JSON.stringify(detail.schedule.payload, null, 2) : '{}'
    });
  }

  newEndpoint(): void {
    this.resetEndpointForm();
    this.selectedEndpointId.set(null);
  }

  saveEndpoint(): void {
    const current = this.apiData();
    if (!current || this.endpointForm.invalid) {
      this.endpointForm.markAllAsTouched();
      return;
    }
    const pipeline = this.parseJson(this.endpointForm.getRawValue().pipeline, 'Le pipeline doit être un tableau JSON valide.');
    if (!Array.isArray(pipeline)) {
      this.pipelineError.set('Le pipeline doit être un tableau JSON d\u2019étapes.');
      return;
    }
    this.pipelineError.set(null);

    const raw = this.endpointForm.getRawValue();
    const payload: EndpointPayload = {
      name: raw.name,
      method: raw.method,
      path: raw.path,
      direction: raw.direction,
      description: raw.description,
      pipeline,
      active: raw.active
    };

    this.savingEndpoint.set(true);
    const selectedId = this.selectedEndpointId();
    const operation = selectedId
      ? this.api.updateEndpoint(selectedId, payload)
      : this.api.createEndpoint(current.id, payload);

    operation.subscribe({
      next: (detail) => {
        this.afterEndpointSaved(detail, raw.cron, raw.scheduleEnabled, raw.schedulePayload);
      },
      error: () => {
        this.savingEndpoint.set(false);
        this.toast.error('Enregistrement de l\u2019endpoint impossible.');
      }
    });
  }

  private afterEndpointSaved(detail: EndpointDetail, cron: string, enabled: boolean, payload: string): void {
    const schedulePayload = this.parseJson(payload, 'Le payload de planification doit être un JSON valide.');
    if (schedulePayload === undefined) {
      this.savingEndpoint.set(false);
      return;
    }

    const finish = () => {
      this.savingEndpoint.set(false);
      this.toast.success('Endpoint enregistré.');
      this.loadApi(this.apiData()!.id, detail.id);
    };

    if (cron.trim()) {
      const schedule: Schedule = {
        cronExpression: cron.trim(),
        enabled,
        payload: schedulePayload
      };
      this.api.upsertSchedule(detail.id, schedule).subscribe({
        next: finish,
        error: () => {
          this.savingEndpoint.set(false);
          this.toast.error('Endpoint enregistré, mais la planification est invalide.');
          this.loadApi(this.apiData()!.id, detail.id);
        }
      });
    } else if (this.scheduleId) {
      this.api.deleteSchedule(detail.id).subscribe({
        next: finish,
        error: () => {
          this.savingEndpoint.set(false);
          this.toast.warning('Endpoint enregistré, mais la suppression de la planification a échoué.');
          this.loadApi(this.apiData()!.id, detail.id);
        }
      });
    } else {
      finish();
    }
  }

  removeEndpoint(): void {
    const id = this.selectedEndpointId();
    const summary = this.selectedEndpoint();
    if (!id || !summary || !confirm(`Supprimer l'endpoint « ${summary.name} » ?`)) return;

    this.deletingEndpoint.set(true);
    this.api.deleteEndpoint(id).subscribe({
      next: () => {
        this.deletingEndpoint.set(false);
        this.toast.success('Endpoint supprimé.');
        this.loadApi(this.apiData()!.id);
      },
      error: () => {
        this.deletingEndpoint.set(false);
        this.toast.error('Suppression impossible.');
      }
    });
  }

  toggleActive(): void {
    const id = this.selectedEndpointId();
    if (!id) return;
    this.api.setEndpointActive(id, !this.endpointForm.getRawValue().active).subscribe({
      next: () => this.loadApi(this.apiData()!.id, id),
      error: () => this.toast.error('Mise à jour impossible.')
    });
  }

  runTest(): void {
    const id = this.selectedEndpointId();
    if (!id) {
      this.toast.warning('Enregistrez d\u2019abord l\u2019endpoint.');
      return;
    }
    const payload = this.parseJson(this.runPayload(), 'Le payload doit être un JSON valide.');
    if (payload === undefined) return;

    this.running.set(true);
    this.runResult.set(null);
    this.api.runEndpoint(id, payload).subscribe({
      next: (run) => {
        this.running.set(false);
        this.runResult.set(run);
        if (run.status === 'SUCCESS') {
          this.toast.success('Exécution réussie.');
        } else {
          this.toast.error('Exécution échouée : ' + (run.errorMessage ?? 'erreur inconnue'));
        }
      },
      error: () => {
        this.running.set(false);
        this.toast.error('Exécution impossible.');
      }
    });
  }

  insertTemplate(kind: string): void {
    const current = this.parseJson(this.endpointForm.getRawValue().pipeline, null);
    const steps: any[] = Array.isArray(current) ? current : [];
    const templates: Record<string, any> = {
      http: { type: 'HTTP', name: 'appel-http', method: 'GET', url: '/resource', connectionId: null, headers: {}, bodyTemplate: null, failOnError: true },
      dbQuery: { type: 'DB_QUERY', name: 'requete-sql', connectionId: null, sql: 'SELECT * FROM ma_table WHERE id = {{payload.id}}', params: [], maxRows: 500 },
      dbExec: { type: 'DB_EXEC', name: 'ecriture-sql', connectionId: null, sql: 'UPDATE ma_table SET statut = ? WHERE id = ?', params: ['{{payload.statut}}', '{{payload.id}}'] },
      fileRead: { type: 'FILE_READ', name: 'lecture-fichier', connectionId: null, path: 'entrees/donnees.csv', format: 'CSV' },
      fileWrite: { type: 'FILE_WRITE', name: 'ecriture-fichier', connectionId: null, path: 'sorties/export.csv', format: 'CSV', data: 'output' },
      map: { type: 'MAP', name: 'mapping', source: 'output', mappings: [{ source: 'nom', target: 'name', transform: 'UPPER', default: '' }] },
      log: { type: 'LOG', name: 'trace', message: 'Étape exécutée pour {{request.path}}' }
    };
    const template = templates[kind];
    if (!template) return;
    steps.push(template);
    this.endpointForm.patchValue({ pipeline: JSON.stringify(steps, null, 2) });
    this.toast.info('Modèle inséré : ' + kind);
  }

  /** Ouvre l'explorateur de base pour générer une étape SQL sans écrire de requête. */
  openBrowser(): void {
    this.connections.list().subscribe({
      next: (list) => {
        const databases = list.filter((connection) =>
          ['ORACLE', 'POSTGRESQL', 'MYSQL', 'SQLSERVER'].includes(connection.type));
        this.browserConnections.set(databases);
        this.browserConnectionId.set(databases[0]?.id ?? null);
        this.browserTable.set(null);
        this.browserOpen.set(true);
        if (databases.length === 0) {
          this.toast.info('Aucune connexion de base de données : créez-en une dans Connexions.');
        }
      },
      error: () => this.toast.error('Impossible de charger les connexions.')
    });
  }

  onBrowserConnectionChange(event: Event): void {
    const value = Number((event.target as HTMLSelectElement).value);
    this.browserConnectionId.set(value || null);
    this.browserTable.set(null);
  }

  onBrowserTable(ref: { schema: string; table: string }): void {
    this.browserTable.set(ref);
  }

  closeBrowser(): void {
    this.browserOpen.set(false);
    this.browserTable.set(null);
  }

  /** Ajoute une étape DB_QUERY lisant la table sélectionnée dans l'explorateur. */
  insertTableStep(): void {
    const ref = this.browserTable();
    const connectionId = this.browserConnectionId();
    if (!ref || !connectionId) {
      this.toast.error('Sélectionnez une table dans la liste.');
      return;
    }
    const oracle = this.browserConnectionType() === 'ORACLE';
    // Oracle stocke les identifiants en majuscules : on ne cite que les noms mixtes.
    const quote = (name: string) => (oracle && name !== name.toUpperCase() ? `"${name}"` : name);
    const current = this.parseJson(this.endpointForm.getRawValue().pipeline, null);
    const steps: any[] = Array.isArray(current) ? current : [];
    steps.push({
      type: 'DB_QUERY',
      name: 'lecture-' + ref.table.toLowerCase().replace(/[^a-z0-9]+/g, '-'),
      connectionId,
      sql: 'SELECT * FROM ' + quote(ref.schema) + '.' + quote(ref.table),
      params: [],
      maxRows: 500
    });
    this.endpointForm.patchValue({ pipeline: JSON.stringify(steps, null, 2) });
    this.toast.success('Étape DB_QUERY ajoutée pour ' + ref.table + '.');
    this.closeBrowser();
  }

  private resetEndpointForm(): void {    this.scheduleId = null;
    this.endpointForm.reset({
      name: '',
      method: 'GET',
      path: '/',
      direction: 'INBOUND',
      description: '',
      active: true,
      pipeline: '[]',
      cron: '',
      scheduleEnabled: false,
      schedulePayload: '{}'
    });
    this.runResult.set(null);
    this.pipelineError.set(null);
  }

  private parseJson(raw: string, errorMessage: string | null): any {
    try {
      return JSON.parse(raw);
    } catch {
      if (errorMessage) {
        this.pipelineError.set(errorMessage);
        this.toast.error(errorMessage);
      }
      return undefined;
    }
  }

  methodClass(method: string): string {
    return 'method method-' + method;
  }

  directionLabel(direction: Direction): string {
    return direction === 'INBOUND' ? 'Entrant' : 'Sortant';
  }

  formatSummary(raw?: string): string {
    if (!raw) return '';
    try {
      return JSON.stringify(JSON.parse(raw), null, 2);
    } catch {
      return raw;
    }
  }

  statusClass(status: string): string {
    switch (status) {
      case 'SUCCESS': return 'badge-success';
      case 'FAILED': return 'badge-danger';
      default: return 'badge-warning';
    }
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'SUCCESS': return 'Réussi';
      case 'FAILED': return 'Échec';
      default: return 'En cours';
    }
  }

  triggerLabel(trigger?: string): string {
    switch (trigger) {
      case 'MANUAL': return 'Manuel';
      case 'SCHEDULE': return 'Planifié';
      case 'INBOUND': return 'Appel entrant';
      default: return trigger ?? '—';
    }
  }
}
