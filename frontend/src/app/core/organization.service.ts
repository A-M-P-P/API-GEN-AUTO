import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Organization, OrganizationPayload } from './models';

@Injectable({ providedIn: 'root' })
export class OrganizationService {
  private http = inject(HttpClient);

  list(): Observable<Organization[]> { return this.http.get<Organization[]>('/api/organizations'); }
  get(id: number): Observable<Organization> { return this.http.get<Organization>(`/api/organizations/${id}`); }
  create(payload: OrganizationPayload): Observable<Organization> {
    return this.http.post<Organization>('/api/organizations', payload);
  }
  update(id: number, payload: OrganizationPayload): Observable<Organization> {
    return this.http.put<Organization>(`/api/organizations/${id}`, payload);
  }
  delete(id: number): Observable<void> { return this.http.delete<void>(`/api/organizations/${id}`); }
}
