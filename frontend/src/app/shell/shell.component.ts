import { BrandingService } from '../core/branding.service';
import { OrganizationService } from '../core/organization.service';
import { Organization } from '../core/models';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { ToastService } from '../core/toast.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ReactiveFormsModule],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss'
})
export class ShellComponent {
  readonly branding = inject(BrandingService);
  private organizationService = inject(OrganizationService);
  readonly themeOrganizations = signal<Organization[]>([]);
  readonly themeError = signal('');
  constructor() {
    void this.branding.load();
    this.refreshThemeOrganizations();
    inject(DestroyRef).onDestroy(() => this.branding.select(null));
  }
  refreshThemeOrganizations(): void {
    this.organizationService.list().subscribe({ next: rows => { this.themeOrganizations.set(rows); this.themeError.set(''); },
      error: () => this.themeError.set('Organisations indisponibles. Réessayez avec Actualiser.') });
  }
  selectVisualOrganization(event: Event): void {
    const id = Number((event.target as HTMLSelectElement).value);
    this.branding.select(this.themeOrganizations().find(org => org.id === id) || null);
  }

  private router = inject(Router);
  private fb = inject(FormBuilder);
  private toast = inject(ToastService);

  readonly auth = inject(AuthService);
  readonly sidebarOpen = signal(false);
  readonly profileOpen = signal(false);
  readonly savingPassword = signal(false);

  readonly passwordForm = this.fb.nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', Validators.required]
  });

  toggleSidebar(): void {
    this.sidebarOpen.update((open) => !open);
  }

  initials(): string {
    const name = this.auth.user()?.fullName || this.auth.user()?.username || 'U';
    return name
      .split(/\s+/)
      .map((part) => part.charAt(0))
      .join('')
      .slice(0, 2)
      .toUpperCase();
  }

  openProfile(): void {
    this.passwordForm.reset();
    this.profileOpen.set(true);
  }

  closeProfile(): void {
    this.profileOpen.set(false);
    this.passwordForm.reset();
  }

  changePassword(): void {
    const raw = this.passwordForm.getRawValue();
    if (raw.newPassword !== raw.confirmPassword) {
      this.toast.error('Les deux nouveaux mots de passe ne correspondent pas.');
      return;
    }
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    this.savingPassword.set(true);
    this.auth.changePassword(raw.currentPassword, raw.newPassword).subscribe({
      next: () => {
        this.savingPassword.set(false);
        this.toast.success('Mot de passe mis à jour.');
        this.closeProfile();
      },
      error: () => {
        this.savingPassword.set(false);
        this.toast.error('Mot de passe actuel incorrect ou mise à jour impossible.');
      }
    });
  }

  logout(event: Event): void {
    event.preventDefault();
    this.auth.logout();
  }
}
