import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ApiDetail,
  ApiKeyResponse,
  ApiPayload,
  ApiSummary,
  EndpointDetail,
  EndpointPayload,
  Run,
  Schedule
} from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);

  list(): Observable<ApiSummary[]> { return this.http.get<ApiSummary[]>('/api/apis'); }
  get(id: number): Observable<ApiDetail> { return this.http.get<ApiDetail>(`/api/apis/${id}`); }
  create(payload: ApiPayload): Observable<ApiDetail> { return this.http.post<ApiDetail>('/api/apis', payload); }
  update(id: number, payload: ApiPayload): Observable<ApiDetail> { return this.http.put<ApiDetail>(`/api/apis/${id}`, payload); }
  delete(id: number): Observable<void> { return this.http.delete<void>(`/api/apis/${id}`); }

  generateKey(id: number): Observable<ApiKeyResponse> {
    return this.http.post<ApiKeyResponse>(`/api/apis/${id}/api-key`, {});
  }

  revokeKey(id: number): Observable<void> {
    return this.http.delete<void>(`/api/apis/${id}/api-key`);
  }

  getEndpoint(id: number): Observable<EndpointDetail> {
    return this.http.get<EndpointDetail>(`/api/endpoints/${id}`);
  }

  createEndpoint(apiId: number, payload: EndpointPayload): Observable<EndpointDetail> {
    return this.http.post<EndpointDetail>(`/api/apis/${apiId}/endpoints`, payload);
  }

  updateEndpoint(id: number, payload: EndpointPayload): Observable<EndpointDetail> {
    return this.http.put<EndpointDetail>(`/api/endpoints/${id}`, payload);
  }

  deleteEndpoint(id: number): Observable<void> {
    return this.http.delete<void>(`/api/endpoints/${id}`);
  }

  setEndpointActive(id: number, active: boolean): Observable<EndpointDetail> {
    return this.http.patch<EndpointDetail>(`/api/endpoints/${id}/active?active=${active}`, {});
  }

  upsertSchedule(endpointId: number, payload: Schedule): Observable<EndpointDetail> {
    return this.http.put<EndpointDetail>(`/api/endpoints/${endpointId}/schedule`, payload);
  }

  deleteSchedule(endpointId: number): Observable<void> {
    return this.http.delete<void>(`/api/endpoints/${endpointId}/schedule`);
  }

  runEndpoint(endpointId: number, payload: any): Observable<Run> {
    return this.http.post<Run>(`/api/endpoints/${endpointId}/run`, { payload: payload ?? {} });
  }
}
