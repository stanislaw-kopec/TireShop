import { Component, OnInit, inject, signal } from '@angular/core';
import { AccountService, accountError } from './account/account';
import { ShoppingCart } from './cart/shopping-cart';
import { Router, RouterLink, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit {
  protected readonly cart = inject(ShoppingCart);
  protected readonly auth = inject(AccountService);
  private readonly router = inject(Router);
  protected readonly loggingOut = signal(false);
  ngOnInit() { void this.cart.refresh(); }

  protected async logout() {
    if (this.loggingOut()) return;
    this.loggingOut.set(true);
    try {
      await this.auth.logout();
      await this.router.navigateByUrl('/');
      await this.cart.refresh();
    } catch (error) {
      if (this.auth.token()) this.auth.notice.set(accountError(error));
    } finally {
      this.loggingOut.set(false);
    }
  }
}
