import { normalizeCode } from '../../shared/code-input';
import { BrandingService } from '../../core/branding.service';
import { Component, OnInit, inject, signal, WritableSignal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { OrganizationService } from '../../core/organization.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { Organization } from '../../core/models';

type KeyValueRow = { key: string; value: string };
const MASKED_SECRET = '********';

@Component({
  selector: 'app-organizations',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './organizations.component.html',
  styleUrl: './organizations.component.scss'
})
export class OrganizationsComponent implements OnInit {
  readonly normalizeCode = normalizeCode;

  private service = inject(OrganizationService);
  readonly branding = inject(BrandingService);
  readonly studioTheme = signal('auto');
  private fb = inject(FormBuilder);
  private toast = inject(ToastService);
  readonly auth = inject(AuthService);

  readonly organizations = signal<Organization[]>([]);
  readonly loading = signal(true);
  readonly editorOpen = signal(false);
  readonly editing = signal<Organization | null>(null);
  readonly saving = signal(false);
  readonly search = signal('');
  readonly advancedOpen = signal(false);
  readonly variables = signal<KeyValueRow[]>([]);
  readonly secrets = signal<KeyValueRow[]>([]);
  readonly restBaseUrl = signal('');
  readonly filesBaseDir = signal('');

  readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9][A-Za-z0-9-]{1,59}$/)]],
    name: ['', [Validators.required, Validators.maxLength(150)]],
    description: ['', Validators.maxLength(4000)],
    active: [true]
  });

  ngOnInit(): void {
    void this.branding.load();
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.service.list().subscribe({
      next: (list) => {
        this.organizations.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.toast.error('Impossible de charger les organisations.');
      }
    });
  }

  openNew(): void {
    this.editing.set(null);
    this.form.reset({ code: '', name: '', description: '', active: true });
    this.resetAdvanced();
    this.editorOpen.set(true);
  }

  openEdit(organization: Organization): void {
    this.editing.set(organization);
    this.form.reset({
      code: organization.code,
      name: organization.name,
      description: organization.description ?? '',
      active: organization.active
    });
    this.applyContext(organization.contextConfig);
    this.editorOpen.set(true);
  }

  close(): void {
    this.editorOpen.set(false);
    this.editing.set(null);
  }

  save(): void {
    if (!this.editing()) {
      this.form.controls.code.setValue(normalizeCode(this.form.controls.code.value, false));
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const current = this.editing();
    const payload = {
      code: raw.code.trim(),
      name: raw.name.trim(),
      description: raw.description,
      active: raw.active,
      contextConfig: this.serializeContext()
    };
    this.saving.set(true);
    const operation = current ? this.service.update(current.id, payload) : this.service.create(payload);

    operation.subscribe({
      next: (organization) => {
        if (this.branding.organization()?.id === organization.id) this.branding.select(organization);
        this.saving.set(false);
        this.close();
        this.toast.success(current ? 'Organisation mise à jour.' : 'Organisation créée.');
        this.load();
      },
      error: () => {
        this.saving.set(false);
        this.toast.error('Enregistrement impossible (code déjà utilisé ou paramètre avancé invalide).');
      }
    });
  }

  /** Ajoute une ligne vide dans la section avancée. */
  addRow(target: WritableSignal<KeyValueRow[]>): void {
    target.update((rows) => [...rows, { key: '', value: '' }]);
  }

  removeRow(target: WritableSignal<KeyValueRow[]>, index: number): void {
    target.update((rows) => rows.filter((_, i) => i !== index));
  }

  updateRow(target: WritableSignal<KeyValueRow[]>, index: number,
            field: keyof KeyValueRow, value: string): void {
    target.update((rows) => rows.map((row, i) => i === index ? { ...row, [field]: value } : row));
  }

  private resetAdvanced(): void {
    this.advancedOpen.set(false);
    this.studioTheme.set('auto');
    this.variables.set([]);
    this.secrets.set([]);
    this.restBaseUrl.set('');
    this.filesBaseDir.set('');
  }

  private applyContext(raw?: string | null): void {
    this.resetAdvanced();
    if (!raw?.trim()) {
      return;
    }
    try {
      const context = JSON.parse(raw);
      this.studioTheme.set(context.variables?.studioTheme || 'auto');
      this.variables.set(this.toRows(context.variables).filter(row => row.key !== 'studioTheme'));
      this.secrets.set(this.toRows(context.secrets));
      this.restBaseUrl.set(context.overrides?.rest?.baseUrl ?? '');
      this.filesBaseDir.set(context.overrides?.files?.baseDir ?? '');
      this.advancedOpen.set(this.variables().length > 0 || this.secrets().length > 0
        || !!this.restBaseUrl() || !!this.filesBaseDir());
    } catch {
      // Un contexte illisible est ignoré : il restera modifiable via l'API pour un administrateur.
    }
  }

  private toRows(value: unknown): KeyValueRow[] {
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return [];
    }
    return Object.entries(value as Record<string, unknown>)
      .map(([key, item]) => ({ key, value: item == null ? '' : String(item) }));
  }

  /** Convertit les champs guidés en JSON attendu par le backend. */
  private serializeContext(): string {
    const context: Record<string, unknown> = {};
    const variables = this.toObject(this.variables());
    variables['studioTheme'] = this.studioTheme();
    const secrets = this.toObject(this.secrets());
    if (Object.keys(variables).length) {
      context['variables'] = variables;
    }
    if (Object.keys(secrets).length) {
      context['secrets'] = secrets;
    }
    const overrides: Record<string, unknown> = {};
    if (this.restBaseUrl().trim()) {
      overrides['rest'] = { baseUrl: this.restBaseUrl().trim() };
    }
    if (this.filesBaseDir().trim()) {
      overrides['files'] = { baseDir: this.filesBaseDir().trim() };
    }
    if (Object.keys(overrides).length) {
      context['overrides'] = overrides;
    }
    return Object.keys(context).length ? JSON.stringify(context) : '';
  }

  private toObject(rows: KeyValueRow[]): Record<string, string> {
    const result: Record<string, string> = {};
    rows.forEach((row) => {
      const key = row.key.trim();
      // Une valeur masquée est renvoyée telle quelle : le backend conserve le secret existant.
      if (key) {
        result[key] = row.value === MASKED_SECRET ? MASKED_SECRET : row.value;
      }
    });
    return result;
  }

  toggleActive(organization: Organization): void {
    this.service.update(organization.id, {
      code: organization.code,
      name: organization.name,
      description: organization.description,
      active: !organization.active,
      contextConfig: organization.contextConfig
    }).subscribe({
      next: () => this.load(),
      error: () => this.toast.error('Modification impossible.')
    });
  }

  remove(organization: Organization): void {
    const warning = organization.apiCount > 0
      ? `Supprimer l'organisation « ${organization.name} » ? Ses ${organization.apiCount} API(s) ne seront pas supprimées, elles deviendront non classées.`
      : `Supprimer l'organisation « ${organization.name} » ?`;
    if (!confirm(warning)) {
      return;
    }
    this.service.delete(organization.id).subscribe({
      next: () => {
        this.organizations.update((list) => list.filter((item) => item.id !== organization.id));
        this.toast.success('Organisation supprimée.');
      },
      error: () => this.toast.error('Suppression impossible.')
    });
  }
}
