import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/auth.service';

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

  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly showPassword = signal(false);

  readonly form = this.fb.nonNullable.group({
    username: ['', [Validators.required]],
    password: ['', [Validators.required]]
  });

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const { username, password } = this.form.getRawValue();
    this.auth.login(username, password).subscribe({
      next: () => {
        this.submitting.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        const detail = typeof err.error?.message === 'string' ? err.error.message : null;
        if (err.status === 401) {
          this.error.set('Identifiants incorrects ou compte désactivé.');
        } else if (err.status === 0) {
          this.error.set('Le serveur n’est pas joignable. Vérifiez qu’il est démarré.');
        } else if (err.status === 404) {
          this.error.set('La connexion est introuvable. Vérifiez le chemin de contexte (/apigen) dans Configuration, puis redémarrez le backend.');
        } else {
          this.error.set(detail ?? 'Connexion impossible. Réessayez.');
        }
      }
    });
  }

  togglePassword(): void {
    this.showPassword.update((value) => !value);
  }

  fillDemo(): void {
    this.form.patchValue({ username: 'admin', password: 'ChangeMe123!' });
  }
}
