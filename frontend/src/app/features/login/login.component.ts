import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/auth.service';
import { FrontendConfigService } from '../../core/frontend-config.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);
  readonly frontendConfig = inject(FrontendConfigService);

  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly showPassword = signal(false);

  readonly form = this.fb.nonNullable.group({
    username: ['', [Validators.required]],
    password: ['', [Validators.required]]
  });

  readonly settingsForm = this.fb.nonNullable.group({
    backendUrl: [this.frontendConfig.snapshot().backendUrl],
    contextPath: [this.frontendConfig.snapshot().contextPath]
  });

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const { username, password } = this.form.getRawValue();
    let retried = false;
    const attempt = (): void => {
      this.auth.login(username, password).subscribe({
        next: () => {
          this.submitting.set(false);
          this.router.navigate(['/dashboard']);
        },
        error: (err: HttpErrorResponse) => {
          // Le proxy détecte le préfixe au démarrage ; si le backend a été redémarré
          // entre-temps, un 404 déclenche une nouvelle détection. On réessaie une fois.
          if (err.status === 404 && !retried) {
            retried = true;
            window.setTimeout(attempt, 1700);
            return;
          }
          this.submitting.set(false);
          this.showError(err);
        }
      });
    };
    attempt();
  }

  private showError(err: HttpErrorResponse): void {
    const detail = typeof err.error?.message === 'string' ? err.error.message : null;
    if (err.status === 401) {
      this.error.set('Identifiants incorrects ou compte désactivé.');
    } else if (err.status === 0) {
      this.error.set('Le serveur n’est pas joignable. Vérifiez qu’il est démarré et que l’adresse du backend dans config.json est correcte.');
    } else if (err.status === 404) {
      this.error.set('La connexion est introuvable. Vérifiez l’adresse du backend et son chemin de contexte dans Configuration, puis rechargez le studio.');
    } else {
      this.error.set(detail ?? 'Connexion impossible. Réessayez.');
    }
  }

  togglePassword(): void {
    this.showPassword.update((value) => !value);
  }

  applySettings(): void {
    this.frontendConfig.applyOverride(this.settingsForm.getRawValue());
    window.location.reload();
  }

  resetSettings(): void {
    this.frontendConfig.resetOverride();
    window.location.reload();
  }

  fillDemo(): void {
    this.form.patchValue({ username: 'admin', password: 'ChangeMe123!' });
  }
}
