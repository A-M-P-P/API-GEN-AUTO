import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AuditEvent, Page } from './models';

@Injectable({ providedIn: 'root' })
export class AuditService {
  private http = inject(HttpClient);

  list(page = 0, size = 50): Observable<Page<AuditEvent>> {
    return this.http.get<Page<AuditEvent>>('/api/audit', { params: { page, size } });
  }
}
