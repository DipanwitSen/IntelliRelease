import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';

import { ApiService } from './services/api.service';
import { AuthService } from './services/auth.service';
import { LoginComponent } from './screens/login/login.component';
import { DashboardComponent } from './screens/dashboard/dashboard.component';

type BackendStatus = 'checking' | 'up' | 'down';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, LoginComponent, DashboardComponent],
  templateUrl: './app.component.html',
})
export class AppComponent implements OnInit {
  backendStatus: BackendStatus = 'checking';

  constructor(
    private readonly api: ApiService,
    readonly auth: AuthService,
  ) {}

  ngOnInit(): void {
    this.api.health().subscribe({
      next: () => (this.backendStatus = 'up'),
      error: () => (this.backendStatus = 'down'),
    });
  }
}
