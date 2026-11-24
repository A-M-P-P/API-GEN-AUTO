import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page, Run, RunStatus } from './models';

@Injectable({ providedIn: 'root' })
export class RunService {
  private http = inject(HttpClient);

  list(params: { apiId?: number; endpointId?: number; status?: RunStatus; page?: number; size?: number }): Observable<Page<Run>> {
    return this.http.get<Page<Run>>('/api/runs', { params: params as any });
  }

  get(id: number): Observable<Run> { return this.http.get<Run>(`/api/runs/${id}`); }
}
