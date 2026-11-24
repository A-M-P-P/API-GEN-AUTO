import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { AuditService } from '../../core/audit.service';
import { AuditEvent } from '../../core/models';

@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './audit.component.html',
  styleUrl: './audit.component.scss'
})
export class AuditComponent implements OnInit {
  private service = inject(AuditService);

  readonly events = signal<AuditEvent[]>([]);
  readonly loading = signal(true);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly size = 30;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.service.list(this.page(), this.size).subscribe({
      next: (result) => {
        this.events.set(result.content);
        this.totalPages.set(result.totalPages);
        this.totalElements.set(result.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  goPage(delta: number): void {
    const next = this.page() + delta;
    if (next < 0 || next >= this.totalPages()) return;
    this.page.set(next);
    this.load();
  }

  actionClass(action: string): string {
    if (action.includes('DELETE') || action.includes('REVOKE')) return 'badge-danger';
    if (action.includes('CREATE') || action.includes('GENERATE')) return 'badge-success';
    if (action.includes('LOGIN') || action.includes('PASSWORD')) return 'badge-info';
    return 'badge-muted';
  }

  actionLabel(action: string): string {
    const labels: Record<string, string> = {
      LOGIN: 'Connexion',
      API_CREATE: 'Création API',
      API_UPDATE: 'Mise à jour API',
      API_DELETE: 'Suppression API',
      API_KEY_GENERATE: 'Génération clé API',
      API_KEY_REVOKE: 'Révocation clé API',
      ENDPOINT_CREATE: 'Création endpoint',
      ENDPOINT_UPDATE: 'Mise à jour endpoint',
      ENDPOINT_DELETE: 'Suppression endpoint',
      SCHEDULE_UPDATE: 'Mise à jour planification',
      SCHEDULE_DELETE: 'Suppression planification',
      CONNECTION_CREATE: 'Création connexion',
      CONNECTION_UPDATE: 'Mise à jour connexion',
      CONNECTION_DELETE: 'Suppression connexion',
      USER_CREATE: 'Création utilisateur',
      USER_UPDATE: 'Mise à jour utilisateur',
      USER_DELETE: 'Suppression utilisateur',
      PASSWORD_CHANGE: 'Changement de mot de passe'
    };
    return labels[action] ?? action;
  }
}
