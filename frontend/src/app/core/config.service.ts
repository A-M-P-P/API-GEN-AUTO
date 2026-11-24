import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ConfigEntry, ConfigUpdateResult, ConfigView } from './models';

@Injectable({ providedIn: 'root' })
export class ConfigService {
  private http = inject(HttpClient);

  view(): Observable<ConfigView> {
    return this.http.get<ConfigView>('/api/config');
  }

  update(values: Record<string, string>): Observable<ConfigUpdateResult> {
    return this.http.put<ConfigUpdateResult>('/api/config', { values });
  }
}
