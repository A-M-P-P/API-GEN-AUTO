import { BrandingService } from '../core/branding.service';
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
  readonly logoFailures = signal<Record<string, boolean>>({});
  logoFailed(id: string): void { this.logoFailures.update(values => ({ ...values, [id]: true })); }
  constructor() {
    void this.branding.load();
    inject(DestroyRef).onDestroy(() => this.branding.select(null));
  }
  readonly Math = Math;

  platformOffset(index: number): number {
    const themes = this.branding.themes();
    const active = Math.max(0, themes.findIndex(theme => theme.id === this.branding.current().id));
    const offset = (index - active + themes.length) % themes.length;
    return offset > themes.length / 2 ? offset - themes.length : offset;
  }

  selectPlatform(id: string): void {
    this.branding.selectPlatform(id, []);
  }

  rotatePlatform(direction: number): void {
    const themes = this.branding.themes();
    const active = Math.max(0, themes.findIndex(theme => theme.id === this.branding.current().id));
    this.selectPlatform(themes[(active + direction + themes.length) % themes.length].id);
  }

  carouselKey(event: KeyboardEvent): void {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    event.preventDefault();
    this.rotatePlatform(event.key === 'ArrowRight' ? 1 : -1);
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
