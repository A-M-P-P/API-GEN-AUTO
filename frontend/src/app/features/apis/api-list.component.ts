import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { ApiSummary } from '../../core/models';

@Component({
  selector: 'app-api-list',
  standalone: true,
  imports: [DatePipe, RouterLink],
  templateUrl: './api-list.component.html',
  styleUrl: './api-list.component.scss'
})
export class ApiListComponent implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);

  readonly apis = signal<ApiSummary[]>([]);
  readonly loading = signal(true);
  readonly search = signal('');

  readonly filtered = computed(() => {
    const term = this.search().trim().toLowerCase();
    const list = this.apis();
    if (!term) return list;
    return list.filter((item) =>
      item.name.toLowerCase().includes(term) ||
      item.code.toLowerCase().includes(term) ||
      (item.description ?? '').toLowerCase().includes(term)
    );
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.list().subscribe({
      next: (list) => {
        this.apis.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.toast.error('Impossible de charger les APIs.');
      }
    });
  }

  statusClass(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'badge-success';
      case 'PAUSED': return 'badge-warning';
      case 'ARCHIVED': return 'badge-muted';
      default: return 'badge-info';
    }
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'Active';
      case 'PAUSED': return 'En pause';
      case 'ARCHIVED': return 'Archivée';
      default: return 'Brouillon';
    }
  }

  authLabel(auth: string): string {
    switch (auth) {
      case 'API_KEY': return 'Clé API';
      case 'JWT': return 'JWT';
      default: return 'Ouverte';
    }
  }

  remove(item: ApiSummary): void {
    if (!confirm(`Supprimer définitivement l'API « ${item.name} » et ses endpoints ?`)) {
      return;
    }
    this.api.delete(item.id).subscribe({
      next: () => {
        this.apis.update((list) => list.filter((api) => api.id !== item.id));
        this.toast.success('API supprimée.');
      },
      error: () => this.toast.error('Suppression impossible.')
    });
  }
}
