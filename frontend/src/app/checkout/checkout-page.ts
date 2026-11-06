import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AddressFields } from '../account/address-fields';
import { Router, RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { AccountService } from '../account/account';
import { ShoppingCart, guestCartToken } from '../cart/shopping-cart';
import { priceFormat, seasons } from '../catalog/catalog-api';
import { CheckoutApi, Delivery, Quote, checkoutError } from './checkout-api';

@Component({
  imports: [FormsModule, RouterLink, DatePipe, AddressFields],
  templateUrl: './checkout-page.html',
  styleUrls: ['../account/forms.css', './checkout.css'],
})
export class CheckoutPage implements OnInit {
  protected readonly auth = inject(AccountService);
  private readonly api = inject(CheckoutApi);
  private readonly cart = inject(ShoppingCart);
  private readonly router = inject(Router);
  private readonly life = inject(DestroyRef);
  protected readonly owner = this.auth.profile()?.id ?? 'guest';
  protected readonly step = signal<'choice' | 'loading' | 'addressError' | 'address' | 'summary' | 'retry' | 'merge'>('choice');
  protected readonly quote = signal<Quote | null>(null);
  protected readonly busy = signal(false);
  protected readonly feedback = signal('');
  protected readonly money = priceFormat;
  protected readonly seasons = seasons;
  protected readonly delivery: Delivery = {
    email: this.auth.profile()?.email ?? '', phone: this.auth.profile()?.phone ?? '',
    address: { recipientName: '', street: '', buildingNumber: '', apartmentNumber: '', postalCode: '', city: '', countryCode: 'PL' },
  };

  ngOnInit() {
    if (this.api.attempt(this.owner)?.submitted) this.step.set('retry');
    else if (this.owner !== 'guest') {
      if (guestCartToken() || this.cart.mergeState() !== 'none') this.step.set('merge');
      else void this.loadAddress();
    }
  }

  protected async loadAddress() {
    this.step.set('loading');
    this.feedback.set('');
    try {
      const saved = await this.auth.address();
      if (saved) this.delivery.address = saved;
      this.step.set('address');
    } catch (error) {
      this.feedback.set(checkoutError(error));
      this.step.set('addressError');
    }
  }

  protected async preview() {
    if (this.busy() || this.step() !== 'address') return;
    this.busy.set(true);
    this.feedback.set('');
    try {
      const address = this.delivery.address;
      const result = await this.api.preview(this.owner, { email: this.delivery.email.trim(), phone: this.delivery.phone.trim(),
        address: { ...address, apartmentNumber: address.apartmentNumber?.trim() || null, countryCode: address.countryCode.toUpperCase() } });
      this.quote.set(result);
      this.step.set('summary');
    } catch (error) {
      this.feedback.set(checkoutError(error));
      if (this.api.attempt(this.owner)?.submitted) this.step.set('retry');
    } finally {
      this.busy.set(false);
    }
  }

  protected async confirm() {
    if (this.busy()) return;
    const attempt = this.api.attempt(this.owner);
    const id = this.step() === 'summary' ? this.quote()?.id : attempt?.submitted ? attempt.quoteId : null;
    if (!id) return;
    this.busy.set(true);
    this.feedback.set('');
    try {
      const order = await this.api.confirm(this.owner, id);
      if (this.life.destroyed || (this.owner !== 'guest' && this.owner !== this.auth.profile()?.id)) return;
      void this.cart.refresh();
      const destination = ['/zamowienia', this.owner === 'guest' ? 'gosc' : 'konto', order.id];
      if (order.status === 'PENDING_PAYMENT') destination.push('platnosc');
      await this.router.navigate(destination, { replaceUrl: true });
    } catch (error) {
      this.feedback.set(checkoutError(error));
      if (this.api.attempt(this.owner)?.submitted) this.step.set('retry');
      else {
        this.quote.set(null);
        this.step.set('address');
      }
    } finally {
      this.busy.set(false);
    }
  }
}
