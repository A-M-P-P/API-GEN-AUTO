import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { LoginResponse, Role, User } from './models';

const TOKEN_KEY = 'apigen.token';
const USER_KEY = 'apigen.user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  private currentUser = signal<User | null>(this.readUser());
  readonly user = this.currentUser.asReadonly();

  constructor() {
    if (this.token && !this.currentUser()) {
      this.me().subscribe({ next: (user) => this.currentUser.set(user), error: () => this.clear() });
    }
  }

  get token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  get isAuthenticated(): boolean {
    return !!this.token;
  }

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/login', { username, password }).pipe(
      tap((response) => {
        localStorage.setItem(TOKEN_KEY, response.token);
        localStorage.setItem(USER_KEY, JSON.stringify(response.user));
        this.currentUser.set(response.user);
      })
    );
  }

  me(): Observable<User> {
    return this.http.get<User>('/api/auth/me');
  }

  changePassword(currentPassword: string, newPassword: string): Observable<User> {
    return this.http.post<User>('/api/auth/change-password', { currentPassword, newPassword });
  }

  hasRole(...roles: Role[]): boolean {
    const role = this.currentUser()?.role;
    return !!role && roles.includes(role);
  }

  logout(): void {
    this.clear();
    this.router.navigate(['/login']);
  }

  handleUnauthorized(): void {
    if (this.token) {
      this.clear();
      this.router.navigate(['/login']);
    }
  }

  private clear(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.currentUser.set(null);
  }

  private readUser(): User | null {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? (JSON.parse(raw) as User) : null;
    } catch {
      return null;
    }
  }
}
