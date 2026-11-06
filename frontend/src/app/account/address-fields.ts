import { Component, input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import type { Address } from './account';

@Component({
  selector: 'app-address-fields',
  imports: [FormsModule],
  template: `
    <label>Odbiorca<input name="recipientName" required maxlength="200" autocomplete="shipping name" [(ngModel)]="address().recipientName"></label>
    <label>Ulica<input name="street" required maxlength="200" [(ngModel)]="address().street"></label>
    <label>Numer budynku<input name="buildingNumber" required maxlength="20" [(ngModel)]="address().buildingNumber"></label>
    <label>Numer lokalu (opcjonalnie)<input name="apartmentNumber" maxlength="20" [(ngModel)]="address().apartmentNumber"></label>
    <label>Kod pocztowy<input name="postalCode" required maxlength="20" autocomplete="shipping postal-code" [(ngModel)]="address().postalCode"></label>
    <label>Miejscowość<input name="city" required maxlength="100" autocomplete="shipping address-level2" [(ngModel)]="address().city"></label>
    <label>Kod kraju (np. PL)<input name="countryCode" required maxlength="2" pattern="[A-Za-z]{2}" autocomplete="shipping country" [(ngModel)]="address().countryCode"></label>
  `,
  styles: `
    :host, label { display: grid; gap: 1rem; }
    label { gap: .3rem; }
    input { font: inherit; color: inherit; padding: .55rem; border: 1px solid #8b978d; border-radius: .4rem; min-width: 0; }
  `,
})
export class AddressFields {
  readonly address = input.required<Address>();
}
