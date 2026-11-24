import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { DashboardService } from '../../core/dashboard.service';
import { Dashboard } from '../../core/models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  private service = inject(DashboardService);

  readonly dashboard = signal<Dashboard | null>(null);
  readonly loading = signal(true);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.service.get().subscribe({
      next: (data) => {
        this.dashboard.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
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
}
