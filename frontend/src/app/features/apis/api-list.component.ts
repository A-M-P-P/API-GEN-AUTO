import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { OrganizationService } from '../../core/organization.service';
import { ToastService } from '../../core/toast.service';
import { ApiSummary, Organization } from '../../core/models';

interface ApiGroup {
  /** Clé de suivi stable pour @for (Angular 17 n'accepte pas `??` dans track). */
  key: string;
  organization: Organization | null;
  apis: ApiSummary[];
}

@Component({
  selector: 'app-api-list',
  standalone: true,
  imports: [DatePipe, RouterLink],
  templateUrl: './api-list.component.html',
  styleUrl: './api-list.component.scss'
})
export class ApiListComponent implements OnInit {
  private api = inject(ApiService);
  private organizationService = inject(OrganizationService);
  private toast = inject(ToastService);

  readonly apis = signal<ApiSummary[]>([]);
  readonly organizations = signal<Organization[]>([]);
  readonly loading = signal(true);
  readonly search = signal('');
  /** 'all' ou l'identifiant de l'organisation filtrée. */
  readonly organizationFilter = signal<string>('all');

  readonly filtered = computed(() => {
    const term = this.search().trim().toLowerCase();
    const filter = this.organizationFilter();
    return this.apis().filter((item) => {
      if (filter === 'none') {
        if (item.organizationId != null) {
          return false;
        }
      } else if (filter !== 'all') {
        const wanted = Number(filter);
        if ((item.organizationId ?? null) !== wanted) {
          return false;
        }
      }
      if (!term) {
        return true;
      }
      return item.name.toLowerCase().includes(term)
        || item.code.toLowerCase().includes(term)
        || (item.description ?? '').toLowerCase().includes(term)
        || (item.organizationName ?? '').toLowerCase().includes(term);
    });
  });

  readonly hasUnassigned = computed(() => this.apis().some((item) => item.organizationId == null));

  /** APIs regroupées par organisation, « non classées » en fin de liste. */
  readonly groups = computed<ApiGroup[]>(() => {
    const byOrganization = new Map<number, ApiSummary[]>();
    const unassigned: ApiSummary[] = [];
    for (const item of this.filtered()) {
      if (item.organizationId == null) {
        unassigned.push(item);
      } else {
        const list = byOrganization.get(item.organizationId) ?? [];
        list.push(item);
        byOrganization.set(item.organizationId, list);
      }
    }

    const groups: ApiGroup[] = [];
    for (const organization of this.organizations()) {
      const items = byOrganization.get(organization.id);
      if (items && items.length > 0) {
        groups.push({ key: 'org-' + organization.id, organization, apis: items });
      }
      byOrganization.delete(organization.id);
    }
    // Organisations référencées par une API mais absentes de la liste (filtre, droits…)
    for (const [id, items] of byOrganization) {
      groups.push({
        key: 'org-' + id,
        organization: {
          id,
          code: String(items[0].organizationCode ?? id),
          name: items[0].organizationName ?? `Organisation ${id}`,
          active: true,
          createdAt: '',
          updatedAt: '',
          apiCount: items.length
        },
        apis: items
      });
    }
    if (unassigned.length > 0) {
      groups.push({ key: 'none', organization: null, apis: unassigned });
    }
    return groups;
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.organizationService.list().subscribe({
      next: (organizations) => this.organizations.set(organizations),
      error: () => this.organizations.set([])
    });
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

  setFilter(value: string): void {
    this.organizationFilter.set(value);
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
