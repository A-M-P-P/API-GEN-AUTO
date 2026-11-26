import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { UserService } from '../../core/user.service';
import { ToastService } from '../../core/toast.service';
import { Role, User } from '../../core/models';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './users.component.html',
  styleUrl: './users.component.scss'
})
export class UsersComponent implements OnInit {
  private service = inject(UserService);
  private fb = inject(FormBuilder);
  private toast = inject(ToastService);

  readonly users = signal<User[]>([]);
  readonly loading = signal(true);
  readonly editorOpen = signal(false);
  readonly editing = signal<User | null>(null);
  readonly saving = signal(false);

  readonly form = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.minLength(3)]],
    password: ['', [Validators.minLength(8)]],
    fullName: [''],
    role: ['VIEWER' as Role],
    enabled: [true]
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.service.list().subscribe({
      next: (list) => {
        this.users.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  openNew(): void {
    this.editing.set(null);
    this.form.controls.password.setValidators([Validators.required, Validators.minLength(8)]);
    this.form.controls.password.updateValueAndValidity();
    this.form.reset({ username: '', password: '', fullName: '', role: 'VIEWER', enabled: true });
    this.editorOpen.set(true);
  }

  openEdit(user: User): void {
    this.editing.set(user);
    // Le mot de passe n'est pas modifié ici : il ne doit pas bloquer la mise à jour.
    this.form.controls.password.clearValidators();
    this.form.controls.password.updateValueAndValidity();
    this.form.reset({ username: user.username, password: '', fullName: user.fullName ?? '', role: user.role, enabled: user.enabled });
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
    this.saving.set(true);

    const operation = current
      ? this.service.update(current.id, { fullName: raw.fullName, role: raw.role, enabled: raw.enabled })
      : this.service.create({ username: raw.username, password: raw.password, fullName: raw.fullName, role: raw.role });

    operation.subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success(current ? 'Utilisateur mis à jour.' : 'Utilisateur créé.');
        this.close();
        this.load();
      },
      error: () => {
        this.saving.set(false);
        this.toast.error('Enregistrement impossible.');
      }
    });
  }

  toggleEnabled(user: User): void {
    this.service.update(user.id, { enabled: !user.enabled }).subscribe({
      next: () => this.load(),
      error: () => this.toast.error('Mise à jour impossible.')
    });
  }

  remove(user: User): void {
    if (!confirm(`Supprimer l'utilisateur « ${user.username} » ?`)) return;
    this.service.delete(user.id).subscribe({
      next: () => {
        this.toast.success('Utilisateur supprimé.');
        this.load();
      },
      error: () => this.toast.error('Suppression impossible.')
    });
  }

  roleLabel(role: Role): string {
    switch (role) {
      case 'ADMIN': return 'Administrateur';
      case 'EDITOR': return 'Éditeur';
      default: return 'Lecture seule';
    }
  }

  roleClass(role: Role): string {
    switch (role) {
      case 'ADMIN': return 'badge-brand';
      case 'EDITOR': return 'badge-info';
      default: return 'badge-muted';
    }
  }
}
