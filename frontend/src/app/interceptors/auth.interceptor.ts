import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const header = inject(AuthService).authHeader();
  if (!header) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { Authorization: header } }));
};
