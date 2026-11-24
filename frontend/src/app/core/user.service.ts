import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Role, User } from './models';

@Injectable({ providedIn: 'root' })
export class UserService {
  private http = inject(HttpClient);

  list(): Observable<User[]> { return this.http.get<User[]>('/api/users'); }

  create(payload: { username: string; password: string; fullName?: string; role: Role }): Observable<User> {
    return this.http.post<User>('/api/users', payload);
  }

  update(id: number, payload: { fullName?: string; role?: Role; enabled?: boolean }): Observable<User> {
    return this.http.put<User>(`/api/users/${id}`, payload);
  }

  delete(id: number): Observable<void> { return this.http.delete<void>(`/api/users/${id}`); }
}
