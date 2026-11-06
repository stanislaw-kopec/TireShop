import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TirePhoto } from '../catalog/tire-photo';
import { priceFormat, seasons } from '../catalog/catalog-api';
import { GuestCart } from './guest-cart';

@Component({
  imports: [RouterLink, TirePhoto],
  templateUrl: './cart-page.html',
  styleUrl: './cart-page.css',
})
export class CartPage implements OnInit {
  protected readonly store = inject(GuestCart);
  protected readonly seasons = seasons;
  protected amount(value: number | null) {
    return value === null ? 'Brak wyceny' : priceFormat.format(value);
  }

  ngOnInit() { void this.store.refresh(); }

  protected async update(id: string, input: HTMLInputElement) {
    await this.store.setQuantity(id, input.valueAsNumber);
    const saved = this.store.cart()?.items.find(line => line.product.id === id);
    if (saved) input.value = String(saved.quantity);
  }
}
