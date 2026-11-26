import { APP_INITIALIZER, ApplicationConfig, inject, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { apiBaseInterceptor } from './core/api-base.interceptor';
import { authInterceptor } from './core/auth.interceptor';
import { FrontendConfigService } from './core/frontend-config.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([apiBaseInterceptor, authInterceptor])),
    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: () => {
        const frontendConfig = inject(FrontendConfigService);
        return () => frontendConfig.load();
      }
    }
  ]
};
