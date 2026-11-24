import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { RunService } from '../../core/run.service';
import { ApiSummary, Run, RunStatus } from '../../core/models';

@Component({
  selector: 'app-runs',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  templateUrl: './runs.component.html',
  styleUrl: './runs.component.scss'
})
export class RunsComponent implements OnInit {
  private runService = inject(RunService);
  private apiService = inject(ApiService);

  readonly runs = signal<Run[]>([]);
  readonly apis = signal<ApiSummary[]>([]);
  readonly loading = signal(true);
  readonly page = signal(0);
  readonly size = signal(20);
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);
  readonly statusFilter = signal<RunStatus | ''>('');
  readonly apiFilter = signal<number | ''>('');
  readonly expanded = signal<number | null>(null);

  ngOnInit(): void {
    this.apiService.list().subscribe({ next: (list) => this.apis.set(list) });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    const params: any = { page: this.page(), size: this.size() };
    if (this.statusFilter()) params.status = this.statusFilter();
    if (this.apiFilter()) params.apiId = this.apiFilter();

    this.runService.list(params).subscribe({
      next: (result) => {
        this.runs.set(result.content);
        this.totalElements.set(result.totalElements);
        this.totalPages.set(result.totalPages);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  setStatus(value: string): void {
    this.statusFilter.set(value as RunStatus | '');
    this.page.set(0);
    this.load();
  }

  setApi(value: string): void {
    this.apiFilter.set(value ? Number(value) : '');
    this.page.set(0);
    this.load();
  }

  goPage(delta: number): void {
    const next = this.page() + delta;
    if (next < 0 || next >= this.totalPages()) return;
    this.page.set(next);
    this.load();
  }

  toggle(id: number): void {
    this.expanded.update((current) => (current === id ? null : id));
  }

  statusClass(status: string): string {
    switch (status) {
      case 'SUCCESS': return 'badge-success';
      case 'FAILED': return 'badge-danger';
      default: return 'badge-warning';
    }
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'SUCCESS': return 'Réussi';
      case 'FAILED': return 'Échec';
      default: return 'En cours';
    }
  }

  triggerLabel(trigger?: string): string {
    switch (trigger) {
      case 'MANUAL': return 'Manuel';
      case 'SCHEDULE': return 'Planifié';
      case 'INBOUND': return 'Entrant';
      default: return trigger ?? '—';
    }
  }

  formatSummary(raw?: string): string {
    if (!raw) return '';
    try {
      return JSON.stringify(JSON.parse(raw), null, 2);
    } catch {
      return raw;
    }
  }
}
