import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ColumnsResult,
  Connection,
  ConnectionPayload,
  ConnectionTestResult,
  DiscoveryQuery,
  DiscoveryResult
} from './models';

@Injectable({ providedIn: 'root' })
export class ConnectionService {
  private http = inject(HttpClient);

  list(): Observable<Connection[]> { return this.http.get<Connection[]>('/api/connections'); }
  get(id: number): Observable<Connection> { return this.http.get<Connection>(`/api/connections/${id}`); }
  create(payload: ConnectionPayload): Observable<Connection> { return this.http.post<Connection>('/api/connections', payload); }
  update(id: number, payload: ConnectionPayload): Observable<Connection> { return this.http.put<Connection>(`/api/connections/${id}`, payload); }
  delete(id: number): Observable<void> { return this.http.delete<void>(`/api/connections/${id}`); }

  test(payload: { type: string; baseConfig: any; credentials?: any }): Observable<ConnectionTestResult> {
    return this.http.post<ConnectionTestResult>('/api/connections/test', payload);
  }

  testExisting(id: number): Observable<ConnectionTestResult> {
    return this.http.post<ConnectionTestResult>(`/api/connections/${id}/test`, {});
  }

  discover(query: DiscoveryQuery): Observable<DiscoveryResult> {
    return this.http.post<DiscoveryResult>('/api/connections/discover', query);
  }

  columns(query: DiscoveryQuery, schema: string, table: string): Observable<ColumnsResult> {
    return this.http.post<ColumnsResult>('/api/connections/discover/columns',
      { ...query, schema, table });
  }
}
