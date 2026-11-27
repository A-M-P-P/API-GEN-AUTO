import { BrandingService } from './core/branding.service';
import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastService } from './core/toast.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `
    <router-outlet></router-outlet>
    <div class="toast-stack">
      @for (toast of toasts(); track toast.id) {
        <div class="toast toast-{{ toast.type }}" (click)="dismiss(toast.id)">
          <span class="toast-dot"></span>
          <span>{{ toast.message }}</span>
        </div>
      }
    </div>
  `,
  styles: [`
    .toast-stack {
      position: fixed;
      right: 22px;
      bottom: 22px;
      display: flex;
      flex-direction: column;
      gap: 10px;
      z-index: 1000;
      max-width: 380px;
    }
    .toast {
      display: flex;
      align-items: center;
      gap: 10px;
      background: #0f172a;
      color: #e2e8f0;
      border-radius: 11px;
      padding: 11px 15px;
      font-size: 13px;
      box-shadow: 0 14px 34px rgba(15, 23, 42, .3);
      cursor: pointer;
      animation: toast-in .22s ease both;
      border: 1px solid rgba(255,255,255,.08);
    }
    .toast-dot { width: 8px; height: 8px; border-radius: 50%; background: #38bdf8; flex: none; }
    .toast-success .toast-dot { background: #34d399; }
    .toast-error .toast-dot { background: #f87171; }
    .toast-warning .toast-dot { background: #fbbf24; }
    @keyframes toast-in {
      from { opacity: 0; transform: translateY(8px); }
      to { opacity: 1; transform: translateY(0); }
    }
  `]
})
export class AppComponent {
  constructor() { void inject(BrandingService).load(); }

  private toastService = inject(ToastService);
  readonly toasts = this.toastService.toasts;

  dismiss(id: number): void {
    this.toastService.dismiss(id);
  }
}
