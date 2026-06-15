import { Injectable, signal, computed, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs/operators';

interface PortalUser {
  token: string;
  email: string;
  nombre_completo: string;
}

const TOKEN_KEY = 'portal_token';
const USER_KEY  = 'portal_user';

@Injectable({ providedIn: 'root' })
export class PortalAuthService {
  private readonly http   = inject(HttpClient);
  private readonly router = inject(Router);

  private _user = signal<PortalUser | null>(this.loadUser());

  readonly user        = this._user.asReadonly();
  readonly isLoggedIn  = computed(() => !!this._user());
  readonly token       = computed(() => this._user()?.token ?? null);
  readonly nombreCompleto = computed(() => this._user()?.nombre_completo ?? '');

  login(email: string, password: string) {
    return this.http.post<PortalUser>('/api/portal/auth/login', { email, password }).pipe(
      tap(res => this.saveUser(res))
    );
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this._user.set(null);
    this.router.navigate(['/login']);
  }

  getAuthHeaders(): Record<string, string> {
    const t = this.token();
    return t ? { Authorization: `Bearer ${t}` } : {};
  }

  private saveUser(u: PortalUser): void {
    localStorage.setItem(TOKEN_KEY, u.token);
    localStorage.setItem(USER_KEY, JSON.stringify(u));
    this._user.set(u);
  }

  private loadUser(): PortalUser | null {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch { return null; }
  }
}
