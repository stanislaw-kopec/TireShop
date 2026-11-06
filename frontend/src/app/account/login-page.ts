import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
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
  private readonly route = inject(ActivatedRoute);
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
        if (this.auth.staff()) {
          await this.router.navigateByUrl('/personel');
          return;
        }
        const orderId = this.route.snapshot.queryParamMap.get('zamowienie');
        const history = this.route.snapshot.queryParamMap.get('powrot') === 'historia';
        const orderLink = orderId && /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(orderId);
        if (orderLink || history) {
          await this.cart.open();
          if (this.cart.mergeState() === 'none') {
            await this.router.navigate(orderLink ? ['/zamowienia/konto', orderId] : ['/konto/zamowienia']);
            return;
          }
        }
        await this.router.navigateByUrl('/koszyk');
      }
    } catch (error) {
      this.feedback.set(accountError(error));
    } finally {
      this.busy.set(false);
    }
  }
}
