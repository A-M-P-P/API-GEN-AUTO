import { Routes } from '@angular/router';
import { adminGuard, authGuard, editorGuard } from './core/auth.guard';
import { ShellComponent } from './shell/shell.component';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent)
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent)
      },
      {
        path: 'apis',
        loadComponent: () => import('./features/apis/api-list.component').then((m) => m.ApiListComponent)
      },
      {
        path: 'apis/new',
        canActivate: [editorGuard],
        loadComponent: () => import('./features/apis/api-builder.component').then((m) => m.ApiBuilderComponent)
      },
      {
        path: 'apis/:id',
        loadComponent: () => import('./features/apis/api-builder.component').then((m) => m.ApiBuilderComponent)
      },
      {
        path: 'connections',
        loadComponent: () => import('./features/connections/connections.component').then((m) => m.ConnectionsComponent)
      },
      {
        path: 'runs',
        loadComponent: () => import('./features/runs/runs.component').then((m) => m.RunsComponent)
      },
      {
        path: 'users',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/users/users.component').then((m) => m.UsersComponent)
      },
      {
        path: 'audit',
        loadComponent: () => import('./features/audit/audit.component').then((m) => m.AuditComponent)
      },
      {
        path: 'config',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/config/config.component').then((m) => m.ConfigComponent)
      }
    ]
  },
  { path: '**', redirectTo: 'dashboard' }
];
