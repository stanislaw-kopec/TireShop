import { Component, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { GuestCart } from './guest-cart';

@Component({
  selector: 'app-add-to-cart',
  imports: [RouterLink],
  template: `
    <form (submit)="$event.preventDefault(); add(quantity.valueAsNumber)">
      <label>Liczba sztuk
        <input #quantity class="quantity" type="number" value="1" required min="1" step="1" [max]="available()" [disabled]="store.busy()">
      </label>
      <button class="primary" [disabled]="store.busy()">Dodaj do koszyka</button>
    </form>
    <div aria-live="polite">
      @if (message()) { <p>{{ message() }} <a routerLink="/koszyk">Przejdź do koszyka</a></p> }
    </div>
  `,
  styles: `
    form { display: flex; align-items: end; gap: .8rem; flex-wrap: wrap; }
    label { display: grid; gap: .35rem; }
    input { width: 5rem; }
  `,
})
export class AddToCart {
  readonly productId = input.required<string>();
  readonly available = input.required<number>();
  protected readonly store = inject(GuestCart);
  protected readonly message = signal('');

  protected async add(quantity: number) {
    this.message.set('');
    const added = await this.store.add(this.productId(), quantity);
    this.message.set(added ? `Dodano do koszyka: ${quantity} szt.` : this.store.error());
  }
}
