import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ShoppingCart } from '../cart/shopping-cart';
import { AccountService, accountError } from './account';

@Component({
  imports: [FormsModule, RouterLink],
  templateUrl: './login-page.html',
  styleUrl: './forms.css',
})
export class LoginPage {
  protected readonly auth = inject(AccountService);
  protected readonly cart = inject(ShoppingCart);
  private readonly router = inject(Router);
  protected readonly registerMode = signal(false);
  protected readonly busy = signal(false);
  protected readonly feedback = signal('');
  protected email = '';
  protected password = '';
  protected firstName = '';
  protected lastName = '';

  protected switchMode() {
    this.registerMode.update(mode => !mode);
    this.password = '';
    this.feedback.set('');
  }

  protected async submit() {
    if (this.busy() || this.cart.busy()) return;
    this.busy.set(true);
    this.feedback.set('');
    try {
      if (this.registerMode()) {
        await this.auth.register(this.email.trim(), this.password, this.firstName.trim(), this.lastName.trim());
        this.switchMode();
        this.feedback.set('Konto jest gotowe. Zaloguj się, aby kontynuować zakupy.');
      } else {
        await this.auth.login(this.email.trim(), this.password);
        this.password = '';
        await this.router.navigateByUrl('/koszyk');
      }
    } catch (error) {
      this.feedback.set(accountError(error));
    } finally {
      this.busy.set(false);
    }
  }
}
