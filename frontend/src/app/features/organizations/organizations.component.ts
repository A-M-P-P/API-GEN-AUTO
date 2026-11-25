import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { OrganizationService } from '../../core/organization.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { Organization } from '../../core/models';

@Component({
  selector: 'app-organizations',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './organizations.component.html',
  styleUrl: './organizations.component.scss'
})
export class OrganizationsComponent implements OnInit {
  private service = inject(OrganizationService);
  private fb = inject(FormBuilder);
  private toast = inject(ToastService);
  readonly auth = inject(AuthService);

  readonly organizations = signal<Organization[]>([]);
  readonly loading = signal(true);
  readonly editorOpen = signal(false);
  readonly editing = signal<Organization | null>(null);
  readonly saving = signal(false);
  readonly search = signal('');

  readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9][A-Za-z0-9-]{1,59}$/)]],
    name: ['', [Validators.required, Validators.maxLength(150)]],
    description: ['', Validators.maxLength(4000)],
    active: [true]
  });

  ngOnInit(): void {
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
    this.editorOpen.set(true);
  }

  close(): void {
    this.editorOpen.set(false);
    this.editing.set(null);
  }

  save(): void {
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
      active: raw.active
    };
    this.saving.set(true);
    const operation = current ? this.service.update(current.id, payload) : this.service.create(payload);

    operation.subscribe({
      next: () => {
        this.saving.set(false);
        this.close();
        this.toast.success(current ? 'Organisation mise à jour.' : 'Organisation créée.');
        this.load();
      },
      error: () => {
        this.saving.set(false);
        this.toast.error('Enregistrement impossible (code déjà utilisé ?).');
      }
    });
  }

  toggleActive(organization: Organization): void {
    this.service.update(organization.id, {
      code: organization.code,
      name: organization.name,
      description: organization.description,
      active: !organization.active
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
