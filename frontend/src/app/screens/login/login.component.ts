import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AuthService } from '../../services/auth.service';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
})
export class LoginComponent {
  username = '';
  password = '';
  error: string | null = null;
  loading = false;

  constructor(
    private readonly auth: AuthService,
    private readonly api: ApiService,
  ) {}

  submit(): void {
    this.error = null;
    this.loading = true;
    this.auth.login(this.username, this.password);

    // Basic Auth has no separate "log in" call — the first authenticated
    // request either succeeds or comes back 401. This is that first request.
    this.api.listPullRequests().subscribe({
      next: () => {
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.auth.logout();
        this.error = 'Invalid username or password.';
      },
    });
  }
}
