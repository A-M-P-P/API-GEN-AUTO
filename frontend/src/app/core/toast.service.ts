import { Injectable, signal } from '@angular/core';

export interface Toast {
  id: number;
  message: string;
  type: 'success' | 'error' | 'info' | 'warning';
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private counter = 0;
  readonly toasts = signal<Toast[]>([]);

  success(message: string): void { this.push(message, 'success'); }
  error(message: string): void { this.push(message, 'error'); }
  info(message: string): void { this.push(message, 'info'); }
  warning(message: string): void { this.push(message, 'warning'); }

  dismiss(id: number): void {
    this.toasts.update((list) => list.filter((toast) => toast.id !== id));
  }

  private push(message: string, type: Toast['type']): void {
    const id = ++this.counter;
    this.toasts.update((list) => [...list, { id, message, type }]);
    setTimeout(() => this.dismiss(id), 5200);
  }
}
