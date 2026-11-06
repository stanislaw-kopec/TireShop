import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AccountService } from '../account/account';
import { priceFormat } from '../catalog/catalog-api';
import { CheckoutApi, Order } from '../checkout/checkout-api';
import { Outcome, Payment, PaymentApi, paymentError } from './payment-api';

interface Context { owner: string; id: string; receipt: string[] }

@Component({
  imports: [DatePipe, RouterLink],
  templateUrl: './payment-page.html',
  styleUrls: ['../account/forms.css', '../checkout/checkout.css'],
})
export class PaymentPage {
  private readonly api = inject(PaymentApi);
  private readonly checkout = inject(CheckoutApi);
  private readonly auth = inject(AccountService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly life = inject(DestroyRef);
  private readonly params = toSignal(this.route.paramMap, { requireSync: true });
  protected readonly orderId = computed(() => this.params().get('id')!);
  protected readonly context = computed<Context | null>(() => {
    const id = this.orderId();
    const guest = this.route.snapshot.data['guest'] === true;
    const owner = guest ? 'guest' : this.auth.profile()?.id;
    return owner ? { owner, id, receipt: ['/zamowienia', guest ? 'gosc' : 'konto', id] } : null;
  });
  protected readonly order = signal<Order | null>(null);
  protected readonly payment = signal<Payment | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly money = priceFormat;
  private timer?: ReturnType<typeof setTimeout>;

  constructor() {
    effect(() => {
      const ctx = this.context();
      untracked(() => {
        clearTimeout(this.timer);
        this.order.set(null);
        this.payment.set(null);
        this.error.set('');
        if (ctx) void this.run(ctx, () => this.load(ctx));
      });
    });
    this.life.onDestroy(() => clearTimeout(this.timer));
  }

  private current(ctx: Context) { return !this.life.destroyed && this.context() === ctx; }

  private async load(ctx: Context) {
    const order = await this.checkout.order(ctx.owner, ctx.id);
    if (!this.current(ctx)) return;
    this.order.set(order);
    if (order.status !== 'PENDING_PAYMENT') {
      await this.router.navigate(ctx.receipt, { replaceUrl: true });
      return;
    }
    const payment = await this.api.resume(ctx.owner, ctx.id);
    if (!this.current(ctx)) return;
    this.payment.set(payment);
    clearTimeout(this.timer);
    const remaining = Date.parse(order.reservationExpiresAt) - Date.now();
    if (remaining > 0 && remaining < 2_147_483_647) this.timer = setTimeout(() => this.refresh(), remaining + 100);
  }

  protected refresh() {
    const ctx = this.context();
    if (ctx && !this.busy()) void this.run(ctx, () => this.load(ctx));
  }

  protected retry() {
    const ctx = this.context();
    const status = this.payment()?.status;
    if (!ctx || this.busy() || (status !== 'FAILED' && status !== 'CANCELLED')) return;
    void this.run(ctx, async () => {
      const payment = await this.api.start(ctx.owner, ctx.id);
      if (this.current(ctx)) this.payment.set(payment);
    });
  }

  protected finish(outcome: Outcome) {
    const ctx = this.context();
    const payment = this.payment();
    if (!ctx || !payment || payment.status !== 'PENDING' || this.busy() || this.error()) return;
    void this.run(ctx, async () => {
      await this.api.result(ctx.owner, ctx.id, payment.id, outcome);
      if (this.current(ctx)) await this.load(ctx);
    });
  }

  private async run(ctx: Context, operation: () => Promise<void>) {
    this.busy.set(true);
    this.error.set('');
    try {
      await operation();
    } catch (error) {
      if (!this.current(ctx)) return;
      this.error.set(paymentError(error));
      if (error instanceof HttpErrorResponse && ['ORDER_NOT_PAYABLE', 'PAYMENT_EXPIRED'].includes(error.error?.code)) {
        await this.router.navigate(ctx.receipt, { replaceUrl: true });
      }
    } finally {
      if (this.current(ctx)) this.busy.set(false);
    }
  }
}
