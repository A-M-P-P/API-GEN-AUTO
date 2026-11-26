import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { FrontendConfigService } from './frontend-config.service';

/**
 * Ajoute l'adresse du backend (config.json) aux appels `/api/...` et `/actuator/...`.
 * Sans configuration, les URL restent relatives : c'est le dev-server Angular (ou
 * nginx) qui les achemine vers le backend.
 */
export const apiBaseInterceptor: HttpInterceptorFn = (req, next) => {
  const base = inject(FrontendConfigService).apiBaseUrl();
  if (!base || !/^\/(api|actuator)(\/|$)/.test(req.url)) {
    return next(req);
  }
  return next(req.clone({ url: base + req.url }));
};
