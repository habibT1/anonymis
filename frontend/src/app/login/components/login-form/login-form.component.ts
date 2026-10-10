import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormGroup, FormControl, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { CommonModule } from '@angular/common';
import { UserCredentials } from '../../model/user-credentials';
import { LanguageService } from 'src/app/shared/services/language.service';

@Component({
  selector: 'app-login-form',
  standalone: true,
  templateUrl: './login-form.component.html',
  styleUrls: ['./login-form.component.scss'],
  imports: [
    ReactiveFormsModule,
    MatIconModule,
    CommonModule,
  ],
})
export class LoginFormComponent {
  @Input() loading = false;
  @Output() login = new EventEmitter<UserCredentials>();

  hide = true;

  constructor(protected i18n: LanguageService) {}

  loginForm = new FormGroup({
    username: new FormControl<string>('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    password: new FormControl<string>('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  togglePassword(event: Event) {
    event.preventDefault();
    this.hide = !this.hide;
  }

  onSubmitForm() {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    const creds: UserCredentials = {
      username: this.loginForm.value.username ?? '',
      password: this.loginForm.value.password ?? '',
    };

    this.login.emit(creds);
  }
}