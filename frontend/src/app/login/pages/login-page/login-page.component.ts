import { Component } from '@angular/core';
import { LoginFormComponent } from '../../components/login-form/login-form.component';
import { UserCredentials } from '../../model/user-credentials';
import { AuthenticationService } from '../../services/authentication.service';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { LanguageService } from 'src/app/shared/services/language.service';

@Component({
  selector: 'app-login-page',
  templateUrl: './login-page.component.html',
  styleUrls: ['./login-page.component.scss'],
  imports: [LoginFormComponent],
  standalone: true,
})
export class LoginPageComponent {
  erreurMessageKey: string | null = null;
  isLoading = false;

  constructor(
      private auth: AuthenticationService,
      private router: Router,
      protected i18n: LanguageService,
  ) {}

  async onLogin(userCredentials: UserCredentials): Promise<void> {
    this.erreurMessageKey = null;
    this.isLoading = true;

    try {
      await this.auth.login(userCredentials);
      await this.router.navigate(['/chat']);
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 403) {
        this.erreurMessageKey = 'invalidCredentials';
      } else {
        this.erreurMessageKey = 'connectionError';
      }
    } finally {
      this.isLoading = false;
    }
  }
}